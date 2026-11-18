package com.oem.audiocontrol.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.oem.audiocontrol.model.CabinDspState
import com.oem.audiocontrol.model.VehiclePowerMode
import com.oem.audiocontrol.model.VolumeGroupUiState
import kotlin.math.roundToInt

private val CockpitBg = Color(0xFF0B0F17)
private val CardSurface = Color(0xFF151C28)
private val AccentCyan = Color(0xFF38BDF8)
private val AccentAmber = Color(0xFFF59E0B)
private val AlertRed = Color(0xFFEF4444)

@Composable
fun CabinAudioScreen(viewModel: AudioControlViewModel) {
    val zones by viewModel.zonesState.collectAsState()
    val selectedZoneId by viewModel.selectedZoneId.collectAsState()
    val powerMode by viewModel.powerMode.collectAsState()
    val dspState by viewModel.dspState.collectAsState()

    val activeZone = zones.find { it.zoneId == selectedZoneId } ?: zones.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CockpitBg)
            .padding(20.dp)
    ) {
        // Top Header: Title, Zone Selector Tabs, and CarPowerManager Status
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "CABIN SOUND STUDIO",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp
                )
                Text(
                    text = "Multi-Zone CarAudioManager DSP Router",
                    color = Color(0xFF94A3B8),
                    fontSize = 13.sp
                )
            }

            // Zone Switcher Pills
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                zones.forEach { zone ->
                    val isSelected = zone.zoneId == activeZone?.zoneId
                    FilterChip(
                        selected = isSelected,
                        onClick = { viewModel.selectZone(zone.zoneId) },
                        label = {
                            Text(
                                text = zone.zoneName,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = AccentCyan.copy(alpha = 0.22f),
                            selectedLabelColor = AccentCyan,
                            containerColor = CardSurface,
                            labelColor = Color(0xFFCBD5E1)
                        )
                    )
                }
            }

            // Power State Telemetry Pill
            PowerStatusPill(powerMode)
        }

        // Live Audio Focus Ducking / Chime Alert Banner
        dspState.activeFocusSimulation?.let { alertMessage ->
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(AccentAmber.copy(alpha = 0.18f))
                    .border(1.dp, AccentAmber, RoundedCornerShape(10.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    text = alertMessage,
                    color = AccentAmber,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main Split Layout: Left = Zone Volume Group Faders | Right = 2D Sound Stage & Focus Triggers
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Left Pane (62%): Dynamic Volume Groups for Selected Zone
            Card(
                modifier = Modifier
                    .weight(0.62f)
                    .fillMaxHeight(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = CardSurface)
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "${activeZone?.zoneName ?: "Primary Zone"} — Volume Groups",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val groups = activeZone?.volumeGroups.orEmpty()
                    if (groups.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Connecting to CarAudioService...", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(groups, key = { "${it.zoneId}_${it.groupId}" }) { group ->
                                VolumeGroupSliderRow(
                                    group = group,
                                    onVolumeChange = { newVol ->
                                        viewModel.setGroupVolume(group.zoneId, group.groupId, newVol)
                                    },
                                    onMuteToggle = {
                                        viewModel.toggleGroupMute(group.zoneId, group.groupId, !group.isMuted)
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // Right Pane (38%): 2D Cabin Sound Stage & AudioFocus Simulation Controls
            Column(
                modifier = Modifier
                    .weight(0.38f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cabin Sound Stage Balance / Fade Pad
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    CabinSoundStagePad(
                        dspState = dspState,
                        onBalanceFadeChanged = viewModel::updateBalanceFade,
                        onCenterClicked = viewModel::centerSoundStage
                    )
                }

                // AudioFocusRequest Simulation Deck
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "AudioFocus & Priority Matrix",
                            color = Color.White,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold
                        )

                        Button(
                            onClick = viewModel::simulateNavDucking,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
                        ) {
                            Icon(Icons.Default.Navigation, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Test Nav Ducking (MAY_DUCK)", fontWeight = FontWeight.SemiBold)
                        }

                        Button(
                            onClick = viewModel::simulateEmergencyChime,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = AlertRed)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Trigger ADAS Emergency Chime", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VolumeGroupSliderRow(
    group: VolumeGroupUiState,
    onVolumeChange: (Int) -> Unit,
    onMuteToggle: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Group ${group.groupId}: ${group.label}",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Usages: ${group.usages.joinToString(" • ")}",
                    color = Color(0xFF94A3B8),
                    fontSize = 12.sp
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (group.isMuted) "MUTED" else "${group.currentVolume} / ${group.maxVolume}",
                    color = if (group.isMuted) AlertRed else AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onMuteToggle) {
                    Icon(
                        imageVector = if (group.isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                        contentDescription = "Mute Toggle",
                        tint = if (group.isMuted) AlertRed else Color.White
                    )
                }
            }
        }

        Slider(
            value = group.currentVolume.toFloat(),
            onValueChange = { onVolumeChange(it.roundToInt()) },
            valueRange = group.minVolume.toFloat()..group.maxVolume.toFloat().coerceAtLeast(1f),
            colors = SliderDefaults.colors(
                thumbColor = AccentCyan,
                activeTrackColor = AccentCyan,
                inactiveTrackColor = Color(0xFF334155)
            )
        )
    }
}

@Composable
private fun CabinSoundStagePad(
    dspState: CabinDspState,
    onBalanceFadeChanged: (Float, Float) -> Unit,
    onCenterClicked: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Cabin Sound Stage",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Bal: ${"%.2f".format(dspState.balance)} | Fade: ${"%.2f".format(dspState.fade)}",
                    color = AccentCyan,
                    fontSize = 12.sp
                )
            }
            IconButton(onClick = onCenterClicked) {
                Icon(
                    imageVector = Icons.Default.CenterFocusStrong,
                    contentDescription = "Center Sound Stage",
                    tint = AccentCyan
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF0F172A))
                .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val bal = ((offset.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                            val fade = (1f - (offset.y / size.height) * 2f).coerceIn(-1f, 1f)
                            onBalanceFadeChanged(bal, fade)
                        }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures { change, _ ->
                            change.consume()
                            val bal = ((change.position.x / size.width) * 2f - 1f).coerceIn(-1f, 1f)
                            val fade = (1f - (change.position.y / size.height) * 2f).coerceIn(-1f, 1f)
                            onBalanceFadeChanged(bal, fade)
                        }
                    }
            ) {
                val centerX = size.width / 2f
                val centerY = size.height / 2f

                // Cabin Chassis Outline
                drawRoundRect(
                    color = Color(0xFF334155),
                    topLeft = Offset(size.width * 0.18f, size.height * 0.1f),
                    size = androidx.compose.ui.geometry.Size(size.width * 0.64f, size.height * 0.8f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(40f, 40f),
                    style = Stroke(width = 3f)
                )

                // Crosshairs
                drawLine(Color(0xFF1E293B), Offset(centerX, 0f), Offset(centerX, size.height), 2f)
                drawLine(Color(0xFF1E293B), Offset(0f, centerY), Offset(size.width, centerY), 2f)

                // Acoustic Focal Point
                val focalX = ((dspState.balance + 1f) / 2f) * size.width
                val focalY = ((1f - dspState.fade) / 2f) * size.height

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AccentCyan.copy(alpha = 0.45f), Color.Transparent),
                        center = Offset(focalX, focalY),
                        radius = 90f
                    ),
                    radius = 90f,
                    center = Offset(focalX, focalY)
                )
                drawCircle(
                    color = AccentCyan,
                    radius = 14f,
                    center = Offset(focalX, focalY)
                )
            }
        }
    }
}

@Composable
private fun PowerStatusPill(mode: VehiclePowerMode) {
    val color = when (mode) {
        VehiclePowerMode.ON -> Color(0xFF22C55E)
        VehiclePowerMode.SHUTDOWN_PREPARE -> AccentAmber
        VehiclePowerMode.SUSPEND_ENTER -> AlertRed
        VehiclePowerMode.UNKNOWN -> Color.Gray
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = mode.label,
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}