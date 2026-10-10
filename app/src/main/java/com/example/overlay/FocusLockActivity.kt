package com.example.overlay

import android.content.Intent
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.example.data.BlockDatabase
import com.example.focus.FocusManager
import com.example.focus.FocusState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class FocusLockActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        setContent {
            val focusState by FocusManager.state.collectAsState()
            val allowedPackages by FocusManager.allowedAppsFlow.collectAsState()
            val context = LocalContext.current
            
            LaunchedEffect(focusState) {
                if (focusState !is FocusState.Running) {
                    finish()
                }
            }
            
            LaunchedEffect(Unit) {
                if (FocusManager.allowedApps.isEmpty()) {
                    withContext(Dispatchers.IO) {
                        try {
                            val db = BlockDatabase.getDatabase(context.applicationContext)
                            val settings = db.dao().getFocusSettings()
                            settings?.let { s ->
                                val list = s.allowedApps.split(",").filter { it.isNotBlank() }
                                if (list.isNotEmpty()) {
                                    FocusManager.updateAllowedApps(list)
                                }
                            }
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
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
                    color = android.graphics.Color.argb((255 * 0.40f).toInt(), 0xD8, 0x4B, 0x29) // 40% opacity
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF151312))
            ) {
                // Background Gradients
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Full screen faint warm glow (peaks at ring, fades towards bottom)
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFFD84B29).copy(alpha = 0.14f), Color.Transparent),
                            radius = size.height * 0.85f,
                            center = Offset(size.width / 2f, size.height * 0.42f)
                        )
                    )
                    // Upper ambient tint
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(Color(0xFF2C2522).copy(alpha = 0.45f), Color.Transparent),
                            radius = 800f,
                            center = Offset(size.width / 2f, 0f)
                        )
                    )
                }

                val hasAllowedApps = allowedPackages.isNotEmpty()

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (hasAllowedApps) {
                        Spacer(modifier = Modifier.weight(0.35f))
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // Circular Progress Timer
                    val timerFraction = if (hasAllowedApps && allowedPackages.size > 4) 0.60f else 0.72f
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(timerFraction)
                            .aspectRatio(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 5.dp.toPx()
                            
                            // Larger, very faint outer glow background for the ring
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(Color(0xFFD84B29).copy(alpha = 0.08f), Color.Transparent),
                                    radius = size.width * 0.85f,
                                    center = Offset(size.width / 2f, size.height / 2f)
                                ),
                                radius = size.width * 0.85f,
                                center = Offset(size.width / 2f, size.height / 2f)
                            )
                            
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
                                blurredGlowPaint.strokeWidth = strokeWidth + 12.dp.toPx() // noticeably wider
                                blurredGlowPaint.maskFilter = BlurMaskFilter(20.dp.toPx(), BlurMaskFilter.Blur.NORMAL)
                                
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
                                fontSize = if (hasAllowedApps && allowedPackages.size > 4) 30.sp else 36.sp,
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

                    if (hasAllowedApps) {
                        Spacer(modifier = Modifier.weight(0.45f))

                        // Allowed Apps Card
                        Box(
                            modifier = Modifier
                                .weight(1.3f, fill = false)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 24.dp)
                        ) {
                            AllowedAppsCard(allowedPackages = allowedPackages.toList())
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
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

data class AllowedAppItem(
    val packageName: String,
    val label: String,
    val icon: android.graphics.drawable.Drawable
)

@Composable
fun AllowedAppsCard(allowedPackages: List<String>) {
    val context = LocalContext.current
    val pm = context.packageManager
    
    val apps = remember(allowedPackages) {
        allowedPackages.mapNotNull { pkg ->
            try {
                val appInfo = pm.getApplicationInfo(pkg, 0)
                val icon = pm.getApplicationIcon(appInfo)
                val label = pm.getApplicationLabel(appInfo).toString()
                AllowedAppItem(pkg, label, icon)
            } catch (e: Exception) {
                null
            }
        }
    }
    
    if (apps.isEmpty()) return

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("allowed_apps_card"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF24201F)),
        border = BorderStroke(1.dp, Color(0xFF352F2E))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "ALLOWED APPS (${apps.size})",
                    color = Color(0xFFBCAA99),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.1.em
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            
            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 240.dp)
                    .testTag("allowed_apps_grid"),
                contentPadding = PaddingValues(vertical = 4.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(apps, key = { it.packageName }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                pm.getLaunchIntentForPackage(app.packageName)?.let { launchIntent ->
                                    launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(launchIntent)
                                }
                            }
                            .testTag("allowed_app_${app.packageName}")
                            .padding(vertical = 4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .background(Color(0xFF302B2A), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                bitmap = app.icon.toBitmap().asImageBitmap(),
                                contentDescription = app.label,
                                modifier = Modifier.size(30.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = app.label,
                            color = Color(0xFFC7B8B5),
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            lineHeight = 12.sp
                        )
                    }
                }
            }
        }
    }
}
