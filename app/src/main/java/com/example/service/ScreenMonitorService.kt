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
        "org.chromium.chrome",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fennec_aurora",
        "org.mozilla.focus",
        "com.sec.android.app.sbrowser",
        "com.sec.android.app.sbrowser.beta",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.opera.gx",
        "com.microsoft.emmx",
        "com.microsoft.emmx.beta",
        "com.duckduckgo.mobile.android",
        "com.android.browser",
        "com.brave.browser",
        "com.vivaldi.browser",
        "com.kiwibrowser.browser",
        "org.bromite.bromite",
        "org.cromite.cromite",
        "ru.yandex.searchplugin",
        "com.ecosia.android",
        "com.UCMobile.intl",
        "mark.via.gp",
        "com.yandex.browser"
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
                    
                    val shouldBlock = if (state.isSafeTabActive) {
                        false
                    } else if (state.hasActivePlayerView) {
                        true
                    } else {
                        state.isShortsOrReelsTabActive
                    }
                    
                    if (originalPackageName.contains("facebook")) {
                        Log.d("FbDiag", "Screen analysis for $originalPackageName: isSafeTab=${state.isSafeTabActive}, hasActivePlayer=${state.hasActivePlayerView}, isShortsTab=${state.isShortsOrReelsTabActive} -> shouldBlock=$shouldBlock")
                    }
                    
                    if (shouldBlock) {
                        Log.d(TAG, "Surgical block triggered for $originalPackageName!")
                        
                        var success = false
                        if (originalPackageName.contains("facebook")) {
                            success = findAndClickHomeTab(rootNode)
                        }
                        
                        if (!success) {
                            performGlobalAction(GLOBAL_ACTION_BACK)
                        }
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
            "com.instagram.android", "com.instagram.lite" -> {
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
                if (node.isVisibleToUser && (text.isNotEmpty() || contentDesc.isNotEmpty() || lowerViewId.isNotEmpty())) {
                    Log.d("FbDiag", "Node ($packageName): id='$lowerViewId', text='$text', desc='$contentDesc', isSelected=${node.isSelected}")
                }
                
                if (isFacebookSafeTab(node) && isSelected && node.isVisibleToUser) {
                    state.isSafeTabActive = true
                }
                if (isFacebookReelsTab(node) && isSelected && node.isVisibleToUser) {
                    state.isShortsOrReelsTabActive = true
                }
                
                // Facebook Home Feed safe check to guarantee home view is safe (locale-independent)
                // We avoid generic "feed" or "status" or "isEditable" matches that trigger false positives in Reels.
                val isFbFeedIndicator = (text.contains("whats on your mind") || 
                                         contentDesc.contains("whats on your mind") ||
                                         text.contains("what's on your mind") ||
                                         contentDesc.contains("what's on your mind") ||
                                         text.contains("create a post") ||
                                         contentDesc.contains("create a post") ||
                                         text.contains("news feed") ||
                                         contentDesc.contains("news feed") ||
                                         lowerViewId.contains("composer") ||
                                         lowerViewId.contains("whats_on_your_mind") ||
                                         lowerViewId.contains("news_feed") ||
                                         lowerViewId.contains("main_feed")) && node.isVisibleToUser
                                         
                if (isFbFeedIndicator) {
                    state.isSafeTabActive = true
                }

                // Profile page indicators (locale-independent checks for profile sections)
                val isProfilePage = (text == "posts" || text == "about" || text == "photos" || text == "videos" || text == "friends" ||
                                      text.contains("mutual friends") || text.contains("add friend") || text == "message" || text.contains("joined facebook") ||
                                      text.contains("lives in") || text.contains("edit profile") || text.contains("followed by")) && node.isVisibleToUser
                if (isProfilePage) {
                    state.isSafeTabActive = true
                }

                // Menu & Settings page indicators
                val isMenuPage = (text == "settings" || text == "dark mode" || text == "log out" || text == "help & support" || text == "saved" || text == "find friends" || text == "pages" || text == "groups") && node.isVisibleToUser
                if (isMenuPage) {
                    state.isSafeTabActive = true
                }
                
                // Reels detection based on specific ID keywords.
                // We relaxed the exclusion of 'feed' and 'tab' to prevent false negatives,
                // as actual Reels player structures often contain these keywords in their layout hierarchies.
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
                                      lowerViewId.contains("reels_vp") ||
                                      lowerViewId.contains("reel_vp") ||
                                      (lowerViewId.contains("reel") && lowerViewId.contains("player"))
                
                if (isReelsPlayerId) {
                    // Exclude thumbnails, previews, grids, shelves, or carousels which represent previews on the safe feed.
                    val isExposedPreview = lowerViewId.contains("grid") || 
                                           lowerViewId.contains("shelf") || 
                                           lowerViewId.contains("thumbnail") ||
                                           lowerViewId.contains("preview") ||
                                           lowerViewId.contains("carousel") ||
                                           lowerViewId.contains("card")
                    if (!isExposedPreview) {
                        state.hasActivePlayerView = true
                    }
                }
                
                // Bulletproof Reels text/desc indicators
                val isReelsTextOrDesc = text.contains("share this reel") || 
                                        contentDesc.contains("share this reel") ||
                                        text.contains("remix this reel") || 
                                        contentDesc.contains("remix this reel") ||
                                        text.contains("reel by") || 
                                        contentDesc.contains("reel by") ||
                                        text.contains("reels video") || 
                                        contentDesc.contains("reels video") ||
                                        text.contains("facebook reels") || 
                                        contentDesc.contains("facebook reels") ||
                                        text.contains("reels viewer") || 
                                        contentDesc.contains("reels viewer") ||
                                        text.contains("original audio") || 
                                        contentDesc.contains("original audio") ||
                                        text.contains("original sound") || 
                                        contentDesc.contains("original sound") ||
                                        text.contains("audio used in this") || 
                                        contentDesc.contains("audio used in this") ||
                                        text.contains("double-tap") || 
                                        contentDesc.contains("double-tap") ||
                                        text.contains("remix") || 
                                        contentDesc.contains("remix") ||
                                        text.contains("comment on this") || 
                                        contentDesc.contains("comment on this") ||
                                        text == "reels" || contentDesc == "reels" ||
                                        text == "reel" || contentDesc == "reel" ||
                                        contentDesc.contains("play current reel") ||
                                        contentDesc.contains("play reel")
                
                if (isReelsTextOrDesc) {
                    // Double check we are not in feed previews, lists, tabs, or buttons on safe screens.
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
                                      contentDesc.contains("and short videos")
                                      
                    if (!isExclusion) {
                        state.hasActivePlayerView = true
                    }
                }
            }
            "com.twitter.android", "com.twitter.lite", "com.twitter.android.lite" -> {
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

    private fun isYouTubeUrl(text: String, desc: String): Boolean {
        if (text.contains("youtube.com") || text.contains("m.youtube.com") || text.contains("youtu.be") ||
            desc.contains("youtube.com") || desc.contains("m.youtube.com") || desc.contains("youtu.be")) {
            return true
        }
        return text.contains("youtube") || desc.contains("youtube")
    }

    private fun isInstagramUrl(text: String, desc: String): Boolean {
        if (text.contains("instagram.com") || desc.contains("instagram.com")) {
            return true
        }
        return text.contains("instagram") || desc.contains("instagram")
    }

    private fun isTwitterUrl(text: String, desc: String): Boolean {
        if (text.contains("twitter.com") || text.contains("x.com") || text.contains("t.co") ||
            desc.contains("twitter.com") || desc.contains("x.com") || desc.contains("t.co")) {
            return true
        }
        return text.contains("twitter") || text.contains("x.com") || desc.contains("twitter") || desc.contains("x.com")
    }

    private fun isFacebookUrl(text: String, desc: String): Boolean {
        if (text.contains("facebook.com") || text.contains("facebook.co") || text.contains("m.facebook") ||
            desc.contains("facebook.com") || desc.contains("facebook.co") || desc.contains("m.facebook")) {
            if (text.contains("google.com/search") || desc.contains("google.com/search")) {
                return false // Avoid blocking google searches about Facebook
            }
            return true
        }
        return text.contains("facebook") || desc.contains("facebook")
    }

    private fun containsSubpath(text: String, desc: String, subPaths: List<String>): Boolean {
        return subPaths.any { text.contains(it) || desc.contains(it) }
    }

    private fun checkAndBlockBrowser(rootNode: AccessibilityNodeInfo): Boolean {
        var foundYoutube = false
        var foundInstagram = false
        var foundTwitter = false
        var foundFacebook = false
        
        var foundShorts = false
        var foundReels = false
        var foundExploreTw = false
        var foundExploreIg = false
        var foundWatch = false

        fun scanNodes(node: AccessibilityNodeInfo?) {
            if (node == null) return
            val text = node.text?.toString()?.lowercase() ?: ""
            val desc = node.contentDescription?.toString()?.lowercase() ?: ""
            
            if (text.isNotEmpty() || desc.isNotEmpty()) {
                if (isYouTubeUrl(text, desc)) { foundYoutube = true; Log.d("WebDiag", "Found YT: text='$text', desc='$desc'") }
                if (isInstagramUrl(text, desc)) { foundInstagram = true; Log.d("WebDiag", "Found IG: text='$text', desc='$desc'") }
                if (isTwitterUrl(text, desc)) { foundTwitter = true; Log.d("WebDiag", "Found TW: text='$text', desc='$desc'") }
                if (isFacebookUrl(text, desc)) { foundFacebook = true; Log.d("WebDiag", "Found FB: text='$text', desc='$desc'") }
                
                if (containsSubpath(text, desc, listOf("shorts"))) { foundShorts = true; Log.d("WebDiag", "Found Shorts: text='$text', desc='$desc'") }
                if (containsSubpath(text, desc, listOf("reels", "clips"))) { foundReels = true; Log.d("WebDiag", "Found Reels/Clips: text='$text', desc='$desc'") }
                if (containsSubpath(text, desc, listOf("explore", "trends"))) { foundExploreTw = true; Log.d("WebDiag", "Found Explore/Trends: text='$text', desc='$desc'") }
                if (containsSubpath(text, desc, listOf("explore"))) { foundExploreIg = true; Log.d("WebDiag", "Found Explore (IG): text='$text', desc='$desc'") }
                if (containsSubpath(text, desc, listOf("watch"))) { foundWatch = true; Log.d("WebDiag", "Found Watch: text='$text', desc='$desc'") }
            }
            
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                scanNodes(child)
                child?.recycle()
            }
        }
        
        scanNodes(rootNode)

        Log.d("WebDiag", "Web state: yt=$foundYoutube(shorts=$foundShorts), ig=$foundInstagram(reels=$foundReels), fb=$foundFacebook(reels=$foundReels, watch=$foundWatch)")

        val ytConfig = appConfigs["com.google.android.youtube"]
        val igConfig = appConfigs["com.instagram.android"]
        val twConfig = appConfigs["com.twitter.android"]
        val fbConfig = appConfigs["com.facebook.katana"]

        if (ytConfig != null) {
            val isYtLimitReached = ytConfig.dailyLimitMinutes > 0 && ytConfig.timeUsedTodaySeconds >= ytConfig.dailyLimitMinutes * 60L
            if (isYtLimitReached && foundYoutube) {
                triggerWebCounterpartBlock("YouTube", "youtube.com")
                return true
            }
            if (ytConfig.blockShortsReels && foundYoutube && foundShorts) {
                triggerSurgicalWebBlock("YouTube Shorts")
                return true
            }
        }

        if (igConfig != null) {
            val isIgLimitReached = igConfig.dailyLimitMinutes > 0 && igConfig.timeUsedTodaySeconds >= igConfig.dailyLimitMinutes * 60L
            if (isIgLimitReached && foundInstagram) {
                triggerWebCounterpartBlock("Instagram", "instagram.com")
                return true
            }
            if (igConfig.blockShortsReels && foundInstagram && (foundReels || foundExploreIg)) {
                triggerSurgicalWebBlock("Instagram Reels/Explore")
                return true
            }
        }

        if (twConfig != null) {
            val isTwLimitReached = twConfig.dailyLimitMinutes > 0 && twConfig.timeUsedTodaySeconds >= twConfig.dailyLimitMinutes * 60L
            if (isTwLimitReached && foundTwitter) {
                triggerWebCounterpartBlock("X (Twitter)", "x.com")
                return true
            }
            if (twConfig.blockShortsReels && foundTwitter && foundExploreTw) {
                triggerSurgicalWebBlock("Twitter Explore")
                return true
            }
        }

        if (fbConfig != null) {
            val isFbLimitReached = fbConfig.dailyLimitMinutes > 0 && fbConfig.timeUsedTodaySeconds >= fbConfig.dailyLimitMinutes * 60L
            if (isFbLimitReached && foundFacebook) {
                triggerWebCounterpartBlock("Facebook", "facebook.com")
                return true
            }
            if (fbConfig.blockShortsReels && foundFacebook && (foundReels || foundWatch)) {
                triggerSurgicalWebBlock("Facebook Reels")
                return true
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

    private fun performClick(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        var current = AccessibilityNodeInfo.obtain(node)
        while (current != null) {
            if (current.isClickable) {
                val success = current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    current.recycle()
                    return true
                }
            }
            val parentNode = current.parent
            current.recycle()
            if (parentNode != null && parentNode != current) {
                current = parentNode
            } else {
                current = null
            }
        }
        return false
    }

    private fun findAndClickHomeTab(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        
        val isHomeTab = (text == "home" || desc.contains("home") || desc.contains("news feed")) &&
                        (desc.contains("tab") || desc.contains("button") || text.contains("tab") || node.isClickable)
                        
        if (isHomeTab) {
            if (performClick(node)) {
                Log.d("FbDiag", "Successfully auto-clicked Home tab!")
                return true
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findAndClickHomeTab(child)
            child.recycle()
            if (found) return true
        }
        return false
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
