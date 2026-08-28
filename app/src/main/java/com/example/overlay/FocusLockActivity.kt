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
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Settings
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
            val baseRemainingSeconds = runningState?.remainingSeconds ?: 0
            val totalSeconds = runningState?.totalSeconds?.takeIf { it > 0 } ?: 1800

            var displaySeconds by remember { mutableIntStateOf(baseRemainingSeconds) }

            LaunchedEffect(baseRemainingSeconds) {
                displaySeconds = baseRemainingSeconds
            }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(1000L)
                    if (displaySeconds > 0) {
                        displaySeconds--
                    }
                }
            }

            val minutes = displaySeconds / 60
            val seconds = displaySeconds % 60
            val timerText = String.format("%02d : %02d", minutes, seconds)
            
            val progress = if (totalSeconds > 0) (displaySeconds.toFloat() / totalSeconds.toFloat()).coerceIn(0f, 1f) else 0f

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF131313))
            ) {
                // Wide, subtle ambient glow
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .fillMaxWidth()
                        .fillMaxHeight(0.6f)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0xFFc8431e).copy(alpha = 0.15f), Color.Transparent),
                                radius = 800f
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
                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Brush,
                                contentDescription = "Focus",
                                tint = Color(0xFF968e89)
                            )
                        }
                        Text(
                            text = "ZenScroll",
                            color = Color(0xFFffb5a1),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        IconButton(onClick = {}) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFF968e89)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Circular Progress Timer
                    Box(
                        modifier = Modifier.size(300.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                            val strokeWidth = 8.dp.toPx()
                            // Background track
                            drawArc(
                                color = Color(0xFF2c2c2c),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            // Outer glow 1 (wide)
                            drawArc(
                                color = Color(200, 67, 30, 38), // 0.15 alpha
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth + 32.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Outer glow 2 (tight)
                            drawArc(
                                color = Color(200, 67, 30, 76), // 0.3 alpha
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                style = Stroke(width = strokeWidth + 12.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Active progress arc
                            drawArc(
                                color = Color(0xFFc8431e),
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
                                fontSize = 46.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                                letterSpacing = 0.05.em
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "DEEP FOCUS ACTIVE",
                                color = Color(0xFF968e89),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.15.em
                            )
                        }
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Allowed Apps Card
                    AllowedAppsCard()

                    Spacer(modifier = Modifier.weight(1f))
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
            .padding(horizontal = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1e1e1e)),
        border = BorderStroke(1.dp, Color(0xFF353534))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ALLOWED APPS",
                color = Color(0xFFe5e2e1),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.15.em
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.heightIn(max = 140.dp)
            ) {
                items(allowedPackages) { pkg ->
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
                                .clip(RoundedCornerShape(12.dp))
                                .clickable {
                                    pm.getLaunchIntentForPackage(pkg)?.let {
                                        context.startActivity(it)
                                    }
                                }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color(0xFF2a2a2a), RoundedCornerShape(12.dp))
                                    .border(1.dp, Color(0xFF353534), RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = icon.toBitmap().asImageBitmap(),
                                    contentDescription = label,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = label,
                                color = Color(0xFF968e89),
                                fontSize = 11.sp,
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
