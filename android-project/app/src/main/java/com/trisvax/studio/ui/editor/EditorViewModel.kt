package com.trisvax.studio.ui.editor

import android.app.Application
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.trisvax.studio.TrisvaxApplication
import com.trisvax.studio.models.AspectRatioPreset
import com.trisvax.studio.models.BundledMusicAsset
import com.trisvax.studio.models.CropConfig
import com.trisvax.studio.models.ExportConfig
import com.trisvax.studio.models.ExportUiState
import com.trisvax.studio.models.MusicTrackItem
import com.trisvax.studio.models.TextAlignOption
import com.trisvax.studio.models.TextOverlayItem
import com.trisvax.studio.models.TimelineState
import com.trisvax.studio.models.VoiceoverItem
import com.trisvax.studio.video.audio.VoiceoverRecorder
import com.trisvax.studio.video.player.TimelinePreviewPlayer
import com.trisvax.studio.video.processing.Media3ExportEngine
import com.trisvax.studio.video.processing.VideoExportService
import com.trisvax.studio.video.timeline.TimelineEngine
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

enum class EditorTool {
    NONE, MUSIC, TEXT, CROP, TRIM, SPLIT, SPEED, VOLUME, ROTATE, VOICE, CLIPS
}

data class EditorUiState(
    val projectId: String = "",
    val projectName: String = "New project",
    val timeline: TimelineState = TimelineState(),
    val selectedClipId: String? = null,
    val selectedTextId: String? = null,
    val playheadMs: Long = 0L,
    val isPlaying: Boolean = false,
    val activeTool: EditorTool = EditorTool.NONE,
    val bundledMusic: List<BundledMusicAsset> = emptyList(),
    val isRecordingVoiceover: Boolean = false,
    val voiceoverAmplitude: Int = 0,
    val exportState: ExportUiState = ExportUiState.Idle
)

class EditorViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TrisvaxApplication
    private val repository = app.projectRepository
    private val musicCatalog = app.musicAssetCatalog
    private val exportEngine = Media3ExportEngine(application)
    private val voiceRecorder = VoiceoverRecorder(application)

    val previewPlayer = TimelinePreviewPlayer(application)

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    private var playheadTickerJob: Job? = null
    private var exportJob: Job? = null
    private var voiceAmpJob: Job? = null

    init {
        viewModelScope.launch {
            val bundled = musicCatalog.listBundledTracks()
            _uiState.update { it.copy(bundledMusic = bundled) }
        }
    }

    fun loadProject(projectId: String) {
        viewModelScope.launch {
            val entity = repository.getProject(projectId)
            val timeline = entity?.let { repository.decodeTimeline(it.timelineJson) } ?: TimelineState()
            _uiState.update {
                it.copy(
                    projectId = projectId,
                    projectName = entity?.name ?: "New project",
                    timeline = timeline,
                    selectedClipId = timeline.clips.firstOrNull()?.id,
                    playheadMs = 0L,
                    isPlaying = false,
                    exportState = ExportUiState.Idle
                )
            }
            previewPlayer.loadTimeline(timeline)
        }
    }

    fun appendMediaUris(uris: List<Uri>) {
        val pid = _uiState.value.projectId
        if (pid.isBlank() || uris.isEmpty()) return
        viewModelScope.launch {
            val updated = repository.appendUrisToProject(pid, uris)
            _uiState.update {
                it.copy(
                    timeline = updated,
                    selectedClipId = updated.clips.lastOrNull()?.id ?: it.selectedClipId
                )
            }
            previewPlayer.loadTimeline(updated)
        }
    }

    fun selectTool(tool: EditorTool) {
        _uiState.update {
            it.copy(activeTool = if (it.activeTool == tool) EditorTool.NONE else tool)
        }
    }

    fun selectClip(clipId: String) {
        _uiState.update { it.copy(selectedClipId = clipId) }
    }

    fun togglePlayPause() {
        if (_uiState.value.isPlaying) {
            pausePlayback()
        } else {
            previewPlayer.play()
            _uiState.update { it.copy(isPlaying = true) }
            startPlayheadTicker()
        }
    }

    fun pausePlayback() {
        previewPlayer.pause()
        playheadTickerJob?.cancel()
        _uiState.update { it.copy(isPlaying = false) }
    }

    fun seekTo(ms: Long) {
        val clamped = ms.coerceIn(0L, _uiState.value.timeline.totalDurationMs)
        previewPlayer.seekToTimelineMs(clamped)
        val clipAtPlayhead = TimelineEngine.findClipAtPlayhead(_uiState.value.timeline.clips, clamped)
        _uiState.update {
            it.copy(
                playheadMs = clamped,
                selectedClipId = clipAtPlayhead?.id ?: it.selectedClipId
            )
        }
    }

    private fun startPlayheadTicker() {
        playheadTickerJob?.cancel()
        playheadTickerJob = viewModelScope.launch {
            while (isActive) {
                val pos = previewPlayer.currentTimelinePositionMs()
                val total = _uiState.value.timeline.totalDurationMs
                _uiState.update { it.copy(playheadMs = pos) }
                if (!previewPlayer.videoPlayer.isPlaying && pos >= total - 50L) {
                    _uiState.update { it.copy(isPlaying = false, playheadMs = 0L) }
                    previewPlayer.seekToTimelineMs(0L)
                    break
                }
                delay(60L)
            }
        }
    }

    fun splitAtPlayhead() {
        val current = _uiState.value
        val updatedClips = TimelineEngine.splitClipAtPlayhead(current.timeline.clips, current.playheadMs)
        commitTimeline(current.timeline.copy(clips = updatedClips))
    }

    fun trimSelectedClip(startMs: Long, endMs: Long) {
        val current = _uiState.value
        val clipId = current.selectedClipId ?: return
        val updatedClips = TimelineEngine.trimClip(current.timeline.clips, clipId, startMs, endMs)
        commitTimeline(current.timeline.copy(clips = updatedClips))
    }

    fun updateSelectedClipSpeed(speed: Float) {
        val current = _uiState.value
        val clipId = current.selectedClipId ?: return
        val updatedClips = TimelineEngine.updateClipSpeed(current.timeline.clips, clipId, speed)
        commitTimeline(current.timeline.copy(clips = updatedClips))
    }

    fun rotateSelectedClip90() {
        val current = _uiState.value
        val clipId = current.selectedClipId ?: return
        val clip = current.timeline.clips.firstOrNull { it.id == clipId } ?: return
        val updatedClips = TimelineEngine.updateClipRotation(
            current.timeline.clips,
            clipId,
            clip.rotationDegrees + 90
        )
        commitTimeline(current.timeline.copy(clips = updatedClips))
    }

    fun updateSelectedClipVolume(volume: Float, isMuted: Boolean) {
        val current = _uiState.value
        val clipId = current.selectedClipId ?: return
        val updatedClips = TimelineEngine.updateClipVolume(current.timeline.clips, clipId, volume, isMuted)
        commitTimeline(current.timeline.copy(clips = updatedClips))
    }

    fun updateAspectAndCrop(preset: AspectRatioPreset, cropConfig: CropConfig) {
        val current = _uiState.value
        val clipId = current.selectedClipId
        var clips = TimelineEngine.applyAspectPresetToAll(current.timeline.clips, preset)
        if (clipId != null) {
            clips = TimelineEngine.updateClipCrop(clips, clipId, cropConfig.copy(preset = preset))
        }
        commitTimeline(current.timeline.copy(aspectRatio = preset, clips = clips))
    }

    fun reorderClip(fromIndex: Int, toIndex: Int) {
        val current = _uiState.value
        val updated = TimelineEngine.reorderClip(current.timeline.clips, fromIndex, toIndex)
        commitTimeline(current.timeline.copy(clips = updated))
    }

    fun deleteClip(clipId: String) {
        val current = _uiState.value
        val updated = TimelineEngine.deleteClip(current.timeline.clips, clipId)
        _uiState.update { it.copy(selectedClipId = updated.firstOrNull()?.id) }
        commitTimeline(current.timeline.copy(clips = updated))
    }

    fun selectBundledMusic(asset: BundledMusicAsset) {
        val track = MusicTrackItem(
            title = asset.title,
            sourceUri = asset.assetPath,
            isBundledAsset = true,
            sourceDurationMs = asset.durationMs,
            trimStartMs = 0L,
            trimEndMs = asset.durationMs
        )
        commitTimeline(_uiState.value.timeline.copy(musicTrack = track))
    }

    fun importMusicFromUri(uri: Uri) {
        viewModelScope.launch {
            val musicDir = File(app.filesDir, "projects/${_uiState.value.projectId}/music").apply { mkdirs() }
            val outFile = File(musicDir, "music_${System.currentTimeMillis()}.m4a")
            app.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(outFile).use { output -> input.copyTo(output) }
            }
            val durationMs = extractAudioDurationMs(outFile)
            val title = resolveFileName(uri) ?: "Imported Music"
            val track = MusicTrackItem(
                title = title,
                sourceUri = Uri.fromFile(outFile).toString(),
                isBundledAsset = false,
                sourceDurationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs
            )
            commitTimeline(_uiState.value.timeline.copy(musicTrack = track))
        }
    }

    fun updateMusicSettings(volume: Float, trimStartMs: Long, trimEndMs: Long, muted: Boolean) {
        val music = _uiState.value.timeline.musicTrack ?: return
        val updated = music.copy(
            volume = volume.coerceIn(0f, 1f),
            trimStartMs = trimStartMs.coerceIn(0L, music.sourceDurationMs - 500L),
            trimEndMs = trimEndMs.coerceIn(trimStartMs + 500L, music.sourceDurationMs),
            isMuted = muted
        )
        commitTimeline(_uiState.value.timeline.copy(musicTrack = updated))
    }

    fun removeMusic() {
        commitTimeline(_uiState.value.timeline.copy(musicTrack = null))
    }

    fun addOrUpdateTextOverlay(
        text: String,
        fontSizeSp: Float,
        colorHex: Long,
        alignment: TextAlignOption,
        normalizedX: Float,
        normalizedY: Float,
        startMs: Long,
        endMs: Long
    ) {
        val current = _uiState.value
        val existingId = current.selectedTextId
        val nextList = if (existingId != null && current.timeline.textOverlays.any { it.id == existingId }) {
            current.timeline.textOverlays.map {
                if (it.id == existingId) {
                    it.copy(
                        text = text,
                        fontSizeSp = fontSizeSp,
                        colorHex = colorHex,
                        alignment = alignment,
                        normalizedX = normalizedX,
                        normalizedY = normalizedY,
                        startTimeMs = startMs,
                        endTimeMs = endMs
                    )
                } else it
            }
        } else {
            val newItem = TextOverlayItem(
                text = text,
                fontSizeSp = fontSizeSp,
                colorHex = colorHex,
                alignment = alignment,
                normalizedX = normalizedX,
                normalizedY = normalizedY,
                startTimeMs = startMs,
                endTimeMs = endMs.coerceAtLeast(startMs + 500L)
            )
            _uiState.update { it.copy(selectedTextId = newItem.id) }
            current.timeline.textOverlays + newItem
        }
        commitTimeline(current.timeline.copy(textOverlays = nextList))
    }

    fun selectTextOverlay(id: String?) {
        _uiState.update { it.copy(selectedTextId = id) }
    }

    fun deleteTextOverlay(id: String) {
        val current = _uiState.value
        _uiState.update { it.copy(selectedTextId = null) }
        commitTimeline(current.timeline.copy(textOverlays = current.timeline.textOverlays.filterNot { it.id == id }))
    }

    fun startVoiceoverRecording() {
        val pid = _uiState.value.projectId.ifBlank { return }
        val file = repository.getVoiceoverOutputFile(pid)
        if (voiceRecorder.startRecording(file)) {
            _uiState.update { it.copy(isRecordingVoiceover = true, voiceoverAmplitude = 0) }
            voiceAmpJob?.cancel()
            voiceAmpJob = viewModelScope.launch {
                while (isActive) {
                    _uiState.update { it.copy(voiceoverAmplitude = voiceRecorder.getMaxAmplitude()) }
                    delay(100L)
                }
            }
        }
    }

    fun stopVoiceoverRecording() {
        voiceAmpJob?.cancel()
        val result = voiceRecorder.stopRecording()
        _uiState.update { it.copy(isRecordingVoiceover = false, voiceoverAmplitude = 0) }
        if (result != null) {
            val (file, durationMs) = result
            val vo = VoiceoverItem(
                filePath = file.absolutePath,
                sourceDurationMs = durationMs,
                trimStartMs = 0L,
                trimEndMs = durationMs,
                timelineStartMs = _uiState.value.playheadMs
            )
            commitTimeline(_uiState.value.timeline.copy(voiceovers = listOf(vo)))
        }
    }

    fun updateVoiceover(volume: Float, trimStartMs: Long, trimEndMs: Long, isMuted: Boolean) {
        val vo = _uiState.value.timeline.voiceovers.firstOrNull() ?: return
        val updated = vo.copy(
            volume = volume.coerceIn(0f, 1f),
            trimStartMs = trimStartMs.coerceIn(0L, vo.sourceDurationMs - 300L),
            trimEndMs = trimEndMs.coerceIn(trimStartMs + 300L, vo.sourceDurationMs),
            isMuted = isMuted
        )
        commitTimeline(_uiState.value.timeline.copy(voiceovers = listOf(updated)))
    }

    fun deleteVoiceover() {
        _uiState.value.timeline.voiceovers.forEach { File(it.filePath).delete() }
        commitTimeline(_uiState.value.timeline.copy(voiceovers = emptyList()))
    }

    fun startExport(onSuccess: () -> Unit) {
        pausePlayback()
        val current = _uiState.value
        if (current.timeline.clips.isEmpty()) {
            _uiState.update { it.copy(exportState = ExportUiState.Error("Add at least one clip before exporting.")) }
            return
        }

        val serviceIntent = Intent(app, VideoExportService::class.java)
        ContextCompat.startForegroundService(app, serviceIntent)

        exportJob?.cancel()
        exportJob = viewModelScope.launch {
            _uiState.update { it.copy(exportState = ExportUiState.Exporting(0, "Initializing export...")) }
            val result = exportEngine.exportVideo(
                config = ExportConfig(
                    projectId = current.projectId,
                    projectName = current.projectName,
                    aspectRatio = current.timeline.aspectRatio,
                    frameRate = 30,
                    timelineState = current.timeline
                ),
                onProgress = { pct, msg ->
                    _uiState.update { it.copy(exportState = ExportUiState.Exporting(pct, msg)) }
                }
            )
            app.stopService(serviceIntent)

            when (result) {
                is Media3ExportEngine.Result.Success -> {
                    repository.saveTimelineState(
                        projectId = current.projectId,
                        projectName = current.projectName,
                        timelineState = current.timeline,
                        exportedUri = result.mediaStoreUri.toString()
                    )
                    _uiState.update {
                        it.copy(
                            exportState = ExportUiState.Success(
                                outputUri = result.mediaStoreUri.toString(),
                                outputFileName = result.fileName
                            )
                        )
                    }
                    onSuccess()
                }
                is Media3ExportEngine.Result.Failure -> {
                    _uiState.update { it.copy(exportState = ExportUiState.Error(result.reason)) }
                }
            }
        }
    }

    fun cancelExport() {
        exportJob?.cancel()
        app.stopService(Intent(app, VideoExportService::class.java))
        _uiState.update { it.copy(exportState = ExportUiState.Idle) }
    }

    fun dismissExportError() {
        _uiState.update { it.copy(exportState = ExportUiState.Idle) }
    }

    private fun commitTimeline(nextTimeline: TimelineState) {
        val current = _uiState.value
        _uiState.update { it.copy(timeline = nextTimeline) }
        previewPlayer.loadTimeline(nextTimeline)
        viewModelScope.launch {
            if (current.projectId.isNotBlank()) {
                repository.saveTimelineState(current.projectId, current.projectName, nextTimeline)
            }
        }
    }

    private fun extractAudioDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 10_000L
        } catch (_: Exception) {
            10_000L
        } finally {
            retriever.release()
        }
    }

    private fun resolveFileName(uri: Uri): String? {
        return app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }

    override fun onCleared() {
        super.onCleared()
        playheadTickerJob?.cancel()
        voiceAmpJob?.cancel()
        voiceRecorder.stopAndRelease()
        previewPlayer.release()
    }
}
