package com.trisvax.studio.video.timeline

import com.trisvax.studio.models.AspectRatioPreset
import com.trisvax.studio.models.CropConfig
import com.trisvax.studio.models.VideoClipItem
import java.util.UUID

object TimelineEngine {

    fun recomputeTimelinePositions(clips: List<VideoClipItem>): List<VideoClipItem> {
        var cursorMs = 0L
        return clips.map { clip ->
            val updated = clip.copy(timelinePositionMs = cursorMs)
            cursorMs += updated.effectiveDurationMs
            updated
        }
    }

    fun splitClipAtPlayhead(
        clips: List<VideoClipItem>,
        playheadMs: Long
    ): List<VideoClipItem> {
        val targetIndex = clips.indexOfFirst { clip ->
            playheadMs > clip.timelinePositionMs + 250L &&
                playheadMs < clip.timelinePositionMs + clip.effectiveDurationMs - 250L
        }
        if (targetIndex == -1) return clips

        val target = clips[targetIndex]
        val offsetInTimeline = playheadMs - target.timelinePositionMs
        val sourceSplitMs = target.startPositionMs + (offsetInTimeline * target.playbackSpeed).toLong()

        if (sourceSplitMs <= target.startPositionMs + 250L || sourceSplitMs >= target.endPositionMs - 250L) {
            return clips
        }

        val firstHalf = target.copy(
            endPositionMs = sourceSplitMs
        )
        val secondHalf = target.copy(
            id = UUID.randomUUID().toString(),
            startPositionMs = sourceSplitMs
        )

        val next = clips.toMutableList().apply {
            removeAt(targetIndex)
            add(targetIndex, firstHalf)
            add(targetIndex + 1, secondHalf)
        }
        return recomputeTimelinePositions(next)
    }

    fun trimClip(
        clips: List<VideoClipItem>,
        clipId: String,
        newStartMs: Long,
        newEndMs: Long
    ): List<VideoClipItem> {
        val updated = clips.map { clip ->
            if (clip.id != clipId) {
                clip
            } else {
                val clampedStart = newStartMs.coerceIn(0L, clip.sourceDurationMs - 300L)
                val clampedEnd = newEndMs.coerceIn(clampedStart + 300L, clip.sourceDurationMs)
                clip.copy(startPositionMs = clampedStart, endPositionMs = clampedEnd)
            }
        }
        return recomputeTimelinePositions(updated)
    }

    fun updateClipSpeed(
        clips: List<VideoClipItem>,
        clipId: String,
        speed: Float
    ): List<VideoClipItem> {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        val updated = clips.map { clip ->
            if (clip.id == clipId) clip.copy(playbackSpeed = clamped) else clip
        }
        return recomputeTimelinePositions(updated)
    }

    fun updateClipRotation(
        clips: List<VideoClipItem>,
        clipId: String,
        rotationDegrees: Int
    ): List<VideoClipItem> {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        return clips.map { clip ->
            if (clip.id == clipId) clip.copy(rotationDegrees = normalized) else clip
        }
    }

    fun updateClipVolume(
        clips: List<VideoClipItem>,
        clipId: String,
        volume: Float,
        isMuted: Boolean
    ): List<VideoClipItem> {
        return clips.map { clip ->
            if (clip.id == clipId) {
                clip.copy(volume = volume.coerceIn(0f, 2f), isMuted = isMuted)
            } else {
                clip
            }
        }
    }

    fun updateClipCrop(
        clips: List<VideoClipItem>,
        clipId: String,
        cropConfig: CropConfig
    ): List<VideoClipItem> {
        return clips.map { clip ->
            if (clip.id == clipId) clip.copy(cropConfig = cropConfig) else clip
        }
    }

    fun applyAspectPresetToAll(
        clips: List<VideoClipItem>,
        preset: AspectRatioPreset
    ): List<VideoClipItem> {
        return clips.map { clip ->
            clip.copy(cropConfig = clip.cropConfig.copy(preset = preset))
        }
    }

    fun reorderClip(
        clips: List<VideoClipItem>,
        fromIndex: Int,
        toIndex: Int
    ): List<VideoClipItem> {
        if (fromIndex !in clips.indices || toIndex !in clips.indices || fromIndex == toIndex) {
            return clips
        }
        val mutable = clips.toMutableList()
        val moved = mutable.removeAt(fromIndex)
        mutable.add(toIndex, moved)
        return recomputeTimelinePositions(mutable)
    }

    fun deleteClip(clips: List<VideoClipItem>, clipId: String): List<VideoClipItem> {
        if (clips.size <= 1) return clips
        return recomputeTimelinePositions(clips.filterNot { it.id == clipId })
    }

    fun findClipAtPlayhead(clips: List<VideoClipItem>, playheadMs: Long): VideoClipItem? {
        return clips.firstOrNull { clip ->
            playheadMs >= clip.timelinePositionMs &&
                playheadMs < clip.timelinePositionMs + clip.effectiveDurationMs
        } ?: clips.lastOrNull()
    }
}
