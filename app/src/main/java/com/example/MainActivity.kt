package com.example

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.AppBlockConfig
import com.example.data.SecuritySettings
import androidx.compose.ui.draw.alpha
import com.example.ui.BlockViewModel
import com.example.ui.theme.*
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    
    private val viewModel: BlockViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Handle incoming blocked app triggers from Accessibility Service
        intent?.let { handleIntent(it) }
        
        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        intent?.let {
            if (it.getBooleanExtra("SHOW_UNLOCK_PROMPT", false)) {
                viewModel.forceShowUnlockPrompt.value = true
                it.removeExtra("SHOW_UNLOCK_PROMPT")
            }
        }
    }

    private fun handleIntent(intent: Intent) {
        val packageBlocked = intent.getStringExtra("BLOCKED_APP_PACKAGE")
        val nameBlocked = intent.getStringExtra("BLOCKED_APP_NAME")
        if (packageBlocked != null && nameBlocked != null) {
            viewModel.blockedAppNotification.value = Pair(packageBlocked, nameBlocked)
        }
        if (intent.getBooleanExtra("SHOW_UNLOCK_PROMPT", false)) {
            viewModel.forceShowUnlockPrompt.value = true
            intent.removeExtra("SHOW_UNLOCK_PROMPT")
        }
    }
}

@Composable
fun MainScreen(viewModel: BlockViewModel) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val appConfigs by viewModel.appConfigs.collectAsStateWithLifecycle()
    val securitySettings by viewModel.securitySettings.collectAsStateWithLifecycle()
    val blockedNotification by viewModel.blockedAppNotification.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Periodically inspect accessibility status (every 2 seconds)
    var isAccessActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isAccessActive = viewModel.isAccessibilityEnabled(context)
            delay(2000)
        }
    }

    val forceShowUnlockPrompt by viewModel.forceShowUnlockPrompt.collectAsStateWithLifecycle()
    val isAppLockedGlobal = viewModel.isConfigurationLocked()
    val isAccessDisabled = !isAccessActive
    val showSecurityOverride = isAppLockedGlobal && (isAccessDisabled || forceShowUnlockPrompt)

    if (showSecurityOverride) {
        SecurityOverridePromptOverlay(
            viewModel = viewModel,
            isAccessDisabled = isAccessDisabled,
            onSuccess = {
                viewModel.forceShowUnlockPrompt.value = false
            }
        )
    } else {
        Scaffold(
            bottomBar = {
                NavigationBar(
                    containerColor = SlateCharcoal,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = selectedTab == 0,
                        onClick = { viewModel.selectedTab.value = 0 },
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricTeal,
                            selectedTextColor = ElectricTeal,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor = AccentPill
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 1,
                        onClick = { viewModel.selectedTab.value = 1 },
                        icon = { Icon(Icons.Default.Block, contentDescription = "Block Rules") },
                        label = { Text("Block Rules") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricTeal,
                            selectedTextColor = ElectricTeal,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor = AccentPill
                        )
                    )
                    NavigationBarItem(
                        selected = selectedTab == 2,
                        onClick = { viewModel.selectedTab.value = 2 },
                        icon = { 
                            Icon(
                                if (viewModel.isConfigurationLocked()) Icons.Default.Lock else Icons.Default.LockOpen,
                                contentDescription = "Security Lock"
                            ) 
                        },
                        label = { Text("Security Lock") },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = ElectricTeal,
                            selectedTextColor = ElectricTeal,
                            unselectedIconColor = TextGray,
                            unselectedTextColor = TextGray,
                            indicatorColor = AccentPill
                        )
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize()
                    .background(CarbonBlack)
            ) {
                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "TabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        0 -> DashboardScreen(
                            viewModel = viewModel,
                            isAccessActive = isAccessActive,
                            appConfigs = appConfigs
                        )
                        1 -> RulesScreen(
                            viewModel = viewModel,
                            appConfigs = appConfigs
                        )
                        2 -> LockScreen(
                            viewModel = viewModel,
                            securitySettings = securitySettings
                        )
                    }
                }

                // Render absolute full-screen block warning overlay if triggered
                blockedNotification?.let { (packageName, appName) ->
                    BlockedWarningOverlay(
                        appName = appName,
                        packageName = packageName,
                        onDismiss = { viewModel.blockedAppNotification.value = null }
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardScreen(
    viewModel: BlockViewModel,
    isAccessActive: Boolean,
    appConfigs: List<AppBlockConfig>
) {
    val context = LocalContext.current
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            // Header
            Text(
                text = "SCANNERS & STATUS",
                letterSpacing = 2.sp,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricTeal,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "ZenScroll",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = ThemePrimaryText
            )
        }

        // Service Active/Inactive Banner
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAccessActive) SlateCharcoal else Color(0x33FF4156)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(
                        1.dp,
                        if (isAccessActive) ElectricTeal.copy(0.3f) else DangerCoral.copy(0.4f),
                        RoundedCornerShape(16.dp)
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isAccessActive) ElectricTeal.copy(0.15f) else DangerCoral.copy(
                                        0.15f
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isAccessActive) Icons.Default.Shield else Icons.Default.Warning,
                                contentDescription = "Status",
                                tint = if (isAccessActive) ElectricTeal else DangerCoral,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = if (isAccessActive) "Surgical Shield Active" else "Shield Offline",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = ThemePrimaryText
                            )
                            Text(
                                text = if (isAccessActive) "Accessibility monitor running safely" else "Accessibility permission is required",
                                fontSize = 12.sp,
                                color = TextGray
                            )
                        }
                    }

                    if (!isAccessActive) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "How to enable:\n1. Tap the button below\n2. Locate 'Scroll Blocker' inside Installed Apps/Services\n3. Turn on the permission toggles",
                            fontSize = 13.sp,
                            color = ThemePrimaryText.copy(0.85f),
                            lineHeight = 18.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                try {
                                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = DangerCoral),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ENABLE BLOCKER SERVICE", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Live stats header
        item {
            Text(
                text = "TODAY'S USAGES & BUDGETS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricTeal,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        if (appConfigs.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ElectricTeal)
                }
            }
        } else {
            items(appConfigs, key = { it.packageName }) { config ->
                AppUsageCard(viewModel = viewModel, config = config)
            }
        }
    }
}

