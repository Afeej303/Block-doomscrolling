package com.example.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.debug.DebugManager
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.SlateCharcoal

@Composable
fun DeveloperScreen() {
    val debugState by DebugManager.state.collectAsState()
    
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(16.dp)
        .verticalScroll(rememberScrollState())) {
        
        Text("Developer Options", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Color.White)
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Debug Mode", color = Color.White, modifier = Modifier.weight(1f))
            Switch(checked = debugState.isEnabled, onCheckedChange = { DebugManager.toggleDebugMode() })
        }
        
        if (debugState.isEnabled) {
            Spacer(modifier = Modifier.height(16.dp))
            Card(colors = CardDefaults.cardColors(containerColor = SlateCharcoal)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Current Package: ${debugState.currentPackage}", color = Color.White)
                    Text("Current Activity: ${debugState.currentActivity}", color = Color.White)
                    Text("Matched Rule: ${debugState.matchedRule}", color = ElectricTeal)
                    Text("Action Taken: ${debugState.actionTaken}", color = DangerCoral)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("View IDs:", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(debugState.viewIds, color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Texts:", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(debugState.texts, color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Content Descriptions:", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(debugState.contentDescriptions, color = Color.Gray, fontSize = 12.sp)
                }
            }
        }
    }
}
