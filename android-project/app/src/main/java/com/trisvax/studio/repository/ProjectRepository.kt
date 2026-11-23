package com.trisvax.studio.repository

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.trisvax.studio.database.ProjectDao
import com.trisvax.studio.database.ProjectEntity
import com.trisvax.studio.models.AspectRatioPreset
import com.trisvax.studio.models.CropConfig
import com.trisvax.studio.models.ProjectCategory
import com.trisvax.studio.models.TimelineState
import com.trisvax.studio.models.VideoClipItem
import com.trisvax.studio.video.timeline.TimelineEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class ProjectRepository(
    private val context: Context,
    private val projectDao: ProjectDao
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun observeProjects(): Flow<List<ProjectEntity>> = projectDao.observeAllProjects()

    suspend fun getProject(projectId: String): ProjectEntity? = projectDao.getProjectById(projectId)

    fun decodeTimeline(timelineJson: String): TimelineState {
        return try {
            json.decodeFromString<TimelineState>(timelineJson)
        } catch (_: Exception) {
            TimelineState()
        }
    }

    suspend fun createProjectFromUris(
        uris: List<Uri>,
        defaultName: String = "New project"
    ): String = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectDir = projectFolder(projectId)
        val clips = mutableListOf<VideoClipItem>()

        for ((index, uri) in uris.withIndex()) {
            val imported = importMediaToProjectDir(projectDir, uri, index)
            if (imported != null) {
                clips.add(imported)
            }
        }

        val reindexed = TimelineEngine.recomputeTimelinePositions(clips)
        val initialAspect = AspectRatioPreset.PORTRAIT_9_16
        val state = TimelineState(
            aspectRatio = initialAspect,
            clips = reindexed.map { it.copy(cropConfig = CropConfig(preset = initialAspect)) }
        )

        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            id = projectId,
            name = defaultName,
            category = inferCategory(state).name,
            thumbnailPath = reindexed.firstOrNull()?.thumbnailPath,
            durationMs = state.totalDurationMs,
            storageSizeBytes = computeFolderSizeBytes(projectDir),
            timelineJson = json.encodeToString(state),
            lastModifiedAt = now,
            createdAt = now
        )
        projectDao.upsertProject(entity)
        projectId
    }

    suspend fun createEmptyProject(name: String = "Untitled"): String = withContext(Dispatchers.IO) {
        val projectId = UUID.randomUUID().toString()
        val projectDir = projectFolder(projectId)
        val now = System.currentTimeMillis()
        val state = TimelineState()
        val entity = ProjectEntity(
            id = projectId,
            name = name,
            category = ProjectCategory.DRAFTS.name,
            thumbnailPath = null,
            durationMs = 0L,
            storageSizeBytes = computeFolderSizeBytes(projectDir),
            timelineJson = json.encodeToString(state),
            lastModifiedAt = now,
            createdAt = now
        )
        projectDao.upsertProject(entity)
        projectId
    }

    suspend fun appendUrisToProject(projectId: String, uris: List<Uri>): TimelineState =
        withContext(Dispatchers.IO) {
            val existing = projectDao.getProjectById(projectId)
            val currentState = existing?.let { decodeTimeline(it.timelineJson) } ?: TimelineState()
            val projectDir = projectFolder(projectId)
            val startIdx = currentState.clips.size
            val added = uris.mapIndexedNotNull { idx, uri ->
                importMediaToProjectDir(projectDir, uri, startIdx + idx)
            }
            val updatedClips = TimelineEngine.recomputeTimelinePositions(currentState.clips + added)
            val updatedState = currentState.copy(clips = updatedClips)
            saveTimelineState(projectId, existing?.name ?: "New project", updatedState, existing?.exportedUri)
            updatedState
        }

    suspend fun saveTimelineState(
        projectId: String,
        projectName: String,
        timelineState: TimelineState,
        exportedUri: String? = null
    ) = withContext(Dispatchers.IO) {
        val existing = projectDao.getProjectById(projectId)
        val projectDir = projectFolder(projectId)
        val now = System.currentTimeMillis()
        val entity = ProjectEntity(
            id = projectId,
            name = projectName,
            category = inferCategory(timelineState).name,
            thumbnailPath = timelineState.clips.firstOrNull()?.thumbnailPath ?: existing?.thumbnailPath,
            durationMs = timelineState.totalDurationMs,
            storageSizeBytes = computeFolderSizeBytes(projectDir),
            timelineJson = json.encodeToString(timelineState),
            lastModifiedAt = now,
            createdAt = existing?.createdAt ?: now,
            exportedUri = exportedUri ?: existing?.exportedUri
        )
        projectDao.upsertProject(entity)
    }

    suspend fun renameProject(projectId: String, newName: String) {
        projectDao.renameProject(projectId, newName.trim().ifEmpty { "Untitled" }, System.currentTimeMillis())
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        projectDao.deleteProjectById(projectId)
        projectFolder(projectId).deleteRecursively()
    }

    fun getVoiceoverOutputFile(projectId: String): File {
        val dir = File(projectFolder(projectId), "voiceovers").apply { mkdirs() }
        return File(dir, "vo_${System.currentTimeMillis()}.m4a")
    }

    private fun projectFolder(projectId: String): File {
        return File(context.filesDir, "projects/$projectId").apply { mkdirs() }
    }

    private fun inferCategory(state: TimelineState): ProjectCategory {
        return when {
            state.clips.isEmpty() -> ProjectCategory.DRAFTS
            state.aspectRatio == AspectRatioPreset.PORTRAIT_9_16 -> ProjectCategory.REELS
            else -> ProjectCategory.VIDEOS
        }
    }

    private fun importMediaToProjectDir(
        projectDir: File,
        uri: Uri,
        index: Int
    ): VideoClipItem? {
        try {
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (_: SecurityException) {
                    // Photo Picker URIs may not support persistable grants; copying locally guarantees durability
                }
            }

            val mimeType = context.contentResolver.getType(uri).orEmpty()
            val isImage = mimeType.startsWith("image/")
            val displayName = resolveDisplayName(uri) ?: "Clip ${index + 1}"
            val mediaDir = File(projectDir, "media").apply { mkdirs() }
            val ext = when {
                isImage -> ".jpg"
                mimeType.contains("webm") -> ".webm"
                else -> ".mp4"
            }
            val localFile = File(mediaDir, "clip_${System.currentTimeMillis()}_$index$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(localFile).use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            val localUri = Uri.fromFile(localFile).toString()
            val durationMs = if (isImage) 3_000L else extractVideoDurationMs(localFile)
            val thumbFile = File(projectDir, "thumb_${localFile.nameWithoutExtension}.jpg")
            if (!isImage) {
                extractVideoThumbnail(localFile, thumbFile)
            } else {
                localFile.copyTo(thumbFile, overwrite = true)
            }

            return VideoClipItem(
                sourceUri = localUri,
                displayName = displayName,
                isImage = isImage,
                sourceDurationMs = durationMs,
                startPositionMs = 0L,
                endPositionMs = durationMs,
                thumbnailPath = thumbFile.takeIf { it.exists() }?.absolutePath
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun resolveDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).name
        return context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun extractVideoDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull()?.coerceAtLeast(500L) ?: 3_000L
        } catch (_: Exception) {
            3_000L
        } finally {
            retriever.release()
        }
    }

    private fun extractVideoThumbnail(videoFile: File, destThumb: File) {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(videoFile.absolutePath)
            val frame = retriever.getFrameAtTime(500_000L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: retriever.frameAtTime
            if (frame != null) {
                FileOutputStream(destThumb).use { out ->
                    frame.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                frame.recycle()
            }
        } catch (_: Exception) {
            // Fallback handled gracefully by UI
        } finally {
            retriever.release()
        }
    }

    private fun computeFolderSizeBytes(dir: File): Long {
        if (!dir.exists()) return 0L
        return dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }
}
