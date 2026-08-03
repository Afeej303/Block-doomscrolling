package com.example.shorts

import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.example.data.AppBlockConfig
import com.example.debug.DebugManager

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
        className: String,
        config: AppBlockConfig?,
        isReliableBrowser: Boolean
    ): DetectionResult {
        
        val viewIds = mutableSetOf<String>()
        val texts = mutableSetOf<String>()
        val contentDescs = mutableSetOf<String>()
        
        fun collect(node: AccessibilityNodeInfo?) {
            if (node == null) return
            node.viewIdResourceName?.let { viewIds.add(it.substringAfterLast('/')) }
            node.text?.toString()?.takeIf { it.isNotBlank() }?.let { texts.add(it) }
            node.contentDescription?.toString()?.takeIf { it.isNotBlank() }?.let { contentDescs.add(it) }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i)
                collect(child)
                child?.recycle()
            }
        }
        
        collect(rootNode)
        
        if (DebugManager.state.value.isEnabled) {
            Log.d("AccessibilityInstrument", "Pkg: $packageName, Class: $className")
            Log.d("AccessibilityInstrument", "ViewIDs: ${viewIds.joinToString(", ")}")
            Log.d("AccessibilityInstrument", "Texts: ${texts.joinToString(", ")}")
            Log.d("AccessibilityInstrument", "ContentDescs: ${contentDescs.joinToString(", ")}")
            
            DebugManager.updateInfo(
                currentPackage = packageName,
                currentActivity = className,
                viewIds = viewIds.joinToString(", "),
                texts = texts.joinToString(", "),
                contentDescriptions = contentDescs.joinToString(", ")
            )
        }

        if (config == null || !config.blockShortsReels) {
            return DetectionResult(false)
        }

        var shouldBlock = false
        var webCounterpart = false
        var domainStr = ""
        var labelStr = ""
        var matchedRule = ""

        val viewIdsStr = viewIds.joinToString(" ").lowercase()
        val textsStr = texts.joinToString(" ").lowercase()
        val descsStr = contentDescs.joinToString(" ").lowercase()
        val classLow = className.lowercase()

        when {
            packageName.contains("youtube") -> {
                if (classLow.contains("shorts") || 
                    viewIdsStr.contains("shorts") || 
                    viewIdsStr.contains("reel") ||
                    textsStr.contains("shorts") || 
                    descsStr.contains("shorts")) {
                    
                    shouldBlock = true
                    labelStr = "YouTube Shorts"
                    matchedRule = "YouTube Shorts Match"
                }
            }
            packageName.contains("instagram") -> {
                if (classLow.contains("reel") || 
                    classLow.contains("clips") ||
                    viewIdsStr.contains("clips") ||
                    viewIdsStr.contains("reel") ||
                    textsStr.contains("reels") ||
                    descsStr.contains("reels")) {
                    
                    shouldBlock = true
                    labelStr = "Instagram Reels"
                    matchedRule = "Instagram Reels Match"
                }
            }
            packageName.contains("facebook") -> {
                if (viewIdsStr.contains("reel") ||
                    textsStr.contains("reels") ||
                    descsStr.contains("reels")) {
                    
                    shouldBlock = true
                    labelStr = "Facebook Reels"
                    matchedRule = "Facebook Reels Match"
                }
            }
        }
        
        if (shouldBlock && DebugManager.state.value.isEnabled) {
            DebugManager.updateInfo(
                matchedRule = matchedRule,
                actionTaken = "BACK"
            )
        }
        
        return DetectionResult(shouldBlock, webCounterpart, domainStr, labelStr)
    }
}
