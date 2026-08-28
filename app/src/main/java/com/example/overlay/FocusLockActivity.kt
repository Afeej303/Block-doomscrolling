package com.example.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.focus.FocusManager
import com.example.focus.FocusState
import kotlinx.coroutines.delay

class FocusLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val focusState by FocusManager.state.collectAsState()
            
            LaunchedEffect(focusState) {
                if (focusState !is FocusState.Running) {
                    finish()
                }
            }
            
            val runningState = focusState as? FocusState.Running
            val displaySeconds = runningState?.remainingSeconds ?: 0
            val totalSeconds = runningState?.totalSeconds?.takeIf { it > 0 } ?: 1800

            val minutes = displaySeconds / 60
            val seconds = displaySeconds % 60
            val timerText = String.format("%02d:%02d", minutes, seconds)
            
            // Orange arc represents time remaining (starts full, drains to 0)
            val progress = if (totalSeconds > 0) (displaySeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF151312))
            ) {
                // Background Gradients
                // Upper ambient tint
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFF2C2522).copy(alpha = 0.6f), Color.Transparent),
                                radius = 1500f
                            )
                        )
                )
                // Center tint behind ring
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(0.7f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFc8431e).copy(alpha = 0.12f), Color.Transparent),
                                radius = 1200f
                            )
                        )
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.weight(0.22f))

                    // Circular Progress Timer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 10.dp.toPx()
                            // Background track
                            drawArc(
                                color = Color(0xFF373231),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            // Active progress arc
                            drawArc(
                                color = Color(0xFFD84B29),
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                        }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = timerText,
                                color = Color(0xFFffb5a1),
                                fontSize = 36.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.0.em
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "DEEP FOCUS ACTIVE",
                                color = Color(0xFF968e89),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.1.em
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Allowed Apps Card
                    Box(modifier = Modifier.padding(bottom = 32.dp)) {
                        AllowedAppsCard()
                    }
                }
            }
        }
    }
    
    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        // Prevent back navigation
    }
}

@Composable
fun AllowedAppsCard() {
    val context = LocalContext.current
    val pm = context.packageManager
    val allowedPackages = remember { FocusManager.allowedApps.toList() }
    
    if (allowedPackages.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1D1918)),
        border = BorderStroke(1.dp, Color(0xFF2A2524))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ALLOWED APPS",
                color = Color(0xFF968e89),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val maxTileSize = 52.dp
                val minTileSize = 40.dp
                val spacing = 8.dp
                val totalSpacing = spacing * 3 // Calculate for 4 items to keep size consistent
                
                val itemWidth = ((availableWidth - totalSpacing) / 4).coerceIn(minTileSize, maxTileSize)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(spacing, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.Top
                ) {
                    allowedPackages.take(4).forEach { pkg ->
                        var icon: android.graphics.drawable.Drawable? = null
                        var label = ""
                        try {
                            val appInfo = pm.getApplicationInfo(pkg, 0)
                            icon = pm.getApplicationIcon(appInfo)
                            label = pm.getApplicationLabel(appInfo).toString()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        
                        if (icon != null) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(itemWidth)
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        pm.getLaunchIntentForPackage(pkg)?.let {
                                            context.startActivity(it)
                                        }
                                    }
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(itemWidth)
                                        .background(Color(0xFF302B2A), RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = icon.toBitmap().asImageBitmap(),
                                        contentDescription = label,
                                        modifier = Modifier.size(itemWidth * 0.6f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = label,
                                    color = Color(0xFF7A706D),
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
