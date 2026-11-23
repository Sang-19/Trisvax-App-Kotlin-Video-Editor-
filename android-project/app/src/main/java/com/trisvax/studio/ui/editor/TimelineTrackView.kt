package com.trisvax.studio.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trisvax.studio.models.TimelineState
import com.trisvax.studio.theme.TrisvaxColors
import java.io.File
import kotlin.math.roundToInt

@Composable
fun TimelineTrackView(
    timeline: TimelineState,
    selectedClipId: String?,
    playheadMs: Long,
    onSeek: (Long) -> Unit,
    onSelectClip: (String) -> Unit,
    onToggleClipMute: (String) -> Unit,
    onAddMusicClick: () -> Unit,
    onAddTextClick: () -> Unit,
    onAddVoiceoverClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pxPerSecond = 44.dp
    val density = LocalDensity.current
    val pxPerMs = with(density) { pxPerSecond.toPx() / 1000f }
    val scrollState = rememberScrollState()
    val totalWidthDp: Dp = ((timeline.totalDurationMs.coerceAtLeast(4000L) / 1000f) * 44f).dp

    Surface(
        color = TrisvaxColors.PrimaryCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TrisvaxColors.Border),
        modifier = modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 12.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(scrollState)
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.width(totalWidthDp.coerceAtLeast(320.dp))
                ) {
                    // Track 1: Main video strip with thumbnails + speaker mute toggle
                    Row(
                        modifier = Modifier.height(54.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        timeline.clips.forEach { clip ->
                            val clipWidthDp = ((clip.effectiveDurationMs / 1000f) * 44f).coerceAtLeast(44f).dp
                            val isSelected = clip.id == selectedClipId
                            Box(
                                modifier = Modifier
                                    .width(clipWidthDp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(TrisvaxColors.SecondaryCard)
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) TrisvaxColors.PrimaryOrange else TrisvaxColors.Border,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable { onSelectClip(clip.id) }
                            ) {
                                val thumbFile = clip.thumbnailPath?.let(::File)?.takeIf { it.exists() }
                                Row(modifier = Modifier.matchParentSize()) {
                                    val tileCount = (clipWidthDp.value / 36f).toInt().coerceIn(1, 10)
                                    repeat(tileCount) {
                                        if (thumbFile != null) {
                                            AsyncImage(
                                                model = thumbFile,
                                                contentDescription = clip.displayName,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxHeight()
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .padding(start = 6.dp)
                                        .size(26.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .clickable { onToggleClipMute(clip.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (clip.isMuted || clip.volume <= 0.01f) {
                                            Icons.Filled.VolumeOff
                                        } else {
                                            Icons.Filled.VolumeUp
                                        },
                                        contentDescription = "Toggle clip audio",
                                        tint = TrisvaxColors.PrimaryText,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Track 2: Music / Audio track
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val music = timeline.musicTrack
                        if (music == null) {
                            TimelineAddChip(label = "Add audio", onClick = onAddMusicClick)
                        } else {
                            val durMs = (music.trimEndMs - music.trimStartMs).coerceAtLeast(1000L)
                            val w = ((durMs / 1000f) * 44f).coerceAtLeast(96f).dp
                            Surface(
                                color = TrisvaxColors.SecondaryCard,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, TrisvaxColors.PrimaryOrange),
                                modifier = Modifier
                                    .width(w)
                                    .height(34.dp)
                                    .clickable(onClick = onAddMusicClick)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = null,
                                        tint = TrisvaxColors.PrimaryOrange,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = music.title,
                                        color = TrisvaxColors.PrimaryText,
                                        fontSize = 12.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Track 3: Text overlay track
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (timeline.textOverlays.isEmpty()) {
                            TimelineAddChip(label = "Add text", onClick = onAddTextClick)
                        } else {
                            timeline.textOverlays.forEach { overlay ->
                                val w = (((overlay.endTimeMs - overlay.startTimeMs) / 1000f) * 44f)
                                    .coerceAtLeast(88f).dp
                                Surface(
                                    color = TrisvaxColors.SecondaryCard,
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, TrisvaxColors.BlueHighlight),
                                    modifier = Modifier
                                        .width(w)
                                        .height(32.dp)
                                        .clickable(onClick = onAddTextClick)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.TextFields,
                                            contentDescription = null,
                                            tint = TrisvaxColors.BlueHighlight,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = overlay.text,
                                            color = TrisvaxColors.PrimaryText,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                            TimelineAddChip(label = "Add text", onClick = onAddTextClick)
                        }
                    }

                    // Track 4: Voiceover track
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val vo = timeline.voiceovers.firstOrNull()
                        if (vo == null) {
                            TimelineAddChip(label = "Add voiceover", onClick = onAddVoiceoverClick)
                        } else {
                            val w = (((vo.trimEndMs - vo.trimStartMs) / 1000f) * 44f).coerceAtLeast(96f).dp
                            Surface(
                                color = TrisvaxColors.SecondaryCard,
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, TrisvaxColors.SecondaryOrange),
                                modifier = Modifier
                                    .width(w)
                                    .height(32.dp)
                                    .clickable(onClick = onAddVoiceoverClick)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Mic,
                                        contentDescription = null,
                                        tint = TrisvaxColors.SecondaryOrange,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = vo.title,
                                        color = TrisvaxColors.PrimaryText,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                    }
                }

                // Vertical white playhead with center circular handle matching screenshots
                val playheadX = (playheadMs * pxPerMs).roundToInt()
                Box(
                    modifier = Modifier
                        .offset { IntOffset(playheadX, 0) }
                        .width(18.dp)
                        .height(178.dp)
                        .pointerInput(timeline.totalDurationMs) {
                            detectHorizontalDragGestures { _, dragAmount ->
                                val deltaMs = (dragAmount / pxPerMs).toLong()
                                onSeek(playheadMs + deltaMs)
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .width(2.dp)
                            .fillMaxHeight()
                            .background(TrisvaxColors.PrimaryText)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(TrisvaxColors.PrimaryText)
                            .border(2.dp, TrisvaxColors.MainBackground, CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineAddChip(
    label: String,
    onClick: () -> Unit
) {
    Surface(
        color = TrisvaxColors.SecondaryCard,
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, TrisvaxColors.Border),
        modifier = Modifier
            .height(32.dp)
            .clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = TrisvaxColors.PrimaryText,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                color = TrisvaxColors.PrimaryText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
