package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.util.Log
import com.example.data.AppBlockConfig
import com.example.data.BlockDatabase
import com.example.data.BlockRepository
import com.example.MainActivity
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

class ScreenMonitorService : AccessibilityService() {

    private val TAG = "ScreenMonitorService"
    
    private lateinit var repository: BlockRepository
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private var currentForegroundPackage = ""
    private var lastForegroundUpdateTime = 0L
    private val appConfigs = ConcurrentHashMap<String, AppBlockConfig>()
    
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.microsoft.empath",
        "com.duckduckgo.mobile.android",
        "com.android.browser",
        "org.chromium.chrome"
    )
    
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenMonitorService created")
        val db = BlockDatabase.getDatabase(this)
        repository = BlockRepository(db.dao())
        
        serviceScope.launch {
            repository.initializeDefaultConfigsIfNeeded()
            repository.allConfigs.collect { configs ->
                Log.d(TAG, "Loaded ${configs.size} configurations")
                appConfigs.clear()
                for (config in configs) {
                    appConfigs[config.packageName] = config
                }
            }
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        var packageName = event.packageName?.toString() ?: return
        
        // Map Lite/Alternative versions of apps to leverage their parent app configurations
        packageName = when (packageName) {
            "com.facebook.lite" -> "com.facebook.katana"
            "com.instagram.lite" -> "com.instagram.android"
            "com.twitter.lite", "com.twitter.android.lite" -> "com.twitter.android"
            else -> packageName
        }
        
        if (BROWSER_PACKAGES.contains(packageName)) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                checkAndBlockBrowser(rootNode)
                rootNode.recycle()
            }
            updateTrackingStats(null)
            return
        }
        
        if (!appConfigs.containsKey(packageName)) {
            updateTrackingStats(null)
            return
        }
        
        updateTrackingStats(packageName)
        
        val config = appConfigs[packageName] ?: return
        
        val limitSeconds = config.dailyLimitMinutes * 60L
        if (config.dailyLimitMinutes > 0 && config.timeUsedTodaySeconds >= limitSeconds) {
            triggerFullBlock(config)
            return
        }
        
        if (config.blockShortsReels) {
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                val state = AppScreenState()
                analyzeScreenState(rootNode, packageName, state)
                
                Log.d(TAG, "Screen stats for $packageName -> safeTabSelected: ${state.isSafeTabActive}, reelsTabSelected: ${state.isShortsOrReelsTabActive}, activePlayer: ${state.hasActivePlayerView}")
                
                val shouldBlock = if (state.isSafeTabActive) {
                    false
                } else {
                    state.isShortsOrReelsTabActive || state.hasActivePlayerView
                }
                
                if (shouldBlock) {
                    Log.d(TAG, "Surgical block triggered for $packageName!")
                    performGlobalAction(GLOBAL_ACTION_BACK)
                }
                rootNode.recycle()
            }
        }
    }

    private fun updateTrackingStats(newPackage: String?) {
        val now = System.currentTimeMillis()
        if (currentForegroundPackage.isNotEmpty()) {
            val elapsedMs = now - lastForegroundUpdateTime
            if (elapsedMs >= 1000) {
                val elapsedSeconds = elapsedMs / 1000
                val packageToUpdate = currentForegroundPackage
                serviceScope.launch(Dispatchers.IO) {
                    repository.incrementAppUsage(packageToUpdate, elapsedSeconds)
                }
                lastForegroundUpdateTime = now
            }
        }
        
        if (newPackage != null) {
            if (currentForegroundPackage != newPackage) {
                currentForegroundPackage = newPackage
                lastForegroundUpdateTime = now
                serviceScope.launch(Dispatchers.IO) {
                    repository.checkAndResetAppUsage(newPackage)
                }
            }
        } else {
            currentForegroundPackage = ""
            lastForegroundUpdateTime = 0L
        }
    }

    private fun triggerFullBlock(config: AppBlockConfig) {
        Log.d(TAG, "Full block triggered for ${config.packageName}")
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BLOCKED_APP_PACKAGE", config.packageName)
            putExtra("BLOCKED_APP_NAME", config.appName)
        }
        startActivity(intent)
    }

    private class AppScreenState {
        var isSafeTabActive = false
        var isShortsOrReelsTabActive = false
        var hasActivePlayerView = false
    }

    private fun isNodeOrRelativeSelected(node: AccessibilityNodeInfo): Boolean {
        if (node.isSelected) return true
        var currentParent = node.parent
        var depth = 0
        while (currentParent != null && depth < 3) {
            if (currentParent.isSelected) {
                currentParent.recycle()
                return true
            }
            val next = currentParent.parent
            currentParent.recycle()
            currentParent = next
            depth++
        }
        return false
    }

    private fun isYouTubeShortsTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        return text == "shorts" || desc == "shorts" || text == "shorts tab" || desc == "shorts tab" || text == "youtube shorts" || desc == "youtube shorts"
    }

    private fun isYouTubeSafeTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text == "home" || desc == "home" || text == "home tab" || desc == "home tab") return true
        if (text == "subscriptions" || desc == "subscriptions" || text == "subscriptions tab" || desc == "subscriptions tab") return true
        if (text == "library" || desc == "library" || text == "library tab" || desc == "library tab") return true
        if (text == "you" || desc == "you" || text == "you tab" || desc == "you tab") return true
        if (text == "create" || desc == "create" || text == "create tab" || desc == "create tab" || text == "add" || desc == "add") return true
        if (text == "inbox" || desc == "inbox" || text == "notifications" || desc == "notifications") return true
        return false
    }

    private fun isInstagramReelsTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        return text == "reels" || desc == "reels" || text == "reels tab" || desc == "reels tab" || text == "instagram reels" || desc == "instagram reels" || text == "clips" || desc == "clips"
    }

    private fun isInstagramSafeTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text == "home" || desc == "home" || text == "home tab" || desc == "home tab") return true
        if (text == "search" || desc == "search" || text == "search tab" || desc == "search tab" || text == "search and explore" || desc == "search and explore") return true
        if (text == "explore" || desc == "explore") return true
        if (text == "create" || desc == "create" || text == "new post" || desc == "new post") return true
        if (text == "notifications" || desc == "notifications" || text == "activity" || desc == "activity") return true
        if (text == "profile" || desc == "profile" || text == "profile tab" || desc == "profile tab") return true
        if (text == "messages" || desc == "messages" || text == "direct" || desc == "direct" || text == "threads" || desc == "threads") return true
        return false
    }

    private fun isFacebookReelsTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        return text == "reels" || desc == "reels" || text == "reels tab" || desc == "reels tab" || text == "facebook reels" || desc == "facebook reels"
    }

    private fun isFacebookSafeTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text == "home" || desc == "home" || text == "news feed" || desc == "news feed") return true
        if (text == "watch" || desc == "watch") return true
        if (text == "groups" || desc == "groups" || text == "groups tab" || desc == "groups tab") return true
        if (text == "marketplace" || desc == "marketplace" || text == "shop" || desc == "shop") return true
        if (text == "profile" || desc == "profile") return true
        if (text == "notifications" || desc == "notifications") return true
        if (text == "menu" || desc == "menu") return true
        return false
    }

    private fun isTwitterExploreTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        return text == "explore" || desc == "explore" || text == "search and explore" || desc == "search and explore"
    }

    private fun isTwitterSafeTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text == "home" || desc == "home") return true
        if (text == "notifications" || desc == "notifications") return true
        if (text == "messages" || desc == "messages" || text == "direct messages" || desc == "direct messages") return true
        if (text == "communities" || desc == "communities") return true
        if (text == "grok" || desc == "grok") return true
        return false
    }

    private fun analyzeScreenState(node: AccessibilityNodeInfo?, packageName: String, state: AppScreenState) {
        if (node == null) return
        
        val viewId = node.viewIdResourceName ?: ""
        val lowerViewId = viewId.lowercase()
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        val isSelected = isNodeOrRelativeSelected(node)
        
        when (packageName) {
            "com.google.android.youtube" -> {
                if (isYouTubeSafeTab(node) && isSelected) {
                    state.isSafeTabActive = true
                }
                if (isYouTubeShortsTab(node) && isSelected) {
                    state.isShortsOrReelsTabActive = true
                }
                val isShortsPlayer = lowerViewId.contains("shorts_video") || 
                                     lowerViewId.contains("shorts_player") || 
                                     lowerViewId.contains("reel_container") ||
                                     lowerViewId.contains("shorts_view")
                if (isShortsPlayer) {
                    val isFeedOrShelf = lowerViewId.contains("grid") || 
                                        lowerViewId.contains("shelf") || 
                                        lowerViewId.contains("thumbnail") ||
                                        lowerViewId.contains("preview") ||
                                        lowerViewId.contains("carousel") ||
                                        lowerViewId.contains("card") ||
                                        lowerViewId.contains("row") ||
                                        lowerViewId.contains("tab")
                    if (!isFeedOrShelf) {
                        state.hasActivePlayerView = true
                    }
                }
                if (contentDesc.contains("shorts video player") || 
                    contentDesc.contains("youtube shorts player") || 
                    contentDesc.contains("youtube shorts") ||
                    contentDesc.equals("shorts", ignoreCase = true)
                ) {
                    if (!lowerViewId.contains("tab") && 
                        !lowerViewId.contains("button") && 
                        !lowerViewId.contains("thumbnail") && 
                        !lowerViewId.contains("shelf")
                    ) {
                        state.hasActivePlayerView = true
                    }
                }
            }
            "com.instagram.android" -> {
                if (isInstagramSafeTab(node) && isSelected) {
                    state.isSafeTabActive = true
                }
                if (isInstagramReelsTab(node) && isSelected) {
                    state.isShortsOrReelsTabActive = true
                }
                val isReelsPlayer = lowerViewId.contains("clips_video") || 
                                    lowerViewId.contains("reel_viewer") || 
                                    lowerViewId.contains("clips_viewer") ||
                                    lowerViewId.contains("clips_video_container")
                if (isReelsPlayer) {
                    val isFeedOrGrid = lowerViewId.contains("grid") || 
                                       lowerViewId.contains("carousel") || 
                                       lowerViewId.contains("thumbnail") || 
                                       lowerViewId.contains("feed") ||
                                       lowerViewId.contains("preview") ||
                                       lowerViewId.contains("card") ||
                                       lowerViewId.contains("row") ||
                                       lowerViewId.contains("tab")
                    if (!isFeedOrGrid) {
                        state.hasActivePlayerView = true
                    }
                }
                if (contentDesc.contains("clips video") || 
                    contentDesc.contains("reels viewer") ||
                    contentDesc.contains("instagram reels") ||
                    contentDesc.equals("reels", ignoreCase = true)
                ) {
                    if (!lowerViewId.contains("tab") && 
                        !lowerViewId.contains("button") && 
                        !lowerViewId.contains("thumbnail")
                    ) {
                        state.hasActivePlayerView = true
                    }
                }
            }
            "com.facebook.katana" -> {
                if (isFacebookSafeTab(node) && isSelected) {
                    state.isSafeTabActive = true
                }
                if (isFacebookReelsTab(node) && isSelected) {
                    state.isShortsOrReelsTabActive = true
                }
                val isReelsPlayer = lowerViewId.contains("reels_viewer") || 
                                    lowerViewId.contains("reels_video") || 
                                    lowerViewId.contains("reels_tab_container")
                if (isReelsPlayer) {
                    val isFeedOrGrid = lowerViewId.contains("grid") || 
                                       lowerViewId.contains("shelf") || 
                                       lowerViewId.contains("thumbnail") ||
                                       lowerViewId.contains("feed") ||
                                       lowerViewId.contains("preview") ||
                                       lowerViewId.contains("card") ||
                                       lowerViewId.contains("row") ||
                                       lowerViewId.contains("tab")
                    if (!isFeedOrGrid) {
                        state.hasActivePlayerView = true
                    }
                }
                if (contentDesc.contains("reels viewer") || 
                    contentDesc.contains("facebook reels") ||
                    contentDesc.equals("reels", ignoreCase = true)
                ) {
                    if (!lowerViewId.contains("tab") && 
                        !lowerViewId.contains("button") && 
                        !lowerViewId.contains("thumbnail")
                    ) {
                        state.hasActivePlayerView = true
                    }
                }
            }
            "com.twitter.android" -> {
                if (isTwitterSafeTab(node) && isSelected) {
                    state.isSafeTabActive = true
                }
                if (isTwitterExploreTab(node) && isSelected) {
                    state.isShortsOrReelsTabActive = true
                }
                if (lowerViewId.contains("trends_view") || 
                    lowerViewId.contains("explore_tab_container") ||
                    lowerViewId.contains("search_timeline")
                ) {
                    if (!lowerViewId.contains("row") && 
                        !lowerViewId.contains("button") && 
                        !lowerViewId.contains("tab")
                    ) {
                        state.hasActivePlayerView = true
                    }
                }
                if (text == "search and explore" || text == "trends") {
                    state.hasActivePlayerView = true
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                analyzeScreenState(child, packageName, state)
                child.recycle()
            }
        }
    }

    private fun isUrlBarNode(node: AccessibilityNodeInfo): Boolean {
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.isNotEmpty()) {
            if (viewId.contains("url") || 
                viewId.contains("address") || 
                viewId.contains("location") || 
                viewId.contains("omnibar") ||
                viewId.contains("searchbar") ||
                viewId.contains("search_box") ||
                viewId.contains("search_src") ||
                viewId.contains("search_edit")
            ) {
                return true
            }
        }
        
        val text = node.text?.toString()?.lowercase() ?: ""
        if (text.isNotEmpty() && (node.className?.contains("EditText") == true || node.className?.contains("TextView") == true)) {
            if (text.startsWith("http://") || text.startsWith("https://") || text.startsWith("www.")) {
                return true
            }
            val domains = listOf("youtube.com", "instagram.com", "facebook.com", "twitter.com", "x.com")
            if (domains.any { text == it || text.startsWith("$it/") || text.startsWith("www.$it") }) {
                return true
            }
        }
        return false
    }

    private fun checkAndBlockBrowser(node: AccessibilityNodeInfo): Boolean {
        if (isUrlBarNode(node)) {
            val text = node.text?.toString()?.lowercase() ?: ""
            val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
            
            val ytConfig = appConfigs["com.google.android.youtube"]
            val igConfig = appConfigs["com.instagram.android"]
            val twConfig = appConfigs["com.twitter.android"]
            val fbConfig = appConfigs["com.facebook.katana"]
            
            fun isUrlActive(domain: String, subPaths: List<String> = emptyList()): Boolean {
                val containsDomain = text.contains(domain) || contentDesc.contains(domain)
                if (!containsDomain) return false
                if (subPaths.isEmpty()) return true
                return subPaths.any { text.contains(it) || contentDesc.contains(it) }
            }
            
            if (ytConfig != null) {
                val isYtLimitReached = ytConfig.dailyLimitMinutes > 0 && ytConfig.timeUsedTodaySeconds >= ytConfig.dailyLimitMinutes * 60L
                if (isYtLimitReached && isUrlActive("youtube.com")) {
                    triggerWebCounterpartBlock("YouTube", "youtube.com")
                    return true
                }
                if (ytConfig.blockShortsReels && isUrlActive("youtube.com", listOf("shorts"))) {
                    triggerSurgicalWebBlock("YouTube Shorts")
                    return true
                }
            }
            
            if (igConfig != null) {
                val isIgLimitReached = igConfig.dailyLimitMinutes > 0 && igConfig.timeUsedTodaySeconds >= igConfig.dailyLimitMinutes * 60L
                if (isIgLimitReached && isUrlActive("instagram.com")) {
                    triggerWebCounterpartBlock("Instagram", "instagram.com")
                    return true
                }
                if (igConfig.blockShortsReels && isUrlActive("instagram.com", listOf("reels", "explore", "clips"))) {
                    triggerSurgicalWebBlock("Instagram Reels/Explore")
                    return true
                }
            }
            
            if (twConfig != null) {
                val isTwLimitReached = twConfig.dailyLimitMinutes > 0 && twConfig.timeUsedTodaySeconds >= twConfig.dailyLimitMinutes * 60L
                if (isTwLimitReached && (isUrlActive("twitter.com") || isUrlActive("x.com"))) {
                    triggerWebCounterpartBlock("X (Twitter)", "x.com")
                    return true
                }
                if (twConfig.blockShortsReels && (isUrlActive("twitter.com", listOf("explore", "trends")) || isUrlActive("x.com", listOf("explore", "trends")))) {
                    triggerSurgicalWebBlock("Twitter Explore")
                    return true
                }
            }
            
            if (fbConfig != null) {
                val isFbLimitReached = fbConfig.dailyLimitMinutes > 0 && fbConfig.timeUsedTodaySeconds >= fbConfig.dailyLimitMinutes * 60L
                if (isFbLimitReached && isUrlActive("facebook.com")) {
                    triggerWebCounterpartBlock("Facebook", "facebook.com")
                    return true
                }
                if (fbConfig.blockShortsReels && isUrlActive("facebook.com", listOf("reels", "watch"))) {
                    triggerSurgicalWebBlock("Facebook Reels")
                    return true
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                if (checkAndBlockBrowser(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
        }
        return false
    }

    private fun triggerWebCounterpartBlock(appName: String, domain: String) {
        Log.d(TAG, "Web counterpart block triggered for $appName ($domain) in browser")
        performGlobalAction(GLOBAL_ACTION_HOME)
        
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BLOCKED_APP_PACKAGE", "browser_bypass_$domain")
            putExtra("BLOCKED_APP_NAME", "$appName (Web)")
        }
        startActivity(intent)
    }
    
    private fun triggerSurgicalWebBlock(featureLabel: String) {
        Log.d(TAG, "Surgically blocked $featureLabel in web browser")
        performGlobalAction(GLOBAL_ACTION_BACK)
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "ScreenMonitorService destroyed")
    }
}
