package com.trisvax.studio.ui.projects

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trisvax.studio.components.TrisvaxClapperIcon
import com.trisvax.studio.components.formatDuration
import com.trisvax.studio.components.formatRelativeDate
import com.trisvax.studio.components.formatStorageSize
import com.trisvax.studio.database.ProjectEntity
import com.trisvax.studio.models.ProjectCategory
import com.trisvax.studio.theme.TrisvaxColors
import java.io.File

@Composable
fun ProjectsScreen(
    viewModel: ProjectsViewModel,
    onOpenProject: (String) -> Unit,
    onOpenMediaPicker: () -> Unit,
    onOpenCamera: () -> Unit
) {
    val state by viewModel.uiState.collectAsState()
    var showCreatePopup by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<ProjectEntity?>(null) }
    var deleteTarget by remember { mutableStateOf<ProjectEntity?>(null) }

    val quickVideoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.createProjectFromUris(uris, onOpenProject)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrisvaxColors.MainBackground)
            .safeDrawingPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(14.dp))

            // Header matching Home_Page_92a909a8.png + Search action
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TrisvaxClapperIcon()
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Projects",
                        color = TrisvaxColors.PrimaryText,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = { viewModel.toggleSearch(!state.isSearchActive) }) {
                    Icon(
                        imageVector = if (state.isSearchActive) Icons.Filled.Close else Icons.Filled.Search,
                        contentDescription = "Search projects",
                        tint = TrisvaxColors.PrimaryText
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "${state.totalCount} projects \u2022 ${formatStorageSize(state.totalStorageBytes)}",
                color = TrisvaxColors.SecondaryText,
                fontSize = 13.sp
            )

            AnimatedVisibility(visible = state.isSearchActive) {
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::updateSearchQuery,
                    placeholder = { Text("Search projects by name...", color = TrisvaxColors.MutedText) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrisvaxColors.PrimaryOrange,
                        unfocusedBorderColor = TrisvaxColors.Border,
                        focusedTextColor = TrisvaxColors.PrimaryText,
                        unfocusedTextColor = TrisvaxColors.PrimaryText,
                        focusedContainerColor = TrisvaxColors.PrimaryCard,
                        unfocusedContainerColor = TrisvaxColors.PrimaryCard
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (state.projects.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No projects yet",
                            color = TrisvaxColors.PrimaryText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Tap + to import clips or capture video.",
                            color = TrisvaxColors.SecondaryText,
                            fontSize = 13.sp
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = 96.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(state.projects, key = { it.id }) { project ->
                        ProjectGridCard(
                            project = project,
                            onClick = { onOpenProject(project.id) },
                            onRename = { renameTarget = project },
                            onDelete = { deleteTarget = project }
                        )
                    }
                }
            }
        }

        // Dim backdrop when popup is open
        if (showCreatePopup) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .clickable { showCreatePopup = false }
            )
        }

        // Create popup matching Create_Button_399036ef.png
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 18.dp, bottom = 22.dp),
            horizontalAlignment = Alignment.End
        ) {
            AnimatedVisibility(visible = showCreatePopup) {
                Surface(
                    color = TrisvaxColors.PrimaryCard,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(1.dp, TrisvaxColors.Border),
                    modifier = Modifier
                        .width(196.dp)
                        .padding(bottom = 12.dp)
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        CreateOptionRow(
                            icon = Icons.Filled.VideoFile,
                            label = "Video",
                            onClick = {
                                showCreatePopup = false
                                quickVideoPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            }
                        )
                        CreateOptionRow(
                            icon = Icons.Filled.CameraAlt,
                            label = "Camera",
                            onClick = {
                                showCreatePopup = false
                                onOpenCamera()
                            }
                        )
                        CreateOptionRow(
                            icon = Icons.Filled.Collections,
                            label = "Gallery",
                            onClick = {
                                showCreatePopup = false
                                onOpenMediaPicker()
                            }
                        )
                    }
                }
            }

            FloatingActionButton(
                onClick = { showCreatePopup = !showCreatePopup },
                containerColor = TrisvaxColors.PrimaryOrange,
                contentColor = TrisvaxColors.PrimaryText,
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.size(60.dp)
            ) {
                Icon(
                    imageVector = if (showCreatePopup) Icons.Filled.Close else Icons.Filled.Add,
                    contentDescription = "Create project",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    }

    renameTarget?.let { target ->
        var text by remember(target.id) { mutableStateOf(target.name) }
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = TrisvaxColors.PrimaryCard,
            title = { Text("Rename project", color = TrisvaxColors.PrimaryText) },
            text = {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TrisvaxColors.PrimaryOrange,
                        unfocusedBorderColor = TrisvaxColors.Border,
                        focusedTextColor = TrisvaxColors.PrimaryText,
                        unfocusedTextColor = TrisvaxColors.PrimaryText
                    )
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameProject(target.id, text)
                        renameTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange)
                ) {
                    Text("Save", color = TrisvaxColors.PrimaryText)
                }
            },
            dismissButton = {
                TextButton(onClick = { renameTarget = null }) {
                    Text("Cancel", color = TrisvaxColors.SecondaryText)
                }
            }
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = TrisvaxColors.PrimaryCard,
            title = { Text("Delete project?", color = TrisvaxColors.PrimaryText) },
            text = {
                Text(
                    text = "This removes \"${target.name}\" and its app-managed editing files. Original device media stays untouched.",
                    color = TrisvaxColors.SecondaryText
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteProject(target.id)
                        deleteTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.ErrorDanger)
                ) {
                    Text("Delete", color = TrisvaxColors.PrimaryText)
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text("Cancel", color = TrisvaxColors.SecondaryText)
                }
            }
        )
    }
}

@Composable
private fun CreateOptionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = TrisvaxColors.PrimaryOrange,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = label,
            color = TrisvaxColors.PrimaryText,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun ProjectGridCard(
    project: ProjectEntity,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        color = TrisvaxColors.PrimaryCard,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, TrisvaxColors.Border),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.95f)
                    .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                    .background(TrisvaxColors.SecondaryCard)
            ) {
                val thumbFile = project.thumbnailPath?.let(::File)?.takeIf { it.exists() }
                if (thumbFile != null) {
                    AsyncImage(
                        model = thumbFile,
                        contentDescription = project.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(TrisvaxColors.SecondaryCard, TrisvaxColors.MainBackground)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = TrisvaxColors.PrimaryOrange,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Duration badge top-left
                Surface(
                    color = Color.Black.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Movie,
                            contentDescription = null,
                            tint = TrisvaxColors.PrimaryText,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = formatDuration(project.durationMs),
                            color = TrisvaxColors.PrimaryText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                // Overflow menu top-right
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Project options",
                            tint = TrisvaxColors.PrimaryText,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        containerColor = TrisvaxColors.SecondaryCard
                    ) {
                        DropdownMenuItem(
                            text = { Text("Rename", color = TrisvaxColors.PrimaryText) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = TrisvaxColors.ErrorDanger) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                Text(
                    text = project.name,
                    color = TrisvaxColors.PrimaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .width(24.dp)
                        .height(2.dp)
                        .background(TrisvaxColors.PrimaryOrange, RoundedCornerShape(1.dp))
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${formatRelativeDate(project.lastModifiedAt)} \u2022 ${formatStorageSize(project.storageSizeBytes)}",
                    color = TrisvaxColors.SecondaryText,
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
        }
    }
}
