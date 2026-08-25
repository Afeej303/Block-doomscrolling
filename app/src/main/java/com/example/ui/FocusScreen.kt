package com.example.ui

import android.content.pm.PackageManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.example.data.FocusSettings
import com.example.data.FocusScheduleItem
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import com.example.focus.FocusManager
import com.example.focus.FocusState
import com.example.ui.theme.DangerCoral
import com.example.ui.theme.ElectricTeal
import com.example.ui.theme.SlateGrayLight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItemInfo(
    val packageName: String,
    val name: String,
    val icon: android.graphics.Bitmap
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusScreen(viewModel: BlockViewModel) {
    val context = LocalContext.current
    val focusState by FocusManager.state.collectAsState()
    val focusSettings by viewModel.focusSettings.collectAsState()
    val isLocked = viewModel.isConfigurationLocked()
    
    var showAppPicker by remember { mutableStateOf(false) }
    var appsList by remember { mutableStateOf<List<AppItemInfo>>(emptyList()) }
    
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = android.content.Intent(android.content.Intent.ACTION_MAIN, null)
            intent.addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            val allApps = pm.queryIntentActivities(intent, 0)
            val loadedApps = allApps.mapNotNull { resolveInfo ->
                try {
                    val pkgName = resolveInfo.activityInfo.packageName
                    if (pkgName == context.packageName) return@mapNotNull null
                    val name = resolveInfo.loadLabel(pm).toString()
                    val icon = resolveInfo.loadIcon(pm).toBitmap()
                    AppItemInfo(pkgName, name, icon)
                } catch (e: Exception) {
                    null
                }
            }.sortedBy { it.name }
            appsList = loadedApps
        }
    }
    
    val settings = focusSettings ?: FocusSettings()
    val allowedPackages = settings.allowedApps.split(",").filter { it.isNotBlank() }.toSet()

    if (showAppPicker) {
        AppPickerDialog(
            apps = appsList,
            selectedPackages = allowedPackages,
            onDismiss = { showAppPicker = false },
            onSave = { selected ->
                viewModel.updateFocusSettings(settings.copy(allowedApps = selected.joinToString(",")))
                showAppPicker = false
            }
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Focus Mode",
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = DangerCoral,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (isLocked) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Focus configurations are locked by the security timer.",
                    fontSize = 12.sp,
                    color = DangerCoral,
                    fontWeight = FontWeight.Medium
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
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
            Spacer(modifier = Modifier.height(32.dp))
            
            // Schedule Configuration
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2C2C))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Enable Schedule", color = Color.White, fontWeight = FontWeight.Bold)
                        Switch(
                            checked = settings.isScheduleEnabled,
                            onCheckedChange = { 
                                if (!isLocked) {
                                    viewModel.updateFocusSettings(settings.copy(isScheduleEnabled = it))
                                }
                            },
                            enabled = !isLocked
                        )
                    }
                    if (settings.isScheduleEnabled) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Active Focus Schedules", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        var draftSchedules by remember(settings.schedules) { mutableStateOf(settings.getScheduleList()) }
                        val hasUnsavedChanges = draftSchedules != settings.getScheduleList()

                        draftSchedules.forEachIndexed { index, item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .background(Color(0xFF3C3C3C), RoundedCornerShape(8.dp))
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Start", color = SlateGrayLight, fontSize = 10.sp)
                                        TimePickerButton(item.startHour, item.startMinute, enabled = !isLocked) { h, m ->
                                            if (!isLocked) {
                                                val newList = draftSchedules.toMutableList()
                                                newList[index] = item.copy(startHour = h, startMinute = m)
                                                draftSchedules = newList
                                            }
                                        }
                                    }
                                    Text("to", color = SlateGrayLight, fontSize = 12.sp)
                                    Column {
                                        Text("End", color = SlateGrayLight, fontSize = 10.sp)
                                        TimePickerButton(item.endHour, item.endMinute, enabled = !isLocked) { h, m ->
                                            if (!isLocked) {
                                                val newList = draftSchedules.toMutableList()
                                                newList[index] = item.copy(endHour = h, endMinute = m)
                                                draftSchedules = newList
                                            }
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        if (!isLocked) {
                                            val newList = draftSchedules.toMutableList().apply { removeAt(index) }
                                            draftSchedules = newList
                                        }
                                    },
                                    enabled = !isLocked
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Schedule", tint = if (isLocked) SlateGrayLight else Color.Red)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (!isLocked) {
                                        val newList = draftSchedules + FocusScheduleItem(9, 0, 17, 0)
                                        draftSchedules = newList
                                    }
                                },
                                enabled = !isLocked,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4C4C4C)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = "Add Schedule", tint = Color.White)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add", color = Color.White, fontWeight = FontWeight.Bold)
                            }

                            if (hasUnsavedChanges) {
                                Button(
                                    onClick = {
                                        if (!isLocked) {
                                            viewModel.updateFocusSettings(settings.copy(schedules = FocusScheduleItem.serializeList(draftSchedules)))
                                        }
                                    },
                                    enabled = !isLocked,
                                    colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save Schedule", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(32.dp))
            
            // Allowed Apps
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Allowed Apps",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                TextButton(
                    onClick = { if (!isLocked) showAppPicker = true },
                    enabled = !isLocked
                ) {
                    Text("Edit", color = if (isLocked) SlateGrayLight else ElectricTeal)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val allowedAppsList = appsList.filter { allowedPackages.contains(it.packageName) }
        
        if (allowedAppsList.isEmpty()) {
            item {
                Text("No apps allowed. Everything will be blocked.", color = SlateGrayLight, fontSize = 14.sp)
            }
        } else {
            items(allowedAppsList) { app ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(8.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = app.icon.asImageBitmap(),
                        contentDescription = app.name,
                        modifier = Modifier.size(40.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(text = app.name, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
fun TimePickerButton(hour: Int, minute: Int, enabled: Boolean = true, onTimeSelected: (Int, Int) -> Unit) {
    val context = LocalContext.current
    Button(
        onClick = {
            if (enabled) {
                android.app.TimePickerDialog(
                    context,
                    { _, h, m -> onTimeSelected(h, m) },
                    hour,
                    minute,
                    false
                ).show()
            }
        },
        enabled = enabled,
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3C3C3C))
    ) {
        val amPm = if (hour >= 12) "PM" else "AM"
        val displayHour = if (hour == 0) 12 else if (hour > 12) hour - 12 else hour
        val timeStr = String.format("%02d:%02d %s", displayHour, minute, amPm)
        Text(timeStr, color = if (enabled) Color.White else SlateGrayLight)
    }
}

@Composable
fun AppPickerDialog(
    apps: List<AppItemInfo>,
    selectedPackages: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit
) {
    var tempSelected by remember { mutableStateOf(selectedPackages) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Allowed Apps") },
        text = {
            LazyColumn {
                items(apps) { app ->
                    val isSelected = tempSelected.contains(app.packageName)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                tempSelected = if (isSelected) {
                                    tempSelected - app.packageName
                                } else {
                                    tempSelected + app.packageName
                                }
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = isSelected,
                            onCheckedChange = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Image(
                            bitmap = app.icon.asImageBitmap(),
                            contentDescription = app.name,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(app.name)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(tempSelected) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
