package com.trisvax.studio.ui.export

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trisvax.studio.components.TrisvaxBrandBadge
import com.trisvax.studio.theme.TrisvaxColors

@Composable
fun SaveConfirmationScreen(
    onGoToHome: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrisvaxColors.MainBackground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        TrisvaxBrandBadge(modifier = Modifier.align(Alignment.TopStart))

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            color = TrisvaxColors.PrimaryCard,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, TrisvaxColors.Border)
        ) {
            Column(
                modifier = Modifier.padding(26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(TrisvaxColors.PrimaryOrange.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(TrisvaxColors.PrimaryOrange),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Saved",
                            tint = TrisvaxColors.PrimaryText,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }

                Text(
                    text = "Your project has been saved",
                    color = TrisvaxColors.PrimaryText,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )

                Button(
                    onClick = onGoToHome,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrisvaxColors.PrimaryOrange,
                        contentColor = TrisvaxColors.PrimaryText
                    )
                ) {
                    Icon(Icons.Filled.Home, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Go to Home",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
