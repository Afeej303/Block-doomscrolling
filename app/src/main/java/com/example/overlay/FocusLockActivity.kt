package com.example.overlay

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.focus.FocusManager
import com.example.focus.FocusState

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
            
            val baseRemainingSeconds = (focusState as? FocusState.Running)?.remainingSeconds ?: 0
            var displaySeconds by remember { androidx.compose.runtime.mutableStateOf(baseRemainingSeconds) }

            androidx.compose.runtime.LaunchedEffect(baseRemainingSeconds) {
                displaySeconds = baseRemainingSeconds
            }

            androidx.compose.runtime.LaunchedEffect(Unit) {
                while (true) {
                    kotlinx.coroutines.delay(1000L)
                    if (displaySeconds > 0) {
                        displaySeconds--
                    }
                }
            }

            val hours = displaySeconds / 3600
            val minutes = (displaySeconds % 3600) / 60
            val seconds = displaySeconds % 60
            val timerText = if (hours > 0) {
                String.format("%02d:%02d:%02d", hours, minutes, seconds)
            } else {
                String.format("%02d:%02d", minutes, seconds)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF1E1E1E)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "Focus Mode Active",
                        color = Color.White,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    if (displaySeconds > 0) {
                        Text(
                            timerText,
                            color = Color(0xFF4CAF50),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    Text(
                        "Return to your task.",
                        color = Color.Gray,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    AllowedAppsGrid()
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
fun AllowedAppsGrid() {
    val context = LocalContext.current
    val pm = context.packageManager
    
    val allowedPackages = remember { FocusManager.allowedApps.toList() }
    
    if (allowedPackages.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Allowed Apps", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(16.dp))
        
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            items(allowedPackages) { pkg ->
                var appInfo: android.content.pm.ApplicationInfo? = null
                var icon: android.graphics.drawable.Drawable? = null
                var label: String = ""
                
                try {
                    appInfo = pm.getApplicationInfo(pkg, 0)
                    icon = pm.getApplicationIcon(appInfo)
                    label = pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    // Ignore
                }
                
                if (appInfo != null && icon != null) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                val launchIntent = pm.getLaunchIntentForPackage(pkg)
                                if (launchIntent != null) {
                                    context.startActivity(launchIntent)
                                }
                            }
                            .padding(8.dp)
                    ) {
                        Image(
                            bitmap = icon.toBitmap().asImageBitmap(),
                            contentDescription = label,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = label,
                            color = Color.White,
                            fontSize = 12.sp,
                            maxLines = 1,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}
