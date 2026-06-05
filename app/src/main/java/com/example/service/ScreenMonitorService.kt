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
    
    private var activeForegroundPackage = ""
    private var lastAnalyzeTime = 0L
    private val appConfigs = ConcurrentHashMap<String, AppBlockConfig>()
    private var isSettingsLockedCached = false
    private var lastAppInfoBlockTime = 0L
    
    private val BROWSER_PACKAGES = setOf(
        "com.android.chrome",
        "org.mozilla.firefox",
        "com.sec.android.app.sbrowser",
        "com.opera.browser",
        "com.microsoft.emmx",
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

        serviceScope.launch {
            repository.securitySettings.collect { settings ->
                isSettingsLockedCached = settings != null && settings.isLockActive && settings.lockUntilTimestamp > System.currentTimeMillis()
                Log.d(TAG, "isSettingsLockedCached updated: $isSettingsLockedCached")
            }
        }

        // 1-second background ticker to track usage precisely and trigger limits dynamically
        serviceScope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(1000)
                val curr = activeForegroundPackage
                if (curr.isNotEmpty()) {
                    val updated = repository.incrementAppUsage(curr, 1)
                    if (updated != null) {
                        val limitSeconds = updated.dailyLimitMinutes * 60L
                        if (updated.dailyLimitMinutes > 0 && updated.timeUsedTodaySeconds >= limitSeconds) {
                            withContext(Dispatchers.Main) {
                                triggerFullBlock(updated)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onServiceConnected() {
        Log.d(TAG, "Accessibility service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        
        val eventPackage = event.packageName?.toString() ?: ""
        
        // Find the root node of the active window with fallbacks
        var rootNode = rootInActiveWindow
        if (rootNode == null) {
            val source = event.source
            if (source != null) {
                var current = source
                var depth = 0
                // Climb parent hierarchy to gather the root of the event's window
                while (depth < 20) {
                    val parent = current?.parent
                    if (parent == null) {
                        break
                    }
                    if (current != source) {
                        current?.recycle() // Recycle intermediate node to avoid leak
                    }
                    current = parent
                    depth++
                }
                rootNode = current
            }
        }
        
        val rootPackageName = rootNode?.packageName?.toString() ?: ""
        val targetPackage = if (rootPackageName.isNotEmpty()) rootPackageName else eventPackage
        val lowerTargetPkg = targetPackage.lowercase()
        
        val myPackage = packageName.lowercase()
        // Extremely broad matching for settings, security managers, device administrators,
        // and installers across varying Android ROMs, ensuring we exclude our own app.
        val isSettingsOrStore = (lowerTargetPkg != myPackage && !lowerTargetPkg.startsWith(myPackage)) && (
                                lowerTargetPkg == "android" ||
                                lowerTargetPkg.contains("settings") ||
                                lowerTargetPkg.contains("securitycenter") ||
                                lowerTargetPkg.contains("systemmanager") ||
                                lowerTargetPkg.contains("safecenter") ||
                                lowerTargetPkg.contains("vending") ||
                                lowerTargetPkg.contains("packageinstaller") ||
                                lowerTargetPkg.contains("permissioncontroller") ||
                                lowerTargetPkg.contains("security") ||
                                lowerTargetPkg.contains("admin") ||
                                lowerTargetPkg.contains("devicepolicy") ||
                                lowerTargetPkg.contains("control") ||
                                lowerTargetPkg.contains("installer") ||
                                lowerTargetPkg.contains("controller")
                                )
                                
        if (isSettingsOrStore) {
            if (rootNode != null) {
                scanAndBlockAppInfo(rootNode)
                rootNode.recycle()
            }
            return
        } else {
            rootNode?.recycle()
        }
        
        if (eventPackage.isNotEmpty()) {
            val mapped = when (eventPackage) {
                "com.facebook.lite" -> "com.facebook.katana"
                "com.instagram.lite" -> "com.instagram.android"
                "com.twitter.lite", "com.twitter.android.lite" -> "com.twitter.android"
                else -> eventPackage
            }
            val trackingPkg = if (appConfigs.containsKey(mapped) && !BROWSER_PACKAGES.contains(mapped)) mapped else ""
            if (activeForegroundPackage != trackingPkg) {
                activeForegroundPackage = trackingPkg
                if (trackingPkg.isNotEmpty()) {
                    serviceScope.launch(Dispatchers.IO) {
                        repository.checkAndResetAppUsage(trackingPkg)
                    }
                }
            }
        }
        
        var packageName = eventPackage
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
            return
        }
        
        if (!appConfigs.containsKey(packageName)) {
            return
        }
        
        val config = appConfigs[packageName] ?: return
        
        val limitSeconds = config.dailyLimitMinutes * 60L
        if (config.dailyLimitMinutes > 0 && config.timeUsedTodaySeconds >= limitSeconds) {
            triggerFullBlock(config)
            return
        }
        
        if (config.blockShortsReels) {
            val nowTime = System.currentTimeMillis()
            if (nowTime - lastAnalyzeTime >= 500L) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    lastAnalyzeTime = nowTime
                    val state = AppScreenState()
                    val originalPackageName = event.packageName?.toString() ?: packageName
                    analyzeScreenState(rootNode, originalPackageName, state)
                    
                    Log.d(TAG, "Screen stats for $originalPackageName -> safeTabSelected: ${state.isSafeTabActive}, reelsTabSelected: ${state.isShortsOrReelsTabActive}, activePlayer: ${state.hasActivePlayerView}")
                    
                    val shouldBlock = if (state.isSafeTabActive) {
                        false
                    } else if (state.hasActivePlayerView) {
                        true
                    } else {
                        state.isShortsOrReelsTabActive
                    }
                    
                    if (shouldBlock) {
                        Log.d(TAG, "Surgical block triggered for $originalPackageName!")
                        performGlobalAction(GLOBAL_ACTION_BACK)
                    }
                    rootNode.recycle()
                }
            }
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

    private fun isNodeSelected(node: AccessibilityNodeInfo): Boolean {
        if (node.isSelected) return true
        var p = node.parent
        var depth = 0
        while (p != null && depth < 2) {
            if (p.isSelected) {
                p.recycle()
                return true
            }
            val next = p.parent
            p.recycle()
            p = next
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
        
        val isSelected = isNodeSelected(node)
        
        when (packageName) {
            "com.google.android.youtube" -> {
                if (isYouTubeSafeTab(node) && isSelected) {
                    state.isSafeTabActive = true
                }
                if (isYouTubeShortsTab(node) && isSelected) {
                    state.isShortsOrReelsTabActive = true
                }
                
                // YouTube Home screen checks to prevent false back trigger from shorts previews
                val isYtFeedIndicator = text.contains("suggested shorts") ||
                                        text.contains("shorts shelf") ||
                                        text.contains("trending shorts") ||
                                        lowerViewId.contains("feed_filter") ||
                                        lowerViewId.contains("youtube_logo")
                if (isYtFeedIndicator) {
                    state.isSafeTabActive = true
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
                    contentDesc.contains("youtube shorts video")
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
                
                // Instagram Home/Explore screen checks to prevent false back trigger from posts
                val isIgFeedIndicator = text.contains("suggested posts") ||
                                        text.contains("suggested reels") ||
                                        text.contains("search and explore") ||
                                        text.contains("explore feed") ||
                                        lowerViewId.contains("feed_root") ||
                                        lowerViewId.contains("main_feed")
                if (isIgFeedIndicator) {
                    state.isSafeTabActive = true
                }
                
                // Instagram Reels (clips) player
                val isReelsPlayer = lowerViewId.contains("clips_video") || 
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
                                       lowerViewId.contains("tab") ||
                                       lowerViewId.contains("button") ||
                                       lowerViewId.contains("avatar")
                    if (!isFeedOrGrid) {
                        state.hasActivePlayerView = true
                    }
                }
                
                if (contentDesc.contains("clips video") || 
                    contentDesc.contains("instagram reels video") ||
                    contentDesc.contains("reels viewer") ||
                    contentDesc.contains("reel by") ||
                    contentDesc.contains("reels video")
                ) {
                    val isFeedOrExclusion = lowerViewId.contains("tab") || 
                                           lowerViewId.contains("button") || 
                                           lowerViewId.contains("thumbnail") ||
                                           lowerViewId.contains("grid") ||
                                           lowerViewId.contains("carousel") ||
                                           lowerViewId.contains("avatar")
                    if (!isFeedOrExclusion) {
                        state.hasActivePlayerView = true
                    }
                }
            }
            "com.facebook.katana", "com.facebook.lite" -> {
                if (isFacebookSafeTab(node) && isSelected) {
                    state.isSafeTabActive = true
                }
                if (isFacebookReelsTab(node) && isSelected) {
                    state.isShortsOrReelsTabActive = true
                }
                
                // Facebook Home Feed safe check to guarantee home view is safe (locale-independent)
                val isFbFeedIndicator = text.contains("suggested reels") || 
                                        contentDesc.contains("suggested reels") ||
                                        text.contains("reels and short videos") ||
                                        contentDesc.contains("reels and short videos") ||
                                        text.contains("news feed") ||
                                        text.contains("whats on your mind") ||
                                        contentDesc.contains("whats on your mind") ||
                                        text.contains("what's on your mind") ||
                                        contentDesc.contains("what's on your mind") ||
                                        lowerViewId.contains("composer") ||
                                        lowerViewId.contains("status") ||
                                        lowerViewId.contains("feed") ||
                                        node.isEditable
                if (isFbFeedIndicator) {
                    state.isSafeTabActive = true
                }
                
                // Track active player views
                val isReelsPlayerId = lowerViewId.contains("reels_viewer") || 
                                      lowerViewId.contains("reels_video") || 
                                      lowerViewId.contains("reels_tab_container") ||
                                      lowerViewId.contains("reel_video") || 
                                      lowerViewId.contains("reel_player") ||
                                      lowerViewId.contains("reels_container") ||
                                      lowerViewId.contains("reels_layout") ||
                                      lowerViewId.contains("reels_page") ||
                                      lowerViewId.contains("reels_shell") ||
                                      lowerViewId.contains("story_viewer") ||
                                      lowerViewId.contains("stories_viewer") ||
                                      (packageName == "com.facebook.lite" && (lowerViewId.contains("reels") || lowerViewId.contains("reel")))
                
                if (isReelsPlayerId) {
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
                
                // Text features for Reels detection
                if (contentDesc.contains("reels viewer") || 
                    contentDesc.contains("facebook reels video") ||
                    contentDesc.contains("reel by") ||
                    contentDesc.contains("reels video") ||
                    contentDesc == "reels" ||
                    text == "reels" ||
                    text == "facebook reels" ||
                    text.contains("reel by") ||
                    text.contains("reels video")
                ) {
                    val isFeedOrExclusion = lowerViewId.contains("tab") || 
                                           lowerViewId.contains("button") || 
                                           lowerViewId.contains("thumbnail") ||
                                           text.contains("suggested reels") ||
                                           contentDesc.contains("suggested reels")
                    if (!isFeedOrExclusion) {
                        state.hasActivePlayerView = true
                    }
                }
                
                // Dedicated robust check for Facebook Lite Reels
                if (packageName == "com.facebook.lite") {
                    val isLiteReel = text == "reels" || contentDesc == "reels" ||
                                     text == "reel" || contentDesc == "reel" ||
                                     text == "facebook reels" || contentDesc == "facebook reels" ||
                                     text.contains("reel by") || contentDesc.contains("reel by") ||
                                     text.contains("reels video") || contentDesc.contains("reels video")
                    
                    if (isLiteReel) {
                        val isExclusion = text.contains("suggested") || 
                                          contentDesc.contains("suggested") ||
                                          text.contains("shelf") ||
                                          contentDesc.contains("shelf") ||
                                          text.contains("grid") ||
                                          contentDesc.contains("grid") ||
                                          text.contains("carousel") ||
                                          contentDesc.contains("carousel") ||
                                          text.contains("thumbnail") ||
                                          contentDesc.contains("thumbnail") ||
                                          text.contains("and short videos") ||
                                          contentDesc.contains("and short videos") ||
                                          text.contains("whats on your") ||
                                          contentDesc.contains("whats on your") ||
                                          text.contains("what's on your") ||
                                          contentDesc.contains("what's on your") ||
                                          text.contains("create") ||
                                          contentDesc.contains("create") ||
                                          text.contains("posts") ||
                                          contentDesc.contains("posts") ||
                                          text.contains("tab") ||
                                          contentDesc.contains("tab") ||
                                          lowerViewId.contains("tab") ||
                                          lowerViewId.contains("button")
                        if (!isExclusion) {
                            state.hasActivePlayerView = true
                        }
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
        // 1. Robust pattern-based text fallback (extremely reliable across browsers)
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        val urlPatterns = listOf("http://", "https://", "www.")
        val isUrlPattern = urlPatterns.any { text.startsWith(it) || contentDesc.startsWith(it) }
        
        val targetDomains = listOf("youtube.com", "instagram.com", "facebook.com", "twitter.com", "x.com", "m.youtube.com", "m.facebook.com")
        val isTargetDomain = targetDomains.any { 
            text == it || text.startsWith("$it/") || text.startsWith("www.$it") ||
            contentDesc == it || contentDesc.startsWith("$it/") || contentDesc.startsWith("www.$it")
        }
        
        if (isUrlPattern || isTargetDomain) {
            val className = node.className?.toString() ?: ""
            if (className.endsWith("TextView") || className.endsWith("EditText") || className.contains("android.widget.TextView") || className.contains("android.widget.EditText") || node.isEditable) {
                return true
            }
        }
        
        // 2. High precision Suffix view ID match
        val viewId = node.viewIdResourceName?.lowercase() ?: ""
        if (viewId.isNotEmpty()) {
            val knownUrlBarSuffixes = listOf(
                "url_bar",
                "urlbar",
                "location_bar",
                "location_bar_edit_text",
                "url_bar_text",
                "url_field",
                "omnibar",
                "search_box_text",
                "mozac_browser_toolbar_url_view",
                "urlbar_title",
                "url_view",
                "url_input"
            )
            
            val matchesId = knownUrlBarSuffixes.any { viewId.endsWith(it) || viewId.contains(it) }
            if (matchesId) {
                // Filter out common false-positives
                val isFalsePositive = viewId.contains("suggest") || 
                                     viewId.contains("button") || 
                                     viewId.contains("icon") || 
                                     viewId.contains("option") ||
                                     viewId.contains("clear") ||
                                     viewId.contains("history") ||
                                     viewId.contains("bookmark") ||
                                     viewId.contains("carousel") ||
                                     viewId.contains("grid") ||
                                     viewId.contains("list") ||
                                     viewId.contains("tab")
                if (!isFalsePositive) {
                    return true
                }
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
            
            fun isYouTubeUrl(urlText: String, urlDesc: String): Boolean {
                if (urlText.contains("youtube.com") || urlText.contains("m.youtube.com") || urlText.contains("youtu.be") ||
                    urlDesc.contains("youtube.com") || urlDesc.contains("m.youtube.com") || urlDesc.contains("youtu.be")) {
                    return true
                }
                return urlText == "youtube" || urlText == "m.youtube" || urlDesc == "youtube" || urlDesc == "m.youtube"
            }
            
            fun isInstagramUrl(urlText: String, urlDesc: String): Boolean {
                if (urlText.contains("instagram.com") || urlDesc.contains("instagram.com")) {
                    return true
                }
                return urlText == "instagram" || urlDesc == "instagram"
            }
            
            fun isTwitterUrl(urlText: String, urlDesc: String): Boolean {
                if (urlText.contains("twitter.com") || urlText.contains("x.com") || urlText.contains("t.co") ||
                    urlDesc.contains("twitter.com") || urlDesc.contains("x.com") || urlDesc.contains("t.co")) {
                    return true
                }
                return urlText == "twitter" || urlText == "x" || urlText == "x.com" || urlDesc == "twitter" || urlDesc == "x" || urlDesc == "x.com"
            }
            
            fun isFacebookUrl(urlText: String, urlDesc: String): Boolean {
                if (urlText.contains("facebook.com") || urlText.contains("facebook.co") || urlText.contains("m.facebook") ||
                    urlDesc.contains("facebook.com") || urlDesc.contains("facebook.co") || urlDesc.contains("m.facebook")) {
                    if (urlText.contains("google.com/search") || urlDesc.contains("google.com/search")) {
                        return false // Avoid blocking google searches about Facebook
                    }
                    return true
                }
                return urlText == "facebook" || urlText == "m.facebook" || urlDesc == "facebook" || urlDesc == "m.facebook"
            }
            
            fun containsSubpath(urlText: String, urlDesc: String, subPaths: List<String>): Boolean {
                return subPaths.any { urlText.contains(it) || urlDesc.contains(it) }
            }
            
            if (ytConfig != null) {
                val isYtLimitReached = ytConfig.dailyLimitMinutes > 0 && ytConfig.timeUsedTodaySeconds >= ytConfig.dailyLimitMinutes * 60L
                if (isYtLimitReached && isYouTubeUrl(text, contentDesc)) {
                    triggerWebCounterpartBlock("YouTube", "youtube.com")
                    return true
                }
                if (ytConfig.blockShortsReels && isYouTubeUrl(text, contentDesc) && containsSubpath(text, contentDesc, listOf("shorts"))) {
                    triggerSurgicalWebBlock("YouTube Shorts")
                    return true
                }
            }
            
            if (igConfig != null) {
                val isIgLimitReached = igConfig.dailyLimitMinutes > 0 && igConfig.timeUsedTodaySeconds >= igConfig.dailyLimitMinutes * 60L
                if (isIgLimitReached && isInstagramUrl(text, contentDesc)) {
                    triggerWebCounterpartBlock("Instagram", "instagram.com")
                    return true
                }
                if (igConfig.blockShortsReels && isInstagramUrl(text, contentDesc) && containsSubpath(text, contentDesc, listOf("reels", "explore", "clips"))) {
                    triggerSurgicalWebBlock("Instagram Reels/Explore")
                    return true
                }
            }
            
            if (twConfig != null) {
                val isTwLimitReached = twConfig.dailyLimitMinutes > 0 && twConfig.timeUsedTodaySeconds >= twConfig.dailyLimitMinutes * 60L
                if (isTwLimitReached && isTwitterUrl(text, contentDesc)) {
                    triggerWebCounterpartBlock("X (Twitter)", "x.com")
                    return true
                }
                if (twConfig.blockShortsReels && isTwitterUrl(text, contentDesc) && containsSubpath(text, contentDesc, listOf("explore", "trends"))) {
                    triggerSurgicalWebBlock("Twitter Explore")
                    return true
                }
            }
            
            if (fbConfig != null) {
                val isFbLimitReached = fbConfig.dailyLimitMinutes > 0 && fbConfig.timeUsedTodaySeconds >= fbConfig.dailyLimitMinutes * 60L
                if (isFbLimitReached && isFacebookUrl(text, contentDesc)) {
                    triggerWebCounterpartBlock("Facebook", "facebook.com")
                    return true
                }
                if (fbConfig.blockShortsReels && isFacebookUrl(text, contentDesc) && containsSubpath(text, contentDesc, listOf("reels", "watch"))) {
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

    private fun scanAndBlockAppInfo(rootNode: AccessibilityNodeInfo) {
        if (!isSettingsLockedCached) return

        val now = System.currentTimeMillis()
        if (now - lastAppInfoBlockTime < 2000) return

        var hasZenScrollText = false
        var hasForceStop = false
        var hasUninstall = false
        // Device Admin page markers
        var hasDeactivateButton = false   // "Deactivate this device admin app" button
        var hasAdminPageTitle = false     // "Device admin apps" or "Device administrators" header

        fun collect(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            try {
                val text = node.text?.toString() ?: ""
                val desc = node.contentDescription?.toString() ?: ""
                val viewId = node.viewIdResourceName ?: ""
                val cls = node.className?.toString() ?: ""

                // DIAGNOSTIC: log every node so we can see what's actually on screen
                if (text.isNotEmpty() || desc.isNotEmpty() || viewId.isNotEmpty()) {
                    Log.d("ZenDiag", "${"  ".repeat(depth)}text='$text' desc='$desc' viewId='$viewId' cls='$cls' selected=${node.isSelected} enabled=${node.isEnabled}")
                }

                val textLower = text.lowercase()
                val descLower = desc.lowercase()
                val viewIdLower = viewId.lowercase()

                // Our app name — covers both App Info and Device Admin pages
                if (textLower.contains("zenscroll") || descLower.contains("zenscroll")) {
                    hasZenScrollText = true
                }

                // App Info page — Force Stop button
                if (textLower.contains("force stop") || descLower.contains("force stop") ||
                    textLower.contains("forcestop") || descLower.contains("forcestop") ||
                    viewIdLower.contains("force_stop") || viewIdLower.contains("left_button")
                ) {
                    hasForceStop = true
                }

                // App Info page — Uninstall button
                if (textLower.contains("uninstall") || descLower.contains("uninstall") ||
                    viewIdLower.contains("uninstall")
                ) {
                    hasUninstall = true
                }

                // Device Admin page — page-level title
                if (textLower.contains("device admin") || descLower.contains("device admin") ||
                    textLower.contains("device administrator") || descLower.contains("device administrator") ||
                    textLower.contains("admin apps") || descLower.contains("admin_apps") ||
                    textLower.contains("admin app") || descLower.contains("admin app") ||
                    viewIdLower.contains("device_admin")
                ) {
                    hasAdminPageTitle = true
                }

                // Device Admin page — the dangerous deactivate/remove button
                if (textLower.contains("deactivate") || descLower.contains("deactivate") ||
                    textLower.contains("remove") || descLower.contains("remove") ||
                    textLower.contains("deactivate this") || descLower.contains("deactivate this") ||
                    textLower.contains("remove admin") || descLower.contains("remove admin") ||
                    (textLower.contains("turn off") && (textLower.contains("admin") || descLower.contains("admin"))) ||
                    viewIdLower.contains("deactivate") || viewIdLower.contains("remove_admin")
                ) {
                    hasDeactivateButton = true
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    collect(child, depth + 1)
                    try {
                        child.recycle()
                    } catch (e: Exception) {
                        // ignore recycle exception
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error traversing accessibility node", e)
            }
        }

        collect(rootNode, 0)

        // Case 1: App Info page — app name visible alongside Force Stop or Uninstall
        val isAppInfoPage = hasZenScrollText && (hasForceStop || hasUninstall)

        // Case 2: Device Admin deactivation page — two-of-three rule handles OEM variation.
        // Some OEMs don't surface the app name as a text node on this page, so we don't
        // require all three signals.
        val isDeviceAdminPage = (hasAdminPageTitle && hasDeactivateButton) ||
                                (hasZenScrollText && hasDeactivateButton) ||
                                (hasAdminPageTitle && hasZenScrollText)

        if (isAppInfoPage || isDeviceAdminPage) {
            lastAppInfoBlockTime = now
            Log.d(TAG, "Protected page detected — appInfo=$isAppInfoPage adminPage=$isDeviceAdminPage — redirecting")
            performGlobalAction(GLOBAL_ACTION_BACK)
            val intent = Intent(this, com.example.MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("SHOW_UNLOCK_PROMPT", true)
            }
            startActivity(intent)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.d(TAG, "ScreenMonitorService destroyed")
    }
}
