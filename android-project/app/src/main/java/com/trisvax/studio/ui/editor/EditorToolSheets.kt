package com.trisvax.studio.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.trisvax.studio.components.formatDuration
import com.trisvax.studio.models.AspectRatioPreset
import com.trisvax.studio.models.CropConfig
import com.trisvax.studio.models.TextAlignOption
import com.trisvax.studio.theme.TrisvaxColors

@Composable
fun EditorToolSheet(
    state: EditorUiState,
    viewModel: EditorViewModel,
    onPickMusicFromDevice: () -> Unit,
    onCloseSheet: () -> Unit
) {
    val selectedClip = state.timeline.clips.firstOrNull { it.id == state.selectedClipId }
        ?: state.timeline.clips.firstOrNull()

    Surface(
        color = TrisvaxColors.PrimaryCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TrisvaxColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            when (state.activeTool) {
                EditorTool.TRIM -> {
                    if (selectedClip != null) {
                        var startMs by remember(selectedClip.id, selectedClip.startPositionMs) {
                            mutableFloatStateOf(selectedClip.startPositionMs.toFloat())
                        }
                        var endMs by remember(selectedClip.id, selectedClip.endPositionMs) {
                            mutableFloatStateOf(selectedClip.endPositionMs.toFloat())
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Trim Clip", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                            Text(
                                "${formatDuration(startMs.toLong())} - ${formatDuration(endMs.toLong())}",
                                color = TrisvaxColors.SecondaryOrange,
                                fontSize = 12.sp
                            )
                        }
                        RangeSlider(
                            value = startMs..endMs,
                            onValueChange = { range ->
                                startMs = range.start
                                endMs = range.endInclusive
                            },
                            onValueChangeFinished = {
                                viewModel.trimSelectedClip(startMs.toLong(), endMs.toLong())
                            },
                            valueRange = 0f..selectedClip.sourceDurationMs.toFloat().coerceAtLeast(500f),
                            colors = SliderDefaults.colors(
                                thumbColor = TrisvaxColors.PrimaryOrange,
                                activeTrackColor = TrisvaxColors.PrimaryOrange
                            )
                        )
                    }
                }

                EditorTool.SPLIT -> {
                    Text("Split at Playhead (${formatDuration(state.playheadMs)})", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            viewModel.splitAtPlayhead()
                            onCloseSheet()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Split Selected Clip", color = TrisvaxColors.PrimaryText)
                    }
                }

                EditorTool.CROP -> {
                    Text("Aspect Ratio & Crop", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AspectRatioPreset.entries.forEach { preset ->
                            val active = state.timeline.aspectRatio == preset
                            Surface(
                                color = if (active) TrisvaxColors.PrimaryOrange else TrisvaxColors.SecondaryCard,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TrisvaxColors.Border),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        val cfg = selectedClip?.cropConfig ?: CropConfig()
                                        viewModel.updateAspectAndCrop(preset, cfg)
                                    }
                            ) {
                                Text(
                                    text = preset.label,
                                    color = TrisvaxColors.PrimaryText,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp)
                                )
                            }
                        }
                    }
                    if (selectedClip != null) {
                        Text("Zoom: ${"%.1fx".format(selectedClip.cropConfig.zoom)}", color = TrisvaxColors.SecondaryText, fontSize = 12.sp)
                        Slider(
                            value = selectedClip.cropConfig.zoom,
                            onValueChange = { z ->
                                viewModel.updateAspectAndCrop(
                                    state.timeline.aspectRatio,
                                    selectedClip.cropConfig.copy(zoom = z)
                                )
                            },
                            valueRange = 1f..2.5f,
                            colors = SliderDefaults.colors(
                                thumbColor = TrisvaxColors.PrimaryOrange,
                                activeTrackColor = TrisvaxColors.PrimaryOrange
                            )
                        )
                    }
                }

                EditorTool.ROTATE -> {
                    Text(
                        "Rotate Clip (${selectedClip?.rotationDegrees ?: 0}\u00B0)",
                        color = TrisvaxColors.PrimaryText,
                        fontWeight = FontWeight.Bold
                    )
                    Button(
                        onClick = { viewModel.rotateSelectedClip90() },
                        colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.RotateRight, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Rotate +90\u00B0", color = TrisvaxColors.PrimaryText)
                    }
                }

                EditorTool.SPEED -> {
                    val speeds = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)
                    Text("Playback Speed", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        speeds.forEach { spd ->
                            val active = selectedClip?.playbackSpeed == spd
                            Surface(
                                color = if (active) TrisvaxColors.PrimaryOrange else TrisvaxColors.SecondaryCard,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, TrisvaxColors.Border),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.updateSelectedClipSpeed(spd) }
                            ) {
                                Text(
                                    text = "${spd}x",
                                    color = TrisvaxColors.PrimaryText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp)
                                )
                            }
                        }
                    }
                }

                EditorTool.VOLUME -> {
                    if (selectedClip != null) {
                        val pct = if (selectedClip.isMuted) 0 else (selectedClip.volume * 100).toInt()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Clip Volume: $pct%", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                            OutlinedButton(
                                onClick = {
                                    viewModel.updateSelectedClipVolume(selectedClip.volume, !selectedClip.isMuted)
                                },
                                border = BorderStroke(1.dp, TrisvaxColors.Border)
                            ) {
                                Text(
                                    if (selectedClip.isMuted) "Unmute" else "Mute",
                                    color = TrisvaxColors.PrimaryOrange
                                )
                            }
                        }
                        Slider(
                            value = if (selectedClip.isMuted) 0f else selectedClip.volume,
                            onValueChange = { v -> viewModel.updateSelectedClipVolume(v, v <= 0.01f) },
                            valueRange = 0f..2f,
                            colors = SliderDefaults.colors(
                                thumbColor = TrisvaxColors.PrimaryOrange,
                                activeTrackColor = TrisvaxColors.PrimaryOrange
                            )
                        )
                    }
                }

                EditorTool.MUSIC -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Music Library (assets/music)", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                        OutlinedButton(
                            onClick = onPickMusicFromDevice,
                            border = BorderStroke(1.dp, TrisvaxColors.PrimaryOrange)
                        ) {
                            Text("Import Audio", color = TrisvaxColors.PrimaryOrange, fontSize = 12.sp)
                        }
                    }

                    state.bundledMusic.forEach { track ->
                        val active = state.timeline.musicTrack?.title == track.title
                        Surface(
                            color = TrisvaxColors.SecondaryCard,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, if (active) TrisvaxColors.PrimaryOrange else TrisvaxColors.Border),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.selectBundledMusic(track) }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.MusicNote, contentDescription = null, tint = TrisvaxColors.PrimaryOrange, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(track.title, color = TrisvaxColors.PrimaryText, fontSize = 13.sp)
                                }
                                Text(formatDuration(track.durationMs), color = TrisvaxColors.SecondaryText, fontSize = 12.sp)
                            }
                        }
                    }

                    state.timeline.musicTrack?.let { currentMusic ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Volume ${(currentMusic.volume * 100).toInt()}%", color = TrisvaxColors.SecondaryText, fontSize = 12.sp)
                            IconButton(onClick = { viewModel.removeMusic() }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove music", tint = TrisvaxColors.ErrorDanger)
                            }
                        }
                        Slider(
                            value = currentMusic.volume,
                            onValueChange = { vol ->
                                viewModel.updateMusicSettings(vol, currentMusic.trimStartMs, currentMusic.trimEndMs, false)
                            },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(
                                thumbColor = TrisvaxColors.PrimaryOrange,
                                activeTrackColor = TrisvaxColors.PrimaryOrange
                            )
                        )
                    }
                }

                EditorTool.TEXT -> {
                    val existing = state.timeline.textOverlays.firstOrNull { it.id == state.selectedTextId }
                    var textVal by remember(existing?.id) { mutableStateOf(existing?.text ?: "TRISVAX") }
                    var fontSize by remember(existing?.id) { mutableFloatStateOf(existing?.fontSizeSp ?: 28f) }
                    var colorHex by remember(existing?.id) { mutableStateOf(existing?.colorHex ?: 0xFFFFFFFF) }
                    var posY by remember(existing?.id) { mutableFloatStateOf(existing?.normalizedY ?: 0.5f) }

                    Text("Text Overlay", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = textVal,
                        onValueChange = { textVal = it },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TrisvaxColors.PrimaryOrange,
                            unfocusedBorderColor = TrisvaxColors.Border,
                            focusedTextColor = TrisvaxColors.PrimaryText,
                            unfocusedTextColor = TrisvaxColors.PrimaryText
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    val palette = listOf(0xFFFFFFFF, 0xFFFF5500, 0xFFFF8232, 0xFF82B8FF, 0xFFFF5A5A)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        palette.forEach { hex ->
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(hex))
                                    .border(
                                        width = if (colorHex == hex) 2.dp else 1.dp,
                                        color = if (colorHex == hex) TrisvaxColors.PrimaryOrange else TrisvaxColors.Border,
                                        shape = CircleShape
                                    )
                                    .clickable { colorHex = hex }
                            )
                        }
                    }
                    Text("Size ${fontSize.toInt()}sp \u2022 Vertical position ${(posY * 100).toInt()}%", color = TrisvaxColors.SecondaryText, fontSize = 12.sp)
                    Slider(
                        value = fontSize,
                        onValueChange = { fontSize = it },
                        valueRange = 16f..54f,
                        colors = SliderDefaults.colors(thumbColor = TrisvaxColors.PrimaryOrange, activeTrackColor = TrisvaxColors.PrimaryOrange)
                    )
                    Slider(
                        value = posY,
                        onValueChange = { posY = it },
                        valueRange = 0.1f..0.9f,
                        colors = SliderDefaults.colors(thumbColor = TrisvaxColors.PrimaryOrange, activeTrackColor = TrisvaxColors.PrimaryOrange)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                if (textVal.isNotBlank()) {
                                    viewModel.addOrUpdateTextOverlay(
                                        text = textVal,
                                        fontSizeSp = fontSize,
                                        colorHex = colorHex,
                                        alignment = TextAlignOption.CENTER,
                                        normalizedX = 0.5f,
                                        normalizedY = posY,
                                        startMs = state.playheadMs,
                                        endMs = (state.playheadMs + 3500L).coerceAtMost(state.timeline.totalDurationMs.coerceAtLeast(3500L))
                                    )
                                    onCloseSheet()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Apply Text", color = TrisvaxColors.PrimaryText)
                        }
                        if (existing != null) {
                            OutlinedButton(
                                onClick = { viewModel.deleteTextOverlay(existing.id) },
                                border = BorderStroke(1.dp, TrisvaxColors.ErrorDanger)
                            ) {
                                Text("Delete", color = TrisvaxColors.ErrorDanger)
                            }
                        }
                    }
                }

                EditorTool.VOICE -> {
                    val context = LocalContext.current
                    var hasMic by remember {
                        mutableStateOf(
                            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                        )
                    }
                    val micLauncher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { granted ->
                        hasMic = granted
                        if (granted) viewModel.startVoiceoverRecording()
                    }

                    Text("Voiceover", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                    Button(
                        onClick = {
                            if (state.isRecordingVoiceover) {
                                viewModel.stopVoiceoverRecording()
                            } else if (hasMic) {
                                viewModel.startVoiceoverRecording()
                            } else {
                                micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (state.isRecordingVoiceover) TrisvaxColors.ErrorDanger else TrisvaxColors.PrimaryOrange
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = if (state.isRecordingVoiceover) Icons.Filled.Stop else Icons.Filled.Mic,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (state.isRecordingVoiceover) "Stop Recording" else "Record Voiceover",
                            color = TrisvaxColors.PrimaryText
                        )
                    }

                    state.timeline.voiceovers.firstOrNull()?.let { vo ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Voiceover (${formatDuration(vo.sourceDurationMs)})", color = TrisvaxColors.SecondaryText, fontSize = 12.sp)
                            IconButton(onClick = { viewModel.deleteVoiceover() }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Delete voiceover", tint = TrisvaxColors.ErrorDanger)
                            }
                        }
                        Slider(
                            value = vo.volume,
                            onValueChange = { v -> viewModel.updateVoiceover(v, vo.trimStartMs, vo.trimEndMs, false) },
                            valueRange = 0f..1f,
                            colors = SliderDefaults.colors(thumbColor = TrisvaxColors.PrimaryOrange, activeTrackColor = TrisvaxColors.PrimaryOrange)
                        )
                    }
                }

                EditorTool.CLIPS -> {
                    val idx = state.timeline.clips.indexOfFirst { it.id == selectedClip?.id }
                    Text("Clip Order & Management", color = TrisvaxColors.PrimaryText, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = idx > 0,
                            onClick = { viewModel.reorderClip(idx, idx - 1) },
                            border = BorderStroke(1.dp, TrisvaxColors.Border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = null, tint = TrisvaxColors.PrimaryText)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Move Left", color = TrisvaxColors.PrimaryText, fontSize = 12.sp)
                        }
                        OutlinedButton(
                            enabled = idx in 0 until state.timeline.clips.lastIndex,
                            onClick = { viewModel.reorderClip(idx, idx + 1) },
                            border = BorderStroke(1.dp, TrisvaxColors.Border),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Move Right", color = TrisvaxColors.PrimaryText, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Filled.ArrowForward, contentDescription = null, tint = TrisvaxColors.PrimaryText)
                        }
                        OutlinedButton(
                            enabled = state.timeline.clips.size > 1 && selectedClip != null,
                            onClick = { selectedClip?.let { viewModel.deleteClip(it.id) } },
                            border = BorderStroke(1.dp, TrisvaxColors.ErrorDanger)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete clip", tint = TrisvaxColors.ErrorDanger)
                        }
                    }
                }

                EditorTool.NONE -> Unit
            }
        }
    }
}
