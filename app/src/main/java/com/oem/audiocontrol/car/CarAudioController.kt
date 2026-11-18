package com.oem.audiocontrol.car

import android.car.Car
import android.car.hardware.power.CarPowerManager
import android.car.media.CarAudioManager
import android.content.Context
import android.media.AudioAttributes
import android.util.Log
import com.oem.audiocontrol.model.AudioZoneUiState
import com.oem.audiocontrol.model.VehiclePowerMode
import com.oem.audiocontrol.model.VolumeGroupUiState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.concurrent.Executors

class CarAudioController(private val context: Context, CarPowerStateListener: Unit.(Any) -> Unit) {

    companion object {
        private const val TAG = "CarAudioController"
    }

    private var car: Car? = null
    private var carAudioManager: CarAudioManager? = null
    private var carPowerManager: CarPowerManager? = null
    private val callbackExecutor = Executors.newSingleThreadExecutor()

    private val _zonesState = MutableStateFlow<List<AudioZoneUiState>>(emptyList())
    val zonesState: StateFlow<List<AudioZoneUiState>> = _zonesState.asStateFlow()

    private val _powerMode = MutableStateFlow(VehiclePowerMode.UNKNOWN)
    val powerMode: StateFlow<VehiclePowerMode> = _powerMode.asStateFlow()

    // Cached snapshot saved before ECU enters Deep Sleep / Suspend
    private var suspendedVolumeSnapshot: Map<Pair<Int, Int>, Int> = emptyMap()

    private val volumeCallback = object : CarAudioManager.CarVolumeCallback() {
        override fun onGroupVolumeChanged(zoneId: Int, groupId: Int, flags: Int) {
            Log.d(TAG, "Hardware volume changed -> Zone: $zoneId, Group: $groupId, Flags: $flags")
            refreshZoneVolume(zoneId, groupId)
        }

        override fun onMasterMuteChanged(zoneId: Int, flags: Int) {
            Log.d(TAG, "Master mute changed -> Zone: $zoneId")
            loadAllAudioZones()
        }

        override fun onGroupMuteChanged(zoneId: Int, groupId: Int, flags: Int) {
            Log.d(TAG, "Group mute changed -> Zone: $zoneId, Group: $groupId")
            refreshZoneVolume(zoneId, groupId)
        }
    }

    private val powerStateListener = CarPowerManager.CarPowerStateListener { state ->
        when (state) {
            CarPowerManager.STATE_ON -> {
                _powerMode.value = VehiclePowerMode.ON
                restoreVolumesAfterWake()
            }
            CarPowerManager.STATE_SHUTDOWN_PREPARE -> {
                _powerMode.value = VehiclePowerMode.SHUTDOWN_PREPARE
                persistVolumeSnapshot()
            }
            CarPowerManager.STATE_SUSPEND_ENTER -> {
                _powerMode.value = VehiclePowerMode.SUSPEND_ENTER
                persistVolumeSnapshot()
            }
            else -> {
                Log.d(TAG, "Unhandled CarPowerManager state: $state")
            }
        }
    }

    fun connect() {
        if (car?.isConnected == true) return

        car = Car.createCar(context, null, Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER) { carInstance, ready ->
            if (ready) {
                carAudioManager = carInstance.getCarManager(Car.AUDIO_SERVICE) as? CarAudioManager
                carPowerManager = carInstance.getCarManager(Car.POWER_SERVICE) as? CarPowerManager

                carAudioManager?.registerCarVolumeCallback(volumeCallback)
                try {
                    carPowerManager?.setListener(callbackExecutor, powerStateListener)
                    _powerMode.value = VehiclePowerMode.ON
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register CarPowerStateListener", e)
                }

                loadAllAudioZones()
            }
        }
    }

    fun disconnect() {
        try {
            carAudioManager?.unregisterCarVolumeCallback(volumeCallback)
            carPowerManager?.clearListener()
            car?.disconnect()
        } catch (e: Exception) {
            Log.e(TAG, "Error during Car service teardown", e)
        } finally {
            car = null
            carAudioManager = null
            carPowerManager = null
            callbackExecutor.shutdown()
        }
    }

    fun loadAllAudioZones() {
        val manager = carAudioManager ?: return

        // Discover primary cabin zone + any configured Rear Seat Entertainment (RSE) zones
        val zoneIds = try {
            manager.audioZoneIds
        } catch (e: Exception) {
            listOf(CarAudioManager.PRIMARY_AUDIO_ZONE)
        }

        val zones = zoneIds.map { zoneId ->
            val groupCount = manager.getVolumeGroupCount(zoneId)
            val groups = (0 until groupCount).map { groupId ->
                buildVolumeGroupState(manager, zoneId, groupId)
            }
            AudioZoneUiState(
                zoneId = zoneId,
                zoneName = if (zoneId == CarAudioManager.PRIMARY_AUDIO_ZONE) {
                    "Primary Cabin (Zone $zoneId)"
                } else {
                    "Rear Seat Zone $zoneId"
                },
                volumeGroups = groups
            )
        }
        _zonesState.value = zones
    }

