package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.focus.FocusManager
import com.example.focus.FocusState
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.SlateGrayLight

@Composable
fun FocusScreen() {
    val context = LocalContext.current
    val focusState by FocusManager.state.collectAsState()
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Focus Mode",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Start a focused session to block distracting apps.",
            color = SlateGrayLight,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(32.dp))
        
        if (focusState is FocusState.Running) {
            Button(
                onClick = { FocusManager.stop() },
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
            ) {
                Text("Stop Focus Session", color = Color.White, fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = { FocusManager.start(context) },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal)
            ) {
                Text("Start Focus Session", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        }
    }
}
