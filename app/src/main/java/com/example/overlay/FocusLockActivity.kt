package com.example.overlay

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Paint
import android.graphics.BlurMaskFilter
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
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
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

            val blurredGlowPaint = remember {
                Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    color = android.graphics.Color.argb((255 * 0.20f).toInt(), 0xD8, 0x4B, 0x29) // ~11% opacity
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF151312))
            ) {
                // Background Gradients
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Upper ambient tint
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2C2522).copy(alpha = 0.55f), Color.Transparent),
                            radius = 800f,
                            center = Offset(size.width / 2f, 0f)
                        )
                    )
                }

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
                                imageVector = androidx.compose.material.icons.Icons.Default.Face,
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
                                imageVector = androidx.compose.material.icons.Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = Color(0xFF968e89)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(28.dp))

                    // Circular Progress Timer
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.76f)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 10.dp.toPx()
                            
                            // Faint warm radial fill centered on the ring
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFD84B29).copy(alpha = 0.04f), Color.Transparent),
                                    radius = size.width * 0.55f,
                                    center = Offset(size.width / 2f, size.height / 2f)
                                ),
                                radius = size.width * 0.55f,
                                center = Offset(size.width / 2f, size.height / 2f)
                            )

                            // Blurred orange arc glow
                            drawIntoCanvas { canvas ->
                                blurredGlowPaint.strokeWidth = strokeWidth + 4.dp.toPx() // slightly wider
                                blurredGlowPaint.maskFilter = BlurMaskFilter(25.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                                
                                val rect = android.graphics.RectF(0f, 0f, size.width, size.height)
                                canvas.nativeCanvas.drawArc(
                                    rect,
                                    -90f,
                                    360f * progress,
                                    false,
                                    blurredGlowPaint
                                )
                            }

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
        colors = CardDefaults.cardColors(containerColor = Color(0xFF24201F)),
        border = BorderStroke(1.dp, Color(0xFF352F2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "ALLOWED APPS",
                color = Color(0xFFBCAA99),
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.1.em
            )
            Spacer(modifier = Modifier.height(16.dp))
            
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val availableWidth = maxWidth
                val maxTileSize = 52.dp
                val minTileSize = 44.dp
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
                                    color = Color(0xFF9A8D8A),
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
