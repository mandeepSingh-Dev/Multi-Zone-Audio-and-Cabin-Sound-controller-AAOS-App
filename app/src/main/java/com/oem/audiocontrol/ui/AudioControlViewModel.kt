package com.oem.audiocontrol.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.oem.audiocontrol.car.AudioFocusSimulator
import com.oem.audiocontrol.car.CarAudioController
import com.oem.audiocontrol.model.CabinDspState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AudioControlViewModel(application: Application) : AndroidViewModel(application) {

    private val audioController = CarAudioController(
        application,
        CarPowerStateListener = {},
    )
    private val focusSimulator = AudioFocusSimulator(application)

    val zonesState = audioController.zonesState
    val powerMode = audioController.powerMode

    private val _selectedZoneId = MutableStateFlow(0)
    val selectedZoneId: StateFlow<Int> = _selectedZoneId.asStateFlow()

    private val _dspState = MutableStateFlow(CabinDspState())
    val dspState: StateFlow<CabinDspState> = _dspState.asStateFlow()

    init {
        audioController.connect()
    }

    fun selectZone(zoneId: Int) {
        _selectedZoneId.value = zoneId
    }

    fun setGroupVolume(zoneId: Int, groupId: Int, volume: Int) {
        audioController.setGroupVolume(zoneId, groupId, volume)
    }

    fun toggleGroupMute(zoneId: Int, groupId: Int, mute: Boolean) {
        audioController.toggleGroupMute(zoneId, groupId, mute)
    }

    fun updateBalanceFade(balance: Float, fade: Float) {
        _dspState.update {
            it.copy(
                balance = balance.coerceIn(-1f, 1f),
                fade = fade.coerceIn(-1f, 1f)
            )
        }
    }

    fun centerSoundStage() {
        updateBalanceFade(0f, 0f)
    }

    fun simulateNavDucking() {
        focusSimulator.triggerNavigationDuckingPrompt { banner ->
            _dspState.update { it.copy(activeFocusSimulation = banner) }
        }
    }

    fun simulateEmergencyChime() {
        focusSimulator.triggerEmergencyAdasChime { banner ->
            _dspState.update { it.copy(activeFocusSimulation = banner) }
        }
    }

    override fun onCleared() {
        audioController.disconnect()
        super.onCleared()
    }
}