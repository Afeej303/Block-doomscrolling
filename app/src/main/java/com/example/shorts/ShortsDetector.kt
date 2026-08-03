package com.example.shorts

import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.AppBlockConfig

data class DetectionResult(
    val shouldBlock: Boolean,
    val isWebCounterpart: Boolean = false,
    val domain: String = "",
    val blockLabel: String = ""
)

class ShortsDetector {
    
    fun detect(
        rootNode: AccessibilityNodeInfo, 
        packageName: String, 
        config: AppBlockConfig?,
        isReliableBrowser: Boolean
    ): DetectionResult {
        if (config == null || !config.blockShortsReels) {
            return DetectionResult(false)
        }
        
        var shouldBlock = false
        var webCounterpart = false
        var domainStr = ""
        var labelStr = ""
        
        // Porting the logic from analyzeScreenState and processScreenState
        // We will need the state collected from traversal.
        
        val state = AppScreenState()
        analyzeScreenState(rootNode, packageName, state)
        
        when {
            packageName.contains("youtube") -> {
                if (state.hasActivePlayerView && (state.isShortsOrReelsTabActive || !isReliableBrowser)) {
                    shouldBlock = true
                    labelStr = "YouTube Shorts"
                }
            }
            packageName.contains("instagram") -> {
                if (state.hasActivePlayerView && !state.isSafeTabActive) {
                    shouldBlock = true
                    labelStr = "Instagram Reels"
                }
            }
            packageName.contains("facebook") -> {
                if (state.isShortsOrReelsTabActive) {
                    shouldBlock = true
                    labelStr = "Facebook Reels"
                }
            }
            packageName.contains("twitter") || packageName.contains("x.com") -> {
                shouldBlock = true
                webCounterpart = true
                domainStr = "x.com"
            }
        }
        
        return DetectionResult(shouldBlock, webCounterpart, domainStr, labelStr)
    }
    
    class AppScreenState {
        var isShortsOrReelsTabActive = false
        var hasActivePlayerView = false
        var isSafeTabActive = false
    }
    
