package com.trisvax.studio.models

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class ProjectCategory(val label: String) {
    ALL("All"),
    VIDEOS("Videos"),
    REELS("Reels"),
    DRAFTS("Drafts")
}

@Serializable
enum class AspectRatioPreset(
    val label: String,
    val ratioWidth: Int,
    val ratioHeight: Int,
    val exportWidth: Int,
    val exportHeight: Int
) {
    PORTRAIT_9_16("9:16", 9, 16, 1080, 1920),
    LANDSCAPE_16_9("16:9", 16, 9, 1920, 1080),
    SQUARE_1_1("1:1", 1, 1, 1080, 1080)
}

@Serializable
data class CropConfig(
    val preset: AspectRatioPreset = AspectRatioPreset.PORTRAIT_9_16,
    val offsetX: Float = 0f, // -1f..1f normalized pan offset
    val offsetY: Float = 0f,
    val zoom: Float = 1f
)

@Serializable
data class VideoClipItem(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val displayName: String,
    val isImage: Boolean = false,
    val sourceDurationMs: Long,
    val startPositionMs: Long = 0L,
    val endPositionMs: Long = sourceDurationMs,
    val timelinePositionMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f, // 0f..2f (0%..200%)
    val isMuted: Boolean = false,
    val rotationDegrees: Int = 0, // 0, 90, 180, 270
    val cropConfig: CropConfig = CropConfig(),
    val thumbnailPath: String? = null
) {
    val effectiveDurationMs: Long
        get() = (((endPositionMs - startPositionMs).coerceAtLeast(250L)) / playbackSpeed).toLong()
}

@Serializable
data class MusicTrackItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val sourceUri: String,
    val isBundledAsset: Boolean = false,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val timelineStartMs: Long = 0L,
    val volume: Float = 0.8f,
    val isMuted: Boolean = false,
    val loopToFitVideo: Boolean = true
)

@Serializable
enum class TextAlignOption {
    LEFT, CENTER, RIGHT
}

@Serializable
data class TextOverlayItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fontSizeSp: Float = 28f,
    val colorHex: Long = 0xFFFFFFFF,
    val alignment: TextAlignOption = TextAlignOption.CENTER,
    val normalizedX: Float = 0.5f, // 0f..1f
    val normalizedY: Float = 0.5f, // 0f..1f
    val startTimeMs: Long = 0L,
    val endTimeMs: Long = 3000L
)

@Serializable
data class VoiceoverItem(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "Voiceover",
    val filePath: String,
    val sourceDurationMs: Long,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = sourceDurationMs,
    val timelineStartMs: Long = 0L,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false
)

@Serializable
data class TimelineState(
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.PORTRAIT_9_16,
    val clips: List<VideoClipItem> = emptyList(),
    val musicTrack: MusicTrackItem? = null,
    val textOverlays: List<TextOverlayItem> = emptyList(),
    val voiceovers: List<VoiceoverItem> = emptyList()
) {
    val totalDurationMs: Long
        get() = clips.sumOf { it.effectiveDurationMs }
}

data class BundledMusicAsset(
    val fileName: String,
    val title: String,
    val assetPath: String,
    val durationMs: Long
)

data class ExportConfig(
    val projectId: String,
    val projectName: String,
    val aspectRatio: AspectRatioPreset,
    val frameRate: Int = 30,
    val timelineState: TimelineState
)

sealed interface ExportUiState {
    data object Idle : ExportUiState
    data class Exporting(val progressPercent: Int, val statusMessage: String) : ExportUiState
    data class Success(val outputUri: String, val outputFileName: String) : ExportUiState
    data class Error(val message: String) : ExportUiState
}