@Composable
fun AppUsageCard(viewModel: BlockViewModel, config: AppBlockConfig) {
    val usedSeconds = config.timeUsedTodaySeconds
    val limitMins = config.dailyLimitMinutes
    val isLocked = viewModel.isConfigurationLocked()
    
    val progress = if (limitMins > 0) {
        (usedSeconds.toFloat() / (limitMins * 60f)).coerceIn(0f, 1f)
    } else {
        0f
    }
    
    val remainsSec = if (limitMins > 0) {
        (limitMins * 60L - usedSeconds).coerceAtLeast(0L)
    } else {
        0L
    }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (progress >= 1f && limitMins > 0) DangerCoral.copy(0.3f) else ThemePrimaryText.copy(0.12f),
                RoundedCornerShape(12.dp)
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(SlateGrayLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = config.appName.first().toString(),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = ElectricTeal
                        )
                    }
                    Column {
                        Text(
                            text = config.appName,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = ThemePrimaryText
                        )
                        Text(
                            text = if (config.blockShortsReels) "Surgically blocking Reels/Shorts" else "No surgical block layer",
                            fontSize = 11.sp,
                            color = if (config.blockShortsReels) ElectricTeal else TextGray
                        )
                    }
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatUsageTime(usedSeconds),
                        fontWeight = FontWeight.Bold,
                        color = ThemePrimaryText,
                        fontSize = 14.sp
                    )
                    Text(
                        text = if (limitMins > 0) "Budget: ${limitMins}m" else "No daily budget",
                        fontSize = 11.sp,
                        color = TextGray
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = if (progress >= 1f && limitMins > 0) DangerCoral else ElectricTeal,
                trackColor = SlateGrayLight,
            )

            if (limitMins > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (progress >= 1f) "Overbudget! App blocks activated." else "Remaining: ${formatUsageTime(remainsSec)}",
                        fontSize = 11.sp,
                        color = if (progress >= 1f) DangerCoral else BrightLime
                    )
                    
                    // Emulation Debugging button (only if not locked)
                    if (!isLocked) {
                        Text(
                            text = "+5m Sim",
                            fontSize = 10.sp,
                            color = ElectricTeal,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentPill)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable {
                                    viewModel.forceResetUsageForTesting(config.copy(timeUsedTodaySeconds = usedSeconds + 300))
                                }
                        )
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "App is allowed unlimited browsing", fontSize = 11.sp, color = TextGray)
                    if (!isLocked) {
                        Text(
                            text = "+5m Sim",
                            fontSize = 10.sp,
                            color = ElectricTeal,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(AccentPill)
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                .clickable {
                                    viewModel.forceResetUsageForTesting(config.copy(timeUsedTodaySeconds = usedSeconds + 300))
                                }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun RulesScreen(viewModel: BlockViewModel, appConfigs: List<AppBlockConfig>) {
    val isLocked = viewModel.isConfigurationLocked()
    
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SURGICAL RULES",
                letterSpacing = 2.sp,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricTeal,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Distraction Blockers",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = ThemePrimaryText
                )
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "🔒 Settings time-locked",
                        tint = DangerCoral,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            if (isLocked) {
                Text(
                    text = "Configurations are locked by the security timer. Unlocking is currently restricted.",
                    fontSize = 12.sp,
                    color = DangerCoral,
                    fontWeight = FontWeight.Medium
                )
            } else {
                Text(
                    text = "Adjust which scrolling facilities to target and set daily time allowances.",
                    fontSize = 12.sp,
                    color = TextGray
                )
            }
        }

        items(appConfigs, key = { it.packageName }) { config ->
            RuleCard(viewModel = viewModel, config = config, isLocked = isLocked)
        }
    }
}

