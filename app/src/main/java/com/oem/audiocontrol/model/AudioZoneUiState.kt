package com.oem.audiocontrol.model

data class AudioZoneUiState(
    val zoneId: Int,
    val zoneName: String,
    val volumeGroups: List<VolumeGroupUiState>
)

data class VolumeGroupUiState(
    val zoneId: Int,
    val groupId: Int,
    val label: String,
    val usages: List<String>,
    val currentVolume: Int,
    val minVolume: Int,
    val maxVolume: Int,
    val isMuted: Boolean
)

enum class VehiclePowerMode(val label: String) {
    ON("Ignition ON (Active)"),
    SHUTDOWN_PREPARE("Shutdown Prepare (Saving DSP)"),
    SUSPEND_ENTER("Deep Sleep / Suspend"),
    UNKNOWN("Initializing")
}

data class CabinDspState(
    val balance: Float = 0f, // -1.0 (Left) to +1.0 (Right)
    val fade: Float = 0f,    // -1.0 (Rear) to +1.0 (Front)
    val activeFocusSimulation: String? = null
)