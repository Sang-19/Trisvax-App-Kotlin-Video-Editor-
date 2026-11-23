package com.trisvax.studio.ui.picker

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.trisvax.studio.TrisvaxApplication
import com.trisvax.studio.theme.TrisvaxColors
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun CameraCaptureScreen(
    onBack: () -> Unit,
    onVideoRecorded: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val app = context.applicationContext as TrisvaxApplication
    val scope = rememberCoroutineScope()

    var hasCameraPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var hasAudioPerm by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        )
    }
    var useFrontCamera by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var activeRecording by remember { mutableStateOf<Recording?>(null) }
    var videoCapture by remember { mutableStateOf<VideoCapture<Recorder>?>(null) }

    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasCameraPerm = grants[Manifest.permission.CAMERA] == true
        hasAudioPerm = grants[Manifest.permission.RECORD_AUDIO] == true
    }

    LaunchedEffect(Unit) {
        if (!hasCameraPerm) {
            permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        }
    }

    DisposableEffect(Unit) {
        onDispose { activeRecording?.stop() }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrisvaxColors.MainBackground)
            .safeDrawingPadding()
    ) {
        if (!hasCameraPerm) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera permission is required to record video.", color = TrisvaxColors.PrimaryText)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = { permLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) },
                    colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange)
                ) {
                    Text("Grant Camera", color = TrisvaxColors.PrimaryText)
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                    }
                },
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }
                        val recorder = Recorder.Builder()
                            .setQualitySelector(QualitySelector.from(Quality.FHD))
                            .build()
                        val capture = VideoCapture.withOutput(recorder)
                        videoCapture = capture

                        val selector = if (useFrontCamera) {
                            CameraSelector.DEFAULT_FRONT_CAMERA
                        } else {
                            CameraSelector.DEFAULT_BACK_CAMERA
                        }
                        try {
                            provider.unbindAll()
                            provider.bindToLifecycle(lifecycleOwner, selector, preview, capture)
                        } catch (_: Exception) {
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close camera", tint = TrisvaxColors.PrimaryText)
            }

            IconButton(
                onClick = { if (!isRecording) useFrontCamera = !useFrontCamera },
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
            ) {
                Icon(Icons.Filled.FlipCameraAndroid, contentDescription = "Flip camera", tint = TrisvaxColors.PrimaryText)
            }
        }

        if (hasCameraPerm) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 34.dp)
                    .size(76.dp)
                    .clip(CircleShape)
                    .border(4.dp, TrisvaxColors.PrimaryText, CircleShape)
                    .padding(6.dp)
                    .clip(if (isRecording) RoundedCornerShape(14.dp) else CircleShape)
                    .background(TrisvaxColors.PrimaryOrange)
                    .clickable {
                        val capture = videoCapture ?: return@clickable
                        if (isRecording) {
                            activeRecording?.stop()
                            isRecording = false
                        } else {
                            val outFile = File(context.cacheDir, "cam_${System.currentTimeMillis()}.mp4")
                            val options = FileOutputOptions.Builder(outFile).build()
                            var pending = capture.output.prepareRecording(context, options)
                            if (hasAudioPerm) {
                                pending = pending.withAudioEnabled()
                            }
                            activeRecording = pending.start(ContextCompat.getMainExecutor(context)) { event ->
                                if (event is VideoRecordEvent.Finalize && !event.hasError()) {
                                    scope.launch {
                                        val id = app.projectRepository.createProjectFromUris(
                                            listOf(Uri.fromFile(outFile)),
                                            defaultName = "Camera clip"
                                        )
                                        onVideoRecorded(id)
                                    }
                                }
                            }
                            isRecording = true
                        }
                    }
            )
        }
    }
}