    private fun analyzeScreenState(node: AccessibilityNodeInfo?, packageName: String, state: AppScreenState) {
        if (node == null) return
        
        val text = node.text?.toString()?.lowercase() ?: ""
        val contentDesc = node.contentDescription?.toString()?.lowercase() ?: ""
        val lowerViewId = node.viewIdResourceName?.lowercase() ?: ""

        val isSelected = isNodeSelected(node)

        // 1. YouTube Shorts Detection
        if (packageName.contains("youtube")) {
            val isShortsTabSelected = (text == "shorts" || contentDesc == "shorts") && isSelected
            if (isShortsTabSelected) state.isShortsOrReelsTabActive = true
            
            val isShortsPlayer = lowerViewId.contains("reel") || 
                               lowerViewId.contains("shorts") || 
                               lowerViewId.contains("player") ||
                               contentDesc.contains("player")
            if (isShortsPlayer && node.isVisibleToUser) {
                state.hasActivePlayerView = true
            }
        }

        // 2. Instagram Reels Detection
        if (packageName.contains("instagram")) {
            val isReelsSelected = (text == "reels" || contentDesc == "reels") && isSelected
            if (isReelsSelected) state.isShortsOrReelsTabActive = true

            val isIgSafeTab = isInstagramSafeTab(node)
            if (isIgSafeTab && isSelected) {
                state.isSafeTabActive = true
            }

            val isIgProfilePage = (text == "posts" ||
                                   text == "followers" ||
                                   text == "following" ||
                                   text == "message" ||
                                   text == "contact" ||
                                   text == "follow" ||
                                   text.contains("followers") ||
                                   text.contains("following") ||
                                   contentDesc.contains("profile picture") ||
                                   contentDesc.contains("profile photo") ||
                                   lowerViewId.contains("profile_header") ||
                                   lowerViewId.contains("profile_tab") ||
                                   lowerViewId.contains("profile_grid")) && node.isVisibleToUser

            if (isIgProfilePage) {
                state.isSafeTabActive = true
            }

            val isIgProfileHeader = (
                lowerViewId.contains("profile_header") ||
                lowerViewId.contains("row_profile_header") ||
                lowerViewId.contains("profile_tab") ||
                lowerViewId.contains("profile_bio") ||
                lowerViewId.contains("profile_name") ||
                lowerViewId.contains("profile_avatar") ||
                lowerViewId.contains("profile_picture") ||
                text == "edit profile" || text == "share profile" ||
                contentDesc == "edit profile" || contentDesc == "share profile"
            ) && node.isVisibleToUser
            if (isIgProfileHeader) {
                state.isSafeTabActive = true
            }

            val isIgFeedIndicator = (text.contains("home") || 
                                    text.contains("explore feed") || 
                                    lowerViewId.contains("feed_root") || 
                                    lowerViewId.contains("row_feed") || 
                                    lowerViewId.contains("action_bar") || 
                                    lowerViewId.contains("comment") || 
                                    lowerViewId.contains("main_feed")) && node.isVisibleToUser
            if (isIgFeedIndicator) {
                state.isSafeTabActive = true
            }

            val isReelsPlayer = lowerViewId.contains("clips_video") || 
                                lowerViewId.contains("clips_viewer") ||
                                lowerViewId.contains("reel") ||
                                lowerViewId.contains("video") ||
                                contentDesc.contains("video") ||
                                text == "remix" || contentDesc == "remix" ||
                                text.contains("comment on this") || 
                                contentDesc.contains("comment on this") ||
                                contentDesc.contains("play current reel") ||
                                contentDesc.contains("play reel") ||
                                text.contains("swipe up for next") ||
                                contentDesc.contains("swipe up for next")
            
            if (isReelsPlayer && node.isVisibleToUser) {
                val isFeedOrGrid = lowerViewId.contains("grid") || 
                                   lowerViewId.contains("list") || 
                                   lowerViewId.contains("carousel") || 
                                   lowerViewId.contains("thumbnail") || 
                                   lowerViewId.contains("feed") ||
                                   lowerViewId.contains("row_feed") ||
                                   lowerViewId.contains("preview") ||
                                   lowerViewId.contains("card") ||
                                   lowerViewId.contains("row") ||
                                   lowerViewId.contains("button") ||
                                   lowerViewId.contains("comment") ||
                                   lowerViewId.contains("avatar")
                if (!isFeedOrGrid) {
                    state.hasActivePlayerView = true
                }
            }
        }

        // 3. Facebook Reels Detection
        if (packageName.contains("facebook")) {
            val isReelsSelected = (text == "reels" || contentDesc == "reels" || 
                                 text == "video" || contentDesc == "video") && isSelected
            if (isReelsSelected) state.isShortsOrReelsTabActive = true

            val isFbSafeTab = isFacebookSafeTab(node)
            if (isFbSafeTab && isSelected) {
                state.isSafeTabActive = true
            }

            val isFbFeedIndicator = (text.contains("home") || 
                                     text.contains("news feed") || 
                                     lowerViewId.contains("composer") ||
                                     lowerViewId.contains("whats_on_your_mind") ||
                                     lowerViewId.contains("news_feed") ||
                                     lowerViewId.contains("feed_view") ||
                                     lowerViewId.contains("feed_recycler") ||
                                     lowerViewId.contains("feed_container") ||
                                     lowerViewId.contains("main_feed")) && node.isVisibleToUser
                                     
            if (isFbFeedIndicator) {
                state.isSafeTabActive = true
            }

            val isReelsTextOrDesc = (lowerViewId.contains("reel") || 
                                   lowerViewId.contains("video") ||
                                   lowerViewId.contains("player") ||
                                   text.contains("swipe up") ||
                                   contentDesc.contains("swipe up")) && 
                                   node.isVisibleToUser
                                   
            if (isReelsTextOrDesc) {
                val isFeedOrExclusion = lowerViewId.contains("story") || 
                                       lowerViewId.contains("stories") ||
                                       lowerViewId.contains("button") || 
                                       lowerViewId.contains("thumbnail") ||
                                       lowerViewId.contains("grid") ||
                                       lowerViewId.contains("row_feed") ||
                                       lowerViewId.contains("feed") ||
                                       lowerViewId.contains("comment") ||
                                       lowerViewId.contains("carousel") ||
                                       lowerViewId.contains("avatar")
                if (!isFeedOrExclusion) {
                    state.hasActivePlayerView = true
                }
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            analyzeScreenState(child, packageName, state)
            child?.recycle()
        }
    }
    
    private fun isNodeSelected(node: AccessibilityNodeInfo): Boolean {
        if (node.isSelected) return true
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text.contains("selected") || desc.contains("selected")) return true
        var p = node.parent
        var depth = 0
        while (p != null && depth < 2) {
            if (p.isSelected) {
                p.recycle()
                return true
            }
            val pDesc = p.contentDescription?.toString()?.lowercase() ?: ""
            if (pDesc.contains("selected")) {
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

    private fun isInstagramSafeTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text.contains("home") || desc.contains("home")) return true
        if (text.contains("search") || desc.contains("search") || text.contains("explore") || desc.contains("explore")) return true
        if (text.contains("create") || desc.contains("create") || text.contains("new post") || desc.contains("new post")) return true
        if (text.contains("notification") || desc.contains("notification") || text.contains("activity") || desc.contains("activity")) return true
        if (text.contains("profile") || desc.contains("profile")) return true
        if (text.contains("message") || desc.contains("message") || text.contains("direct") || desc.contains("direct") || text.contains("threads") || desc.contains("threads")) return true
        return false
    }

    private fun isFacebookSafeTab(node: AccessibilityNodeInfo): Boolean {
        val text = node.text?.toString()?.lowercase() ?: ""
        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        if (text.contains("home") || desc.contains("home") || text.contains("news feed") || desc.contains("news feed")) return true
        if (text.contains("groups") || desc.contains("groups")) return true
        if (text.contains("marketplace") || desc.contains("marketplace") || text.contains("shop") || desc.contains("shop")) return true
        if (text.contains("profile") || desc.contains("profile")) return true
        if (text.contains("notifications") || desc.contains("notifications")) return true
        if (text.contains("menu") || desc.contains("menu")) return true
        return false
    }
}
