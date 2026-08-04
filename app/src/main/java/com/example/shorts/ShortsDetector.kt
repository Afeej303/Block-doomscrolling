package com.example.shorts

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.AppBlockConfig
import com.example.debug.DebugManager

data class DetectionResult(
    val shouldBlock: Boolean,
    val isWebCounterpart: Boolean = false,
    val domain: String = "",
    val blockLabel: String = "",
    val confidence: Int = 0
)

enum class RulePriority { PRIMARY, SECONDARY, FALLBACK }

data class DetectionRule(
    val name: String,
    val priority: RulePriority,
    val score: Int,
    val matchCondition: (AppScreenState) -> Boolean
)

class AppScreenState {
    val viewIds = mutableSetOf<String>()
    val texts = mutableSetOf<String>()
    val contentDescs = mutableSetOf<String>()
    val classNames = mutableSetOf<String>()
    
    val selectedTabs = mutableSetOf<String>()
    
    var packageName = ""
    var className = ""
    var isReliableBrowser = false
}

class ShortsDetector {
    
    private val rules = listOf(
        // YouTube Rules
        DetectionRule("YouTube Selected Shorts Tab", RulePriority.PRIMARY, 60) { state ->
            state.packageName.contains("youtube") && 
            state.selectedTabs.any { it.contains("shorts") || it.contains("reels") }
        },
        DetectionRule("YouTube Player View ID (reel_recycler/reel_viewer)", RulePriority.PRIMARY, 80) { state ->
            state.packageName.contains("youtube") &&
            state.viewIds.any { it.contains("reel_recycler") || it.contains("reel_viewer") || it.contains("shorts_video") }
        },
        DetectionRule("YouTube Web URL Match", RulePriority.PRIMARY, 100) { state ->
            state.isReliableBrowser && (state.texts.any { it.contains("youtube.com/shorts") } || state.contentDescs.any { it.contains("youtube.com/shorts") })
        },
        DetectionRule("YouTube Shorts Activity", RulePriority.FALLBACK, 30) { state ->
            state.packageName.contains("youtube") && state.className.contains("shorts")
        },
        
        // Instagram Rules
        DetectionRule("Instagram Selected Reels Tab", RulePriority.PRIMARY, 60) { state ->
            state.packageName.contains("instagram") && 
            state.selectedTabs.any { it.contains("reels") || it.contains("clips") }
        },
        DetectionRule("Instagram Player View ID (clips_viewer/reel_viewer)", RulePriority.PRIMARY, 80) { state ->
            state.packageName.contains("instagram") &&
            state.viewIds.any { it.contains("clips_viewer") || it.contains("clips_video_container") || it.contains("reel_viewer") }
        },
        DetectionRule("Instagram Web URL Match", RulePriority.PRIMARY, 100) { state ->
            state.isReliableBrowser && (state.texts.any { it.contains("instagram.com/reels") || it.contains("instagram.com/reel") } || state.contentDescs.any { it.contains("instagram.com/reels") || it.contains("instagram.com/reel") })
        },
        DetectionRule("Instagram Reel Player Class", RulePriority.SECONDARY, 40) { state ->
            state.packageName.contains("instagram") && state.classNames.any { it.contains("reel") } && state.viewIds.any { it.contains("player") }
        },

        // Facebook Rules
        DetectionRule("Facebook Selected Reels Tab", RulePriority.PRIMARY, 60) { state ->
            state.packageName.contains("facebook") && 
            state.selectedTabs.any { it.contains("reels") }
        },
        DetectionRule("Facebook Reels View ID", RulePriority.PRIMARY, 80) { state ->
            state.packageName.contains("facebook") &&
            state.viewIds.any { it.contains("reel") || it.contains("clips") }
        },
        
        // TikTok Rules
        DetectionRule("TikTok Main App", RulePriority.PRIMARY, 100) { state ->
            state.packageName.contains("zhiliaoapp") || state.packageName.contains("trill")
        },

        // Snapchat Spotlight Rules
        DetectionRule("Snapchat Spotlight Tab", RulePriority.PRIMARY, 60) { state ->
            state.packageName.contains("snapchat") &&
            state.selectedTabs.any { it.contains("spotlight") }
        },
        DetectionRule("Snapchat Spotlight View", RulePriority.PRIMARY, 80) { state ->
            state.packageName.contains("snapchat") &&
            state.viewIds.any { it.contains("spotlight") }
        }
    )