@Composable
fun RuleCard(viewModel: BlockViewModel, config: AppBlockConfig, isLocked: Boolean) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isLocked) DangerCoral.copy(0.2f) else ThemePrimaryText.copy(0.12f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .alpha(if (isLocked) 0.62f else 1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = when (config.packageName) {
                          "com.google.android.youtube" -> Icons.Default.PlayArrow
                          "com.instagram.android" -> Icons.Default.Videocam
                          "com.twitter.android" -> Icons.Default.Search
                          else -> Icons.Default.Share
                        },
                        contentDescription = config.appName,
                        tint = ElectricTeal,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = config.appName,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = ThemePrimaryText
                    )
                }
                
                if (isLocked) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = "Locked",
                        tint = TextGray,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    Text(
                        text = "Reset Timer",
                        fontSize = 11.sp,
                        color = ElectricTeal,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable {
                                viewModel.forceResetUsageForTesting(config.copy(timeUsedTodaySeconds = 0))
                            }
                            .border(1.dp, ElectricTeal, RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Divider(color = SlateGrayLight, modifier = Modifier.padding(vertical = 12.dp))

            // 1. Surgical Blocks
            val featureLabel = when (config.packageName) {
                "com.google.android.youtube" -> "Block YouTube Shorts"
                "com.instagram.android" -> "Block Instagram Reels"
                "com.twitter.android" -> "Block X Explore & Trends"
                "com.facebook.katana" -> "Block Facebook Reels"
                else -> "Block Scrolling Tab"
            }
            
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = featureLabel, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ThemePrimaryText)
                    Text(
                        text = "Surgically exits the feed while letting you enjoy other features",
                        fontSize = 10.sp,
                        color = TextGray
                    )
                }
                Switch(
                    checked = config.blockShortsReels,
                    onCheckedChange = { viewModel.updateSurgicalRule(config, it) },
                    enabled = !isLocked,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = CarbonBlack,
                        checkedTrackColor = ElectricTeal,
                        uncheckedThumbColor = TextGray,
                        uncheckedTrackColor = SlateGrayLight
                    )
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. Budget slider
            Column {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = "Daily App Budget", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = ThemePrimaryText)
                    Text(
                        text = if (config.dailyLimitMinutes > 0) "${config.dailyLimitMinutes} minutes" else "No budget limit",
                        fontWeight = FontWeight.ExtraBold,
                        color = if (config.dailyLimitMinutes > 0) ElectricTeal else BrightLime,
                        fontSize = 14.sp
                    )
                }
                Slider(
                    value = config.dailyLimitMinutes.toFloat(),
                    onValueChange = { viewModel.updateDailyLimit(config, it.toInt()) },
                    valueRange = 0f..1440f,
                    steps = 143, // 0 to 1440 in steps of 10
                    enabled = !isLocked,
                    colors = SliderDefaults.colors(
                        thumbColor = ElectricTeal,
                        activeTrackColor = ElectricTeal,
                        inactiveTrackColor = SlateGrayLight
                    )
                )
                Text(
                    text = "If exceeded, wholesale access to the app is suspended for the day.",
                    fontSize = 10.sp,
                    color = TextGray
                )
            }
        }
    }
}

