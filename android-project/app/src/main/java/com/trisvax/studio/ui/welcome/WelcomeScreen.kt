package com.trisvax.studio.ui.welcome

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trisvax.studio.components.TrisvaxBrandBadge
import com.trisvax.studio.theme.TrisvaxColors

@Composable
fun WelcomeScreen(
    onOpenStudio: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TrisvaxColors.MainBackground)
            .safeDrawingPadding()
            .padding(horizontal = 20.dp, vertical = 18.dp)
    ) {
        TrisvaxBrandBadge(
            modifier = Modifier.align(Alignment.TopStart)
        )

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            color = TrisvaxColors.PrimaryCard,
            shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, TrisvaxColors.Border)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                Text(
                    text = "Start creating",
                    color = TrisvaxColors.PrimaryText,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )

                Button(
                    onClick = onOpenStudio,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TrisvaxColors.PrimaryOrange,
                        contentColor = TrisvaxColors.PrimaryText
                    )
                ) {
                    Text(
                        text = "Open your studio  \u2192",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            color = TrisvaxColors.PrimaryCard,
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, TrisvaxColors.Border)
        ) {
            Text(
                text = "No account. No uploads. Your media stays on your device.",
                color = TrisvaxColors.SecondaryText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)
            )
        }
    }
}
