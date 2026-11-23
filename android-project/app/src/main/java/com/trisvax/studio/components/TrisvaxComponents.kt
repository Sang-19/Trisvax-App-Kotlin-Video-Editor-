package com.trisvax.studio.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trisvax.studio.theme.TrisvaxColors

@Composable
fun TrisvaxBrandBadge(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = TrisvaxColors.PrimaryCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, TrisvaxColors.Border)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(TrisvaxColors.PrimaryOrange),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "T",
                    color = TrisvaxColors.MainBackground,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "TRISVAX",
                color = TrisvaxColors.PrimaryText,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                letterSpacing = 1.1.sp
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "STUDIO",
                color = TrisvaxColors.SecondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
                letterSpacing = 1.2.sp
            )
        }
    }
}

@Composable
fun TrisvaxClapperIcon(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(TrisvaxColors.PrimaryOrange),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Movie,
            contentDescription = "TRISVAX Projects",
            tint = TrisvaxColors.MainBackground,
            modifier = Modifier.size(22.dp)
        )
    }
}

@Composable
fun EditorToolButton(
    label: String,
    icon: ImageVector,
    isActive: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(if (isActive) TrisvaxColors.PrimaryOrange else TrisvaxColors.PrimaryCard)
                .border(
                    width = 1.dp,
                    color = if (isActive) TrisvaxColors.SecondaryOrange else TrisvaxColors.Border,
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = TrisvaxColors.PrimaryText,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = label,
            color = if (isActive) TrisvaxColors.SecondaryOrange else TrisvaxColors.PrimaryText,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%02d:%02d".format(minutes, seconds)
}

fun formatShortTimecode(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun formatStorageSize(bytes: Long): String {
    if (bytes <= 0L) return "0 KB"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return "${kb.toInt().coerceAtLeast(1)} KB"
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.1f GB", gb)
}

fun formatRelativeDate(timestampMs: Long): String {
    val diffMs = (System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)
    val mins = diffMs / 60_000L
    if (mins < 5) return "Now"
    if (mins < 60) return "${mins}m ago"
    val hours = mins / 60L
    if (hours < 24) return "${hours}h ago"
    val days = hours / 24L
    return "${days}d ago"
}
