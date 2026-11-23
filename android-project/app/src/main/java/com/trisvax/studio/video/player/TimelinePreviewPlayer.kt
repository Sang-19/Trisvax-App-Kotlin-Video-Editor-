package com.trisvax.studio.video.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.trisvax.studio.models.MusicTrackItem
import com.trisvax.studio.models.TimelineState
import com.trisvax.studio.models.VoiceoverItem

/**
 * Coordinates multi-clip sequential ExoPlayer playback along with synchronized
 * background music and voiceover audio tracks.
 */
class TimelinePreviewPlayer(context: Context) {

    val videoPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val musicPlayer: ExoPlayer = ExoPlayer.Builder(context).build()
    private val voiceoverPlayer: ExoPlayer = ExoPlayer.Builder(context).build()

    private var currentTimeline: TimelineState = TimelineState()

    fun loadTimeline(timeline: TimelineState) {
        currentTimeline = timeline

        val videoItems = timeline.clips.map { clip ->
            MediaItem.Builder()
                .setMediaId(clip.id)
                .setUri(Uri.parse(clip.sourceUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.startPositionMs)
                        .setEndPositionMs(clip.endPositionMs)
                        .build()
                )
                .build()
        }

        videoPlayer.setMediaItems(videoItems, /* resetPosition= */ false)
        videoPlayer.prepare()
        applyActiveClipParams()

        loadMusic(timeline.musicTrack)
        loadVoiceovers(timeline.voiceovers)
    }

    private fun loadMusic(music: MusicTrackItem?) {
        if (music == null || music.isMuted) {
            musicPlayer.stop()
            musicPlayer.clearMediaItems()
            return
        }
        val item = MediaItem.Builder()
            .setUri(Uri.parse(music.sourceUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(music.trimStartMs)
                    .setEndPositionMs(music.trimEndMs)
                    .build()
            )
            .build()
        musicPlayer.setMediaItem(item)
        musicPlayer.repeatMode = if (music.loopToFitVideo) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        musicPlayer.volume = music.volume.coerceIn(0f, 1f)
        musicPlayer.prepare()
    }

    private fun loadVoiceovers(voiceovers: List<VoiceoverItem>) {
        val active = voiceovers.firstOrNull { !it.isMuted }
        if (active == null) {
            voiceoverPlayer.stop()
            voiceoverPlayer.clearMediaItems()
            return
        }
        val item = MediaItem.Builder()
            .setUri(Uri.parse("file://${active.filePath}"))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(active.trimStartMs)
                    .setEndPositionMs(active.trimEndMs)
                    .build()
            )
            .build()
        voiceoverPlayer.setMediaItem(item)
        voiceoverPlayer.volume = active.volume.coerceIn(0f, 1f)
        voiceoverPlayer.prepare()
    }

    fun applyActiveClipParams() {
        val index = videoPlayer.currentMediaItemIndex
        val clip = currentTimeline.clips.getOrNull(index) ?: return
        videoPlayer.playbackParameters = PlaybackParameters(clip.playbackSpeed)
        videoPlayer.volume = if (clip.isMuted) 0f else clip.volume.coerceIn(0f, 1f)
    }

    fun play() {
        applyActiveClipParams()
        videoPlayer.play()
        syncAuxiliaryAudio(isPlaying = true, timelinePositionMs = currentTimelinePositionMs())
    }

    fun pause() {
        videoPlayer.pause()
        musicPlayer.pause()
        voiceoverPlayer.pause()
    }

    fun seekToTimelineMs(timelineMs: Long) {
        val clamped = timelineMs.coerceIn(0L, currentTimeline.totalDurationMs)
        var cursor = 0L
        currentTimeline.clips.forEachIndexed { index, clip ->
            val clipDur = clip.effectiveDurationMs
            if (clamped <= cursor + clipDur || index == currentTimeline.clips.lastIndex) {
                val localOffset = ((clamped - cursor).coerceAtLeast(0L) * clip.playbackSpeed).toLong()
                videoPlayer.seekTo(index, localOffset)
                applyActiveClipParams()
                syncAuxiliaryAudio(videoPlayer.isPlaying, clamped)
                return
            }
            cursor += clipDur
        }
    }

    fun currentTimelinePositionMs(): Long {
        val idx = videoPlayer.currentMediaItemIndex
        val clip = currentTimeline.clips.getOrNull(idx) ?: return 0L
        val localPos = (videoPlayer.currentPosition / clip.playbackSpeed).toLong()
        return (clip.timelinePositionMs + localPos).coerceIn(0L, currentTimeline.totalDurationMs)
    }

    private fun syncAuxiliaryAudio(isPlaying: Boolean, timelinePositionMs: Long) {
        currentTimeline.musicTrack?.let { music ->
            if (!music.isMuted && musicPlayer.mediaItemCount > 0) {
                val offset = (timelinePositionMs - music.timelineStartMs).coerceAtLeast(0L)
                val dur = (music.trimEndMs - music.trimStartMs).coerceAtLeast(1000L)
                musicPlayer.seekTo(offset % dur)
                if (isPlaying) musicPlayer.play() else musicPlayer.pause()
            }
        }
        currentTimeline.voiceovers.firstOrNull { !it.isMuted }?.let { vo ->
            if (voiceoverPlayer.mediaItemCount > 0) {
                val offset = timelinePositionMs - vo.timelineStartMs
                if (offset in 0L..(vo.trimEndMs - vo.trimStartMs)) {
                    voiceoverPlayer.seekTo(offset)
                    if (isPlaying) voiceoverPlayer.play() else voiceoverPlayer.pause()
                } else {
                    voiceoverPlayer.pause()
                }
            }
        }
    }

    fun release() {
        videoPlayer.release()
        musicPlayer.release()
        voiceoverPlayer.release()
    }
}