    private fun buildVolumeGroupState(
        manager: CarAudioManager,
        zoneId: Int,
        groupId: Int
    ): VolumeGroupUiState {
        val usagesIntArray = manager.getUsagesForVolumeGroupId(zoneId, groupId)
        val usageNames = usagesIntArray.map { mapUsageToLabel(it) }.distinct()
        val groupLabel = when {
            usageNames.contains("Media") -> "Media & Entertainment"
            usageNames.contains("Navigation") -> "Navigation Guidance"
            usageNames.contains("Voice Call") -> "Hands-Free Telephony"
            usageNames.contains("Assistant") -> "Voice Assistant"
            usageNames.contains("Alarm / Chime") -> "System & ADAS Chimes"
            else -> "Audio Bus #$groupId"
        }

        return VolumeGroupUiState(
            zoneId = zoneId,
            groupId = groupId,
            label = groupLabel,
            usages = usageNames,
            currentVolume = manager.getGroupVolume(zoneId, groupId),
            minVolume = manager.getGroupMinVolume(zoneId, groupId),
            maxVolume = manager.getGroupMaxVolume(zoneId, groupId),
            isMuted = try {
                manager.isVolumeGroupMuted(zoneId, groupId)
            } catch (e: Exception) {
                false
            }
        )
    }

    fun setGroupVolume(zoneId: Int, groupId: Int, volume: Int) {
        val manager = carAudioManager ?: return
        val min = manager.getGroupMinVolume(zoneId, groupId)
        val max = manager.getGroupMaxVolume(zoneId, groupId)
        val clamped = volume.coerceIn(min, max)

        // Flags = 0 suppresses redundant system volume HUD overlay when adjusting inside OEM mixer
        manager.setGroupVolume(zoneId, groupId, clamped, 0)
        refreshZoneVolume(zoneId, groupId)
    }

    fun toggleGroupMute(zoneId: Int, groupId: Int, mute: Boolean) {
        val manager = carAudioManager ?: return
        try {
            manager.setVolumeGroupMute(zoneId, groupId, mute, 0)
            refreshZoneVolume(zoneId, groupId)
        } catch (e: UnsupportedOperationException) {
            // Fallback for emulators where per-group mute flag isn't enabled in car_audio_configuration.xml
            if (mute) setGroupVolume(zoneId, groupId, manager.getGroupMinVolume(zoneId, groupId))
        }
    }

    private fun refreshZoneVolume(zoneId: Int, groupId: Int) {
        val manager = carAudioManager ?: return
        val updatedGroup = buildVolumeGroupState(manager, zoneId, groupId)
        _zonesState.update { currentZones ->
            currentZones.map { zone ->
                if (zone.zoneId != zoneId) zone
                else zone.copy(
                    volumeGroups = zone.volumeGroups.map { group ->
                        if (group.groupId == groupId) updatedGroup else group
                    }
                )
            }
        }
    }

    private fun persistVolumeSnapshot() {
        val snapshot = mutableMapOf<Pair<Int, Int>, Int>()
        _zonesState.value.forEach { zone ->
            zone.volumeGroups.forEach { group ->
                snapshot[zone.zoneId to group.groupId] = group.currentVolume
            }
        }
        suspendedVolumeSnapshot = snapshot
        Log.i(TAG, "Persisted ${snapshot.size} volume group levels before power transition.")
    }

    private fun restoreVolumesAfterWake() {
        val manager = carAudioManager ?: return
        if (suspendedVolumeSnapshot.isEmpty()) return
        suspendedVolumeSnapshot.forEach { (key, savedVol) ->
            manager.setGroupVolume(key.first, key.second, savedVol, 0)
        }
        loadAllAudioZones()
        Log.i(TAG, "Restored volume levels post-wake.")
    }

    private fun mapUsageToLabel(usage: Int): String = when (usage) {
        AudioAttributes.USAGE_MEDIA -> "Media"
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "Navigation"
        AudioAttributes.USAGE_VOICE_COMMUNICATION,
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING -> "Voice Call"
        AudioAttributes.USAGE_ASSISTANT -> "Assistant"
        AudioAttributes.USAGE_ALARM,
        AudioAttributes.USAGE_NOTIFICATION,
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE -> "Alarm / Chime"
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "UI Touch"
//        AudioAttributes.USAGE_EMERGENCY,
//        AudioAttributes.USAGE_SAFETY -> "Safety / Emergency"
        else -> "Usage($usage)"
    }
}