@Composable
fun LockScreen(viewModel: BlockViewModel, securitySettings: SecuritySettings?) {
    val context = LocalContext.current
    var passwordInput by remember { mutableStateOf("") }
    var confirmPasswordInput by remember { mutableStateOf("") }
    var durationDaysSelection by remember { mutableIntStateOf(7) } // Default 7 days
    var isEmergencyResetChecked by remember { mutableStateOf(false) }

    val isLocked = viewModel.isConfigurationLocked()
    var countdownText by remember { mutableStateOf("") }

    var isAdminActive by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        while (true) {
            isAdminActive = viewModel.isDeviceAdminActive(context)
            delay(1000)
        }
    }

    // Tick the countdown
    LaunchedEffect(key1 = isLocked, key2 = securitySettings) {
        if (isLocked) {
            while (true) {
                val ms = viewModel.getLockRemainingTimeMs()
                if (ms <= 0) {
                    countdownText = "Unlocked"
                    break
                }
                val sec = (ms / 1000) % 60
                val min = (ms / (1000 * 60)) % 60
                val hrs = (ms / (1000 * 60 * 60)) % 24
                val days = ms / (1000 * 60 * 60 * 24)
                countdownText = "${days}d ${hrs}h ${min}m ${sec}s"
                delay(1000)
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "SECURITY VAULT",
                letterSpacing = 2.sp,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = ElectricTeal,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Time-Locked Security",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = ThemePrimaryText
            )
            Text(
                text = "Secure your boundaries. Lock the configuration dials to prevent edits during vulnerable hours of scrolling.",
                fontSize = 12.sp,
                color = TextGray
            )
        }

        item {
            DeviceAdminCard(viewModel = viewModel, isAdminActive = isAdminActive, isLocked = isLocked)
        }

        if (securitySettings == null) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ElectricTeal)
                }
            }
        } else if (isLocked) {
            // Already locked state
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCharcoal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, DangerCoral.copy(0.3f), RoundedCornerShape(16.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .padding(24.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .clip(CircleShape)
                                .background(DangerCoral.copy(0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "Locked settings",
                                tint = DangerCoral,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "DIALS ARE TIME-LOCKED",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = DangerCoral,
                            fontFamily = FontFamily.Monospace
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = countdownText,
                            fontSize = 32.sp,
                            fontWeight = FontWeight.Black,
                            color = ThemePrimaryText,
                            textAlign = TextAlign.Center
                        )

                        Text(
                            text = "Remaining lock duration. Settings edits are greyed out.",
                            fontSize = 12.sp,
                            color = TextGray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(top = 4.dp)
                        )

                        Divider(color = SlateGrayLight, modifier = Modifier.padding(vertical = 20.dp))

                        Text(
                            text = "Need an emergency unlock?",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ThemePrimaryText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Enter Passcode") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SlateGrayLight,
                                focusedLabelColor = ElectricTeal,
                                focusedTextColor = ThemePrimaryText,
                                unfocusedTextColor = ThemePrimaryText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = {
                                if (viewModel.tryToUnlockSettings(passwordInput)) {
                                    passwordInput = ""
                                    Toast.makeText(context, "Lock unlocked successfully!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Incorrect passcode", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("UNLOCK IMMEDIATELY", color = CarbonBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            // Unlock setup state
            item {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = SlateCharcoal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, ElectricTeal.copy(0.15f), RoundedCornerShape(16.dp))
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Settings open",
                                tint = ElectricTeal
                            )
                            Text(
                                text = "Setup Settings Lock",
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 18.sp,
                                color = ThemePrimaryText
                            )
                        }
                        
                        Text(
                            text = "Prevent yourself from modifying limits when temptation strikes. Lock settings with a strict passcode.",
                            fontSize = 12.sp,
                            color = TextGray,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )

                        OutlinedTextField(
                            value = passwordInput,
                            onValueChange = { passwordInput = it },
                            label = { Text("Setup Passcode") },
                            placeholder = { Text("E.g. strictpassword") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SlateGrayLight,
                                focusedLabelColor = ElectricTeal,
                                focusedTextColor = ThemePrimaryText,
                                unfocusedTextColor = ThemePrimaryText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = confirmPasswordInput,
                            onValueChange = { confirmPasswordInput = it },
                            label = { Text("Confirm Passcode") },
                            placeholder = { Text("Re-enter passcode") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = ElectricTeal,
                                unfocusedBorderColor = SlateGrayLight,
                                focusedLabelColor = ElectricTeal,
                                focusedTextColor = ThemePrimaryText,
                                unfocusedTextColor = ThemePrimaryText
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // Duration selection
                        Text(
                            text = "Lock Duration",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = ThemePrimaryText
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val durations = listOf(
                            Pair("1 Day", 1),
                            Pair("3 Days", 3),
                            Pair("1 Week", 7),
                            Pair("1 Month", 30),
                            Pair("2 Months", 60),
                            Pair("3 Months", 90)
                        )
                        
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            durations.chunked(3).forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    rowItems.forEach { (label, days) ->
                                        val selected = durationDaysSelection == days
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(if (selected) ElectricTeal else SlateGrayLight)
                                                .clickable { durationDaysSelection = days }
                                                .padding(vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = label,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp,
                                                color = if (selected) CarbonBlack else ThemePrimaryText
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                if (passwordInput.isBlank()) {
                                    Toast.makeText(context, "Please enter a passcode", Toast.LENGTH_SHORT).show()
                                } else if (passwordInput != confirmPasswordInput) {
                                    Toast.makeText(context, "Passcodes do not match", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.setLockSettings(passwordInput, durationDaysSelection)
                                    passwordInput = ""
                                    confirmPasswordInput = ""
                                    Toast.makeText(context, "Dials are now safely locked!", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ACTIVATE SECURITY LOCK", color = CarbonBlack, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlockedWarningOverlay(
    appName: String,
    packageName: String,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.96f))
            .clickable(enabled = false) {}, // Prevent back clicks from slipping away
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DangerCoral.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Block,
                    contentDescription = "Access Denied",
                    tint = DangerCoral,
                    modifier = Modifier.size(48.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "$appName IS SUSPENDED",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                color = DangerCoral,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Temptation Defeated",
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You set a daily limit for $appName, and you have reached it. Real discipline means honoring the borders you draw for yourself.",
                fontSize = 15.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = SlateGrayLight),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("DISMISS AND FOCUS", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

fun formatUsageTime(seconds: Long): String {
    val hrs = seconds / 3600
    val mins = (seconds % 3600) / 60
    val secs = seconds % 60
    return when {
        hrs > 0 -> "${hrs}h ${mins}m"
        mins > 0 -> "${mins}m ${secs}s"
        else -> "${secs}s"
    }
}

@Composable
fun DeviceAdminCard(viewModel: BlockViewModel, isAdminActive: Boolean, isLocked: Boolean) {
    val context = LocalContext.current
    
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SlateCharcoal),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isAdminActive) ElectricTeal.copy(0.3f) else DangerCoral.copy(0.3f),
                RoundedCornerShape(16.dp)
            )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (isAdminActive) Icons.Default.Shield else Icons.Default.Warning,
                    contentDescription = "Device Admin Status",
                    tint = if (isAdminActive) ElectricTeal else DangerCoral
                )
                Text(
                    text = "Uninstall Protection",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    color = ThemePrimaryText
                )
            }
            
            Text(
                text = if (isAdminActive) {
                    "Device Administrator is active. ZenScroll cannot be uninstalled without disabling this first."
                } else {
                    "Device Administrator is inactive. Under Android, enabling Device Admin is required to block uninstallation of ZenScroll."
                },
                fontSize = 12.sp,
                color = TextGray,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            if (isAdminActive) {
                if (isLocked) {
                    Text(
                        text = "Protection Locked 🔒\nUnlock settings first using the passcode below to deactivate Device Admin.",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = DangerCoral
                    )
                } else {
                    Button(
                        onClick = {
                            try {
                                val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                                val comp = android.content.ComponentName(context, com.example.AdminReceiver::class.java)
                                dpm.removeActiveAdmin(comp)
                                Toast.makeText(context, "Uninstall Protection disabled", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = DangerCoral),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("DEACTIVATE UNINSTALL PROTECTION", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Button(
                    onClick = {
                        viewModel.requestDeviceAdmin(context)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("ACTIVATE UNINSTALL PROTECTION", color = CarbonBlack, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SecurityOverridePromptOverlay(
    viewModel: BlockViewModel,
    isAccessDisabled: Boolean,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    var passcodeEntered by remember { mutableStateOf("") }
    
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.97f))
            .clickable(enabled = false) {}, // Intercept back clicks
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .padding(32.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(DangerCoral.copy(0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Configuration Locked",
                    tint = DangerCoral,
                    modifier = Modifier.size(48.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "SECURITY LOCK ENGAGED",
                fontSize = 12.sp,
                letterSpacing = 2.sp,
                fontWeight = FontWeight.Bold,
                color = DangerCoral,
                fontFamily = FontFamily.Monospace
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Text(
                text = if (isAccessDisabled) "Re-enable Service Guard" else "Settings Protection",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = Color.White,
                textAlign = TextAlign.Center
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = if (isAccessDisabled) {
                    "ZenScroll's accessibility blocker service was turned off. Under your safety rules, you must enter your passcode to re-enable it and maintain your borders."
                } else {
                    "Passcode verification required to proceed after a settings intercept or restricted action."
                },
                fontSize = 14.sp,
                color = TextGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            OutlinedTextField(
                value = passcodeEntered,
                onValueChange = { passcodeEntered = it },
                label = { Text("Enter Passcode") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = ElectricTeal,
                    unfocusedBorderColor = SlateGrayLight,
                    focusedLabelColor = ElectricTeal,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Button(
                onClick = {
                    if (viewModel.tryToUnlockSettings(passcodeEntered)) {
                        passcodeEntered = ""
                        Toast.makeText(context, "Verification successful!", Toast.LENGTH_SHORT).show()
                        onSuccess()
                        if (isAccessDisabled) {
                            try {
                                context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Could not open settings", Toast.LENGTH_SHORT).show()
                            }
                        }
                    } else {
                        Toast.makeText(context, "Incorrect passcode", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = ElectricTeal),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = if (isAccessDisabled) "UNLOCK AND RESTORE SERVICE" else "UNLOCK CONFIGURATION",
                    color = CarbonBlack,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
