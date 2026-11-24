package com.trisvax.studio.video.processing

import android.content.ContentValues
import android.content.Context
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import androidx.annotation.OptIn
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Crop
import androidx.media3.effect.OverlayEffect
import androidx.media3.common.OverlaySettings
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.trisvax.studio.models.ExportConfig
import com.trisvax.studio.models.TextOverlayItem
import com.trisvax.studio.models.VideoClipItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume

@OptIn(UnstableApi::class)
class Media3ExportEngine(private val context: Context) {

    sealed interface Result {
        data class Success(val mediaStoreUri: Uri, val fileName: String) : Result
        data class Failure(val reason: String) : Result
    }

    suspend fun exportVideo(
        config: ExportConfig,
        onProgress: (Int, String) -> Unit
    ): Result = withContext(Dispatchers.Main) {
        if (config.timelineState.clips.isEmpty()) {
            return@withContext Result.Failure("No video clips in timeline.")
        }

        if (!hasSufficientStorage()) {
            return@withContext Result.Failure("Insufficient device storage for 1080p MP4 export.")
        }

        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val safeName = config.projectName.replace(Regex("[^A-Za-z0-9_\\-]"), "_").ifBlank { "TRISVAX" }
        val outputFileName = "${safeName}_$timestamp.mp4"

        val tempDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val tempOutputFile = File(tempDir, outputFileName)
        if (tempOutputFile.exists()) tempOutputFile.delete()

        val composition = buildComposition(config)

        var activeTransformer: Transformer? = null
        val exportOutcome = suspendCancellableCoroutine<kotlin.Result<File>> { cont ->
            val transformer = Transformer.Builder(context)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(kotlin.Result.success(tempOutputFile))
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException
                    ) {
                        if (cont.isActive) {
                            cont.resume(
                                kotlin.Result.failure(
                                    IllegalStateException(exportException.localizedMessage ?: "Export failed")
                                )
                            )
                        }
                    }
                })
                .build()

            activeTransformer = transformer
            cont.invokeOnCancellation {
                transformer.cancel()
                tempOutputFile.delete()
            }

            onProgress(2, "Preparing 1080p H.264/AAC pipeline...")
            transformer.start(composition, tempOutputFile.absolutePath)

            // Progress polling coroutine on Main looper as required by Transformer.getProgress
            val handler = android.os.Handler(android.os.Looper.getMainLooper())
            val holder = ProgressHolder()
            val pollRunnable = object : Runnable {
                override fun run() {
                    if (!cont.isActive) return
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        onProgress(holder.progress.coerceIn(2, 96), "Rendering 1080p MP4...")
                    }
                    if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                        handler.postDelayed(this, 180L)
                    }
                }
            }
            handler.postDelayed(pollRunnable, 180L)
        }

        if (exportOutcome.isFailure) {
            tempOutputFile.delete()
            return@withContext Result.Failure(
                exportOutcome.exceptionOrNull()?.message ?: "Video encoding failed."
            )
        }

        onProgress(98, "Saving to Movies/TRISVAX Studio/...")
        val savedUri = withContext(Dispatchers.IO) {
            publishToMediaStore(tempOutputFile, outputFileName)
        }
        tempOutputFile.delete()

        if (savedUri == null) {
            Result.Failure("Failed to publish MP4 to device Gallery.")
        } else {
            onProgress(100, "Export complete")
            Result.Success(savedUri, outputFileName)
        }
    }

    private fun buildComposition(config: ExportConfig): Composition {
        val timeline = config.timelineState
        val targetW = config.aspectRatio.exportWidth
        val targetH = config.aspectRatio.exportHeight

        val textEffects = buildTextOverlayEffects(timeline.textOverlays)

        val videoEditedItems = timeline.clips.map { clip ->
            buildEditedVideoClip(
                clip = clip,
                targetWidth = targetW,
                targetHeight = targetH,
                globalTextEffects = textEffects
            )
        }

        val sequences = mutableListOf<EditedMediaItemSequence>()
        sequences.add(EditedMediaItemSequence(videoEditedItems))

        // Mix background music track if present and unmuted
        timeline.musicTrack?.takeIf { !it.isMuted }?.let { music ->
            val musicMedia = MediaItem.Builder()
                .setUri(Uri.parse(music.sourceUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(music.trimStartMs)
                        .setEndPositionMs(music.trimEndMs)
                        .build()
                )
                .build()

            val musicItem = EditedMediaItem.Builder(musicMedia)
                .setRemoveVideo(true)
                .build()
            sequences.add(
                EditedMediaItemSequence.Builder(listOf(musicItem))
                    .setIsLooping(music.loopToFitVideo)
                    .build()
            )
        }

        // Mix first active voiceover track if present
        timeline.voiceovers.firstOrNull { !it.isMuted }?.let { vo ->
            val voMedia = MediaItem.Builder()
                .setUri(Uri.parse("file://${vo.filePath}"))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(vo.trimStartMs)
                        .setEndPositionMs(vo.trimEndMs)
                        .build()
                )
                .build()
            val voItem = EditedMediaItem.Builder(voMedia)
                .setRemoveVideo(true)
                .build()
            sequences.add(EditedMediaItemSequence(listOf(voItem)))
        }

        return Composition.Builder(sequences).build()
    }

    private fun buildEditedVideoClip(
        clip: VideoClipItem,
        targetWidth: Int,
        targetHeight: Int,
        globalTextEffects: List<Effect>
    ): EditedMediaItem {
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(clip.sourceUri))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(clip.startPositionMs)
                    .setEndPositionMs(clip.endPositionMs)
                    .build()
            )
            .build()

        val videoEffects = mutableListOf<Effect>()

        // Crop window if zoomed/panned
        val zoom = clip.cropConfig.zoom.coerceIn(1f, 3f)
        if (zoom > 1.01f || clip.cropConfig.offsetX != 0f || clip.cropConfig.offsetY != 0f) {
            val halfSpan = 1f / zoom
            val cx = clip.cropConfig.offsetX.coerceIn(-(1f - halfSpan), 1f - halfSpan)
            val cy = clip.cropConfig.offsetY.coerceIn(-(1f - halfSpan), 1f - halfSpan)
            videoEffects.add(
                Crop(
                    (cx - halfSpan).coerceIn(-1f, 1f),
                    (cx + halfSpan).coerceIn(-1f, 1f),
                    (cy - halfSpan).coerceIn(-1f, 1f),
                    (cy + halfSpan).coerceIn(-1f, 1f)
                )
            )
        }

        if (clip.rotationDegrees != 0) {
            videoEffects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(clip.rotationDegrees.toFloat())
                    .build()
            )
        }

        // Preserve aspect ratio without stretching at 1080p target dimensions
        videoEffects.add(
            Presentation.createForWidthAndHeight(
                targetWidth,
                targetHeight,
                Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP
            )
        )

        videoEffects.addAll(globalTextEffects)

        val audioProcessors = mutableListOf<androidx.media3.common.audio.AudioProcessor>()
        if (clip.playbackSpeed != 1f) {
            val sonic = SonicAudioProcessor().apply {
                setSpeed(clip.playbackSpeed)
                setPitch(1f)
            }
            audioProcessors.add(sonic)
        }

        val builder = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(audioProcessors, videoEffects))
            .setRemoveAudio(clip.isMuted || clip.volume <= 0.01f)

        if (clip.isImage) {
            builder.setDurationUs(clip.effectiveDurationMs * 1000L)
            builder.setFrameRate(30)
        }

        return builder.build()
    }

    private fun buildTextOverlayEffects(overlays: List<TextOverlayItem>): List<Effect> {
        if (overlays.isEmpty()) return emptyList()
        val overlayList = overlays.map { item ->
            val spannable = SpannableString(item.text).apply {
                setSpan(
                    ForegroundColorSpan(item.colorHex.toInt()),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            val ndcX = (item.normalizedX * 2f) - 1f
            val ndcY = 1f - (item.normalizedY * 2f)
            val settings = StaticOverlaySettings.Builder()
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(ndcX.coerceIn(-0.9f, 0.9f), ndcY.coerceIn(-0.9f, 0.9f))
                .build()
            TextOverlay.createStaticTextOverlay(spannable, settings)
        }
        return listOf(OverlayEffect(overlayList))
    }

    private fun publishToMediaStore(sourceFile: File, fileName: String): Uri? {
        if (!sourceFile.exists() || sourceFile.length() == 0L) return null
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/TRISVAX Studio")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }

        val itemUri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(itemUri)?.use { out ->
                sourceFile.inputStream().use { input ->
                    input.copyTo(out)
                }
            } ?: throw IllegalStateException("Unable to open MediaStore output stream")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val doneValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(itemUri, doneValues, null, null)
            }
            itemUri
        } catch (_: Exception) {
            resolver.delete(itemUri, null, null)
            null
        }
    }

    private fun hasSufficientStorage(): Boolean {
        return try {
            val stat = StatFs(context.filesDir.absolutePath)
            stat.availableBytes > 120L * 1024L * 1024L
        } catch (_: Exception) {
            true
        }
    }
}
