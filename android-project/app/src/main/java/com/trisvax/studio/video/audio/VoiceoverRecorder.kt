package com.trisvax.studio.video.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceoverRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    private var startTimestampMs: Long = 0L

    fun startRecording(outputFile: File): Boolean {
        stopAndRelease()
        return try {
            outputFile.parentFile?.mkdirs()
            val mr = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mr.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(128000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            recorder = mr
            currentFile = outputFile
            startTimestampMs = System.currentTimeMillis()
            true
        } catch (_: Exception) {
            stopAndRelease()
            false
        }
    }

    fun stopRecording(): Pair<File, Long>? {
        val file = currentFile ?: return null
        val durationMs = (System.currentTimeMillis() - startTimestampMs).coerceAtLeast(500L)
        return try {
            recorder?.stop()
            file to durationMs
        } catch (_: Exception) {
            null
        } finally {
            stopAndRelease()
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            recorder?.maxAmplitude ?: 0
        } catch (_: Exception) {
            0
        }
    }

    fun stopAndRelease() {
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }
}
