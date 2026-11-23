package com.trisvax.studio.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.PlayerView
import com.trisvax.studio.components.EditorToolButton
import com.trisvax.studio.components.formatShortTimecode
import com.trisvax.studio.models.ExportUiState
import com.trisvax.studio.theme.TrisvaxColors
import com.trisvax.studio.ui.export.ExportDialog

@Composable
fun EditorScreen(
    projectId: String,
    viewModel: EditorViewModel,
    onClose: () -> Unit,
    onExportFinished: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()

    LaunchedEffect(projectId) {
        viewModel.loadProject(projectId)
    }

    val addMoreClipsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.appendMediaUris(uris)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importMusicFromUri(uri)
        }
    }

    val activeClip = state.timeline.clips.firstOrNull { it.id == state.selectedClipId }
        ?: state.timeline.clips.firstOrNull()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrisvaxColors.MainBackground)
            .safeDrawingPadding()
    ) {
        // Top preview region matching Editor_Page_73a362d7.png
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            val aspectFloat = state.timeline.aspectRatio.let {
                it.ratioWidth.toFloat() / it.ratioHeight.toFloat()
            }

            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .aspectRatio(aspectFloat)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(1.dp, TrisvaxColors.Border, RoundedCornerShape(12.dp))
                    .clickable { viewModel.togglePlayPause() }
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            useController = false
                            player = viewModel.previewPlayer.videoPlayer
                        }
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = (activeClip?.rotationDegrees ?: 0).toFloat()
                            scaleX = activeClip?.cropConfig?.zoom ?: 1f
                            scaleY = activeClip?.cropConfig?.zoom ?: 1f
                        }
                )

                // Live Text Overlay preview synchronized to playhead
                state.timeline.textOverlays.forEach { overlay ->
                    if (state.playheadMs in overlay.startTimeMs..overlay.endTimeMs) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(horizontal = 16.dp)
                                .clickable {
                                    viewModel.selectTextOverlay(overlay.id)
                                    viewModel.selectTool(EditorTool.TEXT)
                                }
                        ) {
                            Text(
                                text = overlay.text,
                                color = Color(overlay.colorHex),
                                fontSize = overlay.fontSizeSp.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                if (!state.isPlaying) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(58.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Play preview",
                            tint = TrisvaxColors.PrimaryText,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }

            // Top-left Close button
            IconButton(
                onClick = {
                    viewModel.pausePlayback()
                    onClose()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 4.dp)
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TrisvaxColors.SecondaryCard)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close editor", tint = TrisvaxColors.PrimaryText)
            }

            // Top-right Next button -> triggers 1080p Export
            Surface(
                color = TrisvaxColors.SecondaryCard,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(1.dp, TrisvaxColors.Border),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 4.dp)
                    .clickable { viewModel.startExport(onExportFinished) }
            ) {
                Text(
                    text = "Next",
                    color = TrisvaxColors.PrimaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp)
                )
            }
        }

        HorizontalDivider(color = TrisvaxColors.Border, thickness = 1.dp)

        // Playback transport bar matching screenshots
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = { viewModel.togglePlayPause() },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TrisvaxColors.SecondaryCard)
            ) {
                Icon(
                    imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pause" else "Play",
                    tint = TrisvaxColors.PrimaryText
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatShortTimecode(state.playheadMs),
                    color = TrisvaxColors.PrimaryText,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = " / ${formatShortTimecode(state.timeline.totalDurationMs)}",
                    color = TrisvaxColors.SecondaryText,
                    fontSize = 15.sp
                )
            }

            IconButton(
                onClick = {
                    if (state.activeTool != EditorTool.NONE) {
                        viewModel.selectTool(EditorTool.NONE)
                    } else {
                        addMoreClipsLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                        )
                    }
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TrisvaxColors.SecondaryCard)
            ) {
                Icon(
                    imageVector = if (state.activeTool != EditorTool.NONE) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = if (state.activeTool != EditorTool.NONE) "Close tool" else "Add clip",
                    tint = TrisvaxColors.PrimaryText
                )
            }
        }

        // Multi-track timeline
        TimelineTrackView(
            timeline = state.timeline,
            selectedClipId = state.selectedClipId,
            playheadMs = state.playheadMs,
            onSeek = viewModel::seekTo,
            onSelectClip = viewModel::selectClip,
            onToggleClipMute = { clipId ->
                val target = state.timeline.clips.firstOrNull { it.id == clipId } ?: return@TimelineTrackView
                viewModel.selectClip(clipId)
                viewModel.updateSelectedClipVolume(target.volume, !target.isMuted)
            },
            onAddMusicClick = { viewModel.selectTool(EditorTool.MUSIC) },
            onAddTextClick = { viewModel.selectTool(EditorTool.TEXT) },
            onAddVoiceoverClick = { viewModel.selectTool(EditorTool.VOICE) },
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        AnimatedVisibility(visible = state.activeTool != EditorTool.NONE) {
            EditorToolSheet(
                state = state,
                viewModel = viewModel,
                onPickMusicFromDevice = { audioPickerLauncher.launch("audio/*") },
                onCloseSheet = { viewModel.selectTool(EditorTool.NONE) }
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Bottom horizontal tool strip combining all 3 editor screenshot toolsets
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorToolButton("Music", Icons.Filled.MusicNote, state.activeTool == EditorTool.MUSIC) {
                viewModel.selectTool(EditorTool.MUSIC)
            }
            EditorToolButton("Text", Icons.Filled.TextFields, state.activeTool == EditorTool.TEXT) {
                viewModel.selectTool(EditorTool.TEXT)
            }
            EditorToolButton("Crop", Icons.Filled.Crop, state.activeTool == EditorTool.CROP) {
                viewModel.selectTool(EditorTool.CROP)
            }
            EditorToolButton("Trim", Icons.Filled.Tune, state.activeTool == EditorTool.TRIM) {
                viewModel.selectTool(EditorTool.TRIM)
            }
            EditorToolButton("Split", Icons.Filled.ContentCut, state.activeTool == EditorTool.SPLIT) {
                viewModel.selectTool(EditorTool.SPLIT)
            }
            EditorToolButton("Speed", Icons.Filled.FastForward, state.activeTool == EditorTool.SPEED) {
                viewModel.selectTool(EditorTool.SPEED)
            }
            EditorToolButton("Volume", Icons.Filled.VolumeUp, state.activeTool == EditorTool.VOLUME) {
                viewModel.selectTool(EditorTool.VOLUME)
            }
            EditorToolButton("Rotate", Icons.Filled.RotateRight, state.activeTool == EditorTool.ROTATE) {
                viewModel.selectTool(EditorTool.ROTATE)
            }
            EditorToolButton("Voice", Icons.Filled.Mic, state.activeTool == EditorTool.VOICE) {
                viewModel.selectTool(EditorTool.VOICE)
            }
            EditorToolButton("Clips", Icons.Filled.SwapHoriz, state.activeTool == EditorTool.CLIPS) {
                viewModel.selectTool(EditorTool.CLIPS)
            }
            EditorToolButton("Save", Icons.Filled.Download, false) {
                viewModel.startExport(onExportFinished)
            }
        }
    }

    if (state.exportState !is ExportUiState.Idle && state.exportState !is ExportUiState.Success) {
        ExportDialog(
            exportState = state.exportState,
            aspectLabel = "${state.timeline.aspectRatio.exportWidth}x${state.timeline.aspectRatio.exportHeight}",
            onCancel = viewModel::cancelExport,
            onDismissError = viewModel::dismissExportError
        )
    }
}