    fun detect(
        rootNode: AccessibilityNodeInfo, 
        packageName: String, 
        className: String,
        config: AppBlockConfig?,
        isReliableBrowser: Boolean,
        configs: Map<String, AppBlockConfig> = emptyMap()
    ): DetectionResult {
        
        val isAppShortsBlocked = config?.blockShortsReels == true || (config?.shortsLimitMinutes ?: 0) > 0
        val isYoutubeShortsBlocked = configs.values.any { it.packageName.contains("youtube") && it.blockShortsReels } || configs.values.any { it.blockShortsReels }
        
        if (!isAppShortsBlocked && !isReliableBrowser) {
            return DetectionResult(false)
        }

        val state = AppScreenState().apply {
            this.packageName = packageName.lowercase()
            this.className = className.lowercase()
            this.isReliableBrowser = isReliableBrowser
        }
        
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            
            val viewId = node.viewIdResourceName ?: ""
            val contentDesc = node.contentDescription?.toString() ?: ""
            val text = node.text?.toString() ?: ""
            val nodeClass = node.className?.toString() ?: ""
            
            val viewIdLow = viewId.substringAfterLast('/').lowercase()
            val contentDescLow = contentDesc.lowercase()
            val textLow = text.lowercase()
            val classLow = nodeClass.lowercase()
            
            if (viewIdLow.isNotBlank()) state.viewIds.add(viewIdLow)
            if (textLow.isNotBlank()) state.texts.add(textLow)
            if (contentDescLow.isNotBlank()) state.contentDescs.add(contentDescLow)
            if (classLow.isNotBlank()) state.classNames.add(classLow)

            if (node.isSelected) {
                if (contentDescLow.isNotBlank()) state.selectedTabs.add(contentDescLow)
                if (textLow.isNotBlank()) state.selectedTabs.add(textLow)
            }

            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                collect(child)
                child?.recycle()
            }
        }
        
        collect(rootNode)

        var totalConfidence = 0
        val matchedRuleNames = mutableListOf<String>()
        var isWeb = false
        var webDomain = ""
        var labelStr = ""
        
        for (rule in rules) {
            val matched = rule.matchCondition(state)

            if (matched) {
                totalConfidence += rule.score
                matchedRuleNames.add("${rule.name} (+${rule.score})")
                
                if (rule.name.contains("Web URL Match")) {
                    isWeb = true
                    webDomain = state.texts.firstOrNull { it.contains("youtube.com/shorts") || it.contains("instagram.com/reel") } 
                        ?: state.contentDescs.firstOrNull { it.contains("youtube.com/shorts") || it.contains("instagram.com/reel") } 
                        ?: ""
                }
            }
        }
        
        val shouldBlock = totalConfidence >= 50

        if (shouldBlock) {
            labelStr = when {
                state.packageName.contains("youtube") || webDomain.contains("youtube") -> "YouTube Shorts"
                state.packageName.contains("instagram") || webDomain.contains("instagram") -> "Instagram Reels"
                state.packageName.contains("facebook") -> "Facebook Reels"
                state.packageName.contains("zhiliaoapp") || state.packageName.contains("trill") -> "TikTok"
                state.packageName.contains("snapchat") -> "Snapchat Spotlight"
                else -> "Short-form Video"
            }
        }

        if (DebugManager.state.value.isEnabled) {
            val debugMatchedRule = if (matchedRuleNames.isNotEmpty()) {
                matchedRuleNames.joinToString("\n✓ ")
                    .let { "✓ $it\n\nConfidence Score: $totalConfidence" }
            } else {
                "No matches"
            }

            DebugManager.updateInfo(
                currentPackage = packageName,
                currentActivity = className,
                viewIds = state.viewIds.joinToString(", "),
                texts = state.texts.joinToString(", "),
                contentDescriptions = state.contentDescs.joinToString(", "),
                matchedRule = debugMatchedRule,
                actionTaken = if (shouldBlock) "BACK" else "None"
            )
        }
        
        return DetectionResult(shouldBlock, isWeb, webDomain, labelStr, totalConfidence)
    }
}
