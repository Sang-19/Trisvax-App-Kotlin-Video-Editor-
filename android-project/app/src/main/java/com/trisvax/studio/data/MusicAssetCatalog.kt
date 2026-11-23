package com.trisvax.studio.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import com.trisvax.studio.models.BundledMusicAsset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Loads bundled music tracks from app/src/main/assets/music/
 * and materializes them to app-private cache when selected so Media3 ExoPlayer
 * and Media3 Transformer can read them uniformly via File/Uri.
 */
class MusicAssetCatalog(private val context: Context) {

    suspend fun listBundledTracks(): List<BundledMusicAsset> = withContext(Dispatchers.IO) {
        val assetManager = context.assets
        val files = assetManager.list("music")?.filter {
            it.endsWith(".mp3", ignoreCase = true) ||
                it.endsWith(".m4a", ignoreCase = true) ||
                it.endsWith(".wav", ignoreCase = true) ||
                it.endsWith(".ogg", ignoreCase = true)
        }.orEmpty()

        files.map { fileName ->
            val copiedFile = materializeAssetToPrivateStorage(fileName)
            val durationMs = extractDurationMs(copiedFile)
            BundledMusicAsset(
                fileName = fileName,
                title = fileName.substringBeforeLast(".")
                    .replace("_", " ")
                    .split(" ")
                    .joinToString(" ") { part ->
                        part.replaceFirstChar { it.uppercase() }
                    },
                assetPath = Uri.fromFile(copiedFile).toString(),
                durationMs = durationMs
            )
        }
    }

    fun materializeAssetToPrivateStorage(fileName: String): File {
        val musicDir = File(context.filesDir, "bundled_music").apply { mkdirs() }
        val target = File(musicDir, fileName)
        if (!target.exists() || target.length() == 0L) {
            context.assets.open("music/$fileName").use { input ->
                target.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }
        return target
    }

    private fun extractDurationMs(file: File): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 15_000L
        } catch (_: Exception) {
            15_000L
        } finally {
            retriever.release()
        }
    }
}
