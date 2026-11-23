package com.trisvax.studio.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.trisvax.studio.models.ExportUiState
import com.trisvax.studio.theme.TrisvaxColors

@Composable
fun ExportDialog(
    exportState: ExportUiState,
    aspectLabel: String,
    onCancel: () -> Unit,
    onDismissError: () -> Unit
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            color = TrisvaxColors.PrimaryCard,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, TrisvaxColors.Border),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                when (exportState) {
                    is ExportUiState.Exporting -> {
                        Text(
                            text = "Exporting 1080p MP4",
                            color = TrisvaxColors.PrimaryText,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${exportState.statusMessage} ($aspectLabel \u2022 30 FPS)",
                            color = TrisvaxColors.SecondaryText,
                            fontSize = 13.sp
                        )
                        LinearProgressIndicator(
                            progress = { (exportState.progressPercent / 100f).coerceIn(0f, 1f) },
                            color = TrisvaxColors.PrimaryOrange,
                            trackColor = TrisvaxColors.SecondaryCard,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                        )
                        Text(
                            text = "${exportState.progressPercent}%",
                            color = TrisvaxColors.SecondaryOrange,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        OutlinedButton(
                            onClick = onCancel,
                            border = BorderStroke(1.dp, TrisvaxColors.Border),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Cancel Export", color = TrisvaxColors.PrimaryText)
                        }
                    }

                    is ExportUiState.Error -> {
                        Text(
                            text = "Export Failed",
                            color = TrisvaxColors.ErrorDanger,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = exportState.message,
                            color = TrisvaxColors.SecondaryText,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = onDismissError,
                            colors = ButtonDefaults.buttonColors(containerColor = TrisvaxColors.PrimaryOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close", color = TrisvaxColors.PrimaryText)
                        }
                    }

                    else -> Unit
                }
            }
        }
    }
}
