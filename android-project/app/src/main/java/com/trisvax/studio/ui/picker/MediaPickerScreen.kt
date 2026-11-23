package com.trisvax.studio.ui.picker

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.trisvax.studio.TrisvaxApplication
import com.trisvax.studio.components.formatDuration
import com.trisvax.studio.theme.TrisvaxColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class DeviceMediaItem(
    val uri: Uri,
    val displayName: String,
    val isVideo: Boolean,
    val durationMs: Long
)

@Composable
fun MediaPickerScreen(
    onBack: () -> Unit,
    onProjectCreated: (String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as TrisvaxApplication
    val scope = rememberCoroutineScope()

    var deviceMedia by remember { mutableStateOf<List<DeviceMediaItem>>(emptyList()) }
    val selectedOrder = remember { mutableStateListOf<Uri>() }
    var isImporting by remember { mutableStateOf(false) }

    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }

    var hasMediaPermission by remember {
        mutableStateOf(
            requiredPermissions.any {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        hasMediaPermission = grants.values.any { it }
    }

    val systemPhotoPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) {
            isImporting = true
            scope.launch {
                val projectId = app.projectRepository.createProjectFromUris(uris)
                isImporting = false
                onProjectCreated(projectId)
            }
        }
    }

    LaunchedEffect(hasMediaPermission) {
        if (hasMediaPermission) {
            deviceMedia = queryDeviceMedia(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TrisvaxColors.MainBackground)
            .safeDrawingPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TrisvaxColors.PrimaryText)
                }
                Text(
                    text = "Select Media",
                    color = TrisvaxColors.PrimaryText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                color = TrisvaxColors.SecondaryCard,
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, TrisvaxColors.Border),
                modifier = Modifier.clickable {
                    systemPhotoPicker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                    )
                }
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.PhotoLibrary,
                        contentDescription = null,
                        tint = TrisvaxColors.PrimaryOrange,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("System Picker", color = TrisvaxColors.PrimaryText, fontSize = 12.sp)
                }
            }
        }

        if (!hasMediaPermission) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    color = TrisvaxColors.PrimaryCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TrisvaxColors.Border)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Browse device videos & photos",
                            color = TrisvaxColors.PrimaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Grant media access to show device clips here, or use the Android System Picker without storage permissions.",
                            color = TrisvaxColors.SecondaryText,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = { permissionLauncher.launch(requiredPermissions) },
                            colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Allow Device Media", color = TrisvaxColors.PrimaryText)
                        }
                    }
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(deviceMedia, key = { it.uri.toString() }) { item ->
                    val selectionIndex = selectedOrder.indexOf(item.uri)
                    val isSelected = selectionIndex >= 0
                    Box(
                        modifier = Modifier
                            .aspectRatio(0.8f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(TrisvaxColors.PrimaryCard)
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) TrisvaxColors.PrimaryOrange else TrisvaxColors.Border,
                                shape = RoundedCornerShape(10.dp)
                            )
                            .clickable {
                                if (isSelected) {
                                    selectedOrder.remove(item.uri)
                                } else {
                                    selectedOrder.add(item.uri)
                                }
                            }
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )

                        if (item.isVideo) {
                            Surface(
                                color = Color.Black.copy(alpha = 0.65f),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = formatDuration(item.durationMs),
                                    color = TrisvaxColors.PrimaryText,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(6.dp)
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isSelected) TrisvaxColors.PrimaryOrange else Color.Black.copy(alpha = 0.5f)
                                )
                                .border(1.dp, TrisvaxColors.PrimaryText, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Text(
                                    text = "${selectionIndex + 1}",
                                    color = TrisvaxColors.PrimaryText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        // Bottom Continue bar
        Surface(
            color = TrisvaxColors.PrimaryCard,
            border = BorderStroke(1.dp, TrisvaxColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${selectedOrder.size} selected",
                    color = TrisvaxColors.SecondaryText,
                    fontSize = 14.sp
                )
                Button(
                    enabled = selectedOrder.isNotEmpty() && !isImporting,
                    onClick = {
                        isImporting = true
                        val uris = selectedOrder.toList()
                        scope.launch {
                            val projectId = app.projectRepository.createProjectFromUris(uris)
                            isImporting = false
                            onProjectCreated(projectId)
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange)
                ) {
                    Text(
                        text = if (isImporting) "Importing..." else "Continue",
                        color = TrisvaxColors.PrimaryText,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private suspend fun queryDeviceMedia(context: Context): List<DeviceMediaItem> = withContext(Dispatchers.IO) {
    val result = mutableListOf<DeviceMediaItem>()
    val projection = arrayOf(
        MediaStore.Files.FileColumns._ID,
        MediaStore.Files.FileColumns.MEDIA_TYPE,
        MediaStore.Files.FileColumns.DISPLAY_NAME,
        MediaStore.Video.Media.DURATION
    )
    val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE}=? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE}=?"
    val selectionArgs = arrayOf(
        MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString()
    )
    val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"
    val collection = MediaStore.Files.getContentUri("external")

    context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
        val typeCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val durCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idCol)
            val mediaType = cursor.getInt(typeCol)
            val isVideo = mediaType == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
            val baseUri = if (isVideo) {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            } else {
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            }
            val uri = ContentUris.withAppendedId(baseUri, id)
            result.add(
                DeviceMediaItem(
                    uri = uri,
                    displayName = cursor.getString(nameCol) ?: "Media",
                    isVideo = isVideo,
                    durationMs = if (isVideo) cursor.getLong(durCol).coerceAtLeast(1000L) else 3000L
                )
            )
        }
    }
    result
}
