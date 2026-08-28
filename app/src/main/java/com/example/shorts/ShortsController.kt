package com.example.shorts

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.MainActivity
import com.example.accessibility.AccessibilityEventListener
import com.example.data.AppBlockConfig
import com.example.data.BlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ShortsController(
    private val service: AccessibilityService,
    private val repository: BlockRepository
) : AccessibilityEventListener {

    private val detector = ShortsDetector()
    private val scope = CoroutineScope(Dispatchers.IO)
    private var configs: Map<String, AppBlockConfig> = emptyMap()

    init {
        scope.launch {
            repository.allConfigs.collect { list ->
                configs = list.associateBy { it.packageName }
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?, packageName: String) {
        if (rootNode == null) return
        
        if (packageName.contains("hermit") || packageName.contains("chimborazo")) {
            com.example.service.UsageTracker.updateState(packageName, true)
            triggerWebCounterpartBlock("Hermit", "hermit")
            return
        }
        
        val isBrowser = packageName == "com.android.chrome" || 
                        packageName == "org.mozilla.firefox" || 
                        packageName == "com.microsoft.emmx" ||
                        packageName == "com.opera.browser" ||
                        packageName == "com.duckduckgo.mobile.android" ||
                        packageName == "com.sec.android.app.sbrowser" ||
                        packageName.contains("browser") ||
                        packageName.contains("hermit") ||
                        packageName.contains("chimborazo")
        val isReliableBrowser = isBrowser
        val className = event.className?.toString() ?: ""

        val config = configs[packageName]
        val result = detector.detect(rootNode, packageName, className, config, isReliableBrowser, configs)

        var targetPackage = config?.packageName ?: packageName
        if (result.isWebCounterpart || isReliableBrowser) {
            val counterpartConfig = configs.values.find { 
                result.blockLabel.contains(it.appName, ignoreCase = true) || 
                result.domain.contains(it.appName.lowercase()) ||
                (it.appName == "YouTube" && (result.blockLabel.contains("YouTube") || result.domain.contains("youtube"))) ||
                (it.appName == "Facebook" && (result.blockLabel.contains("Facebook") || result.domain.contains("facebook"))) ||
                (it.appName == "Instagram" && (result.blockLabel.contains("Instagram") || result.domain.contains("instagram"))) ||
                (it.appName == "X (Twitter)" && (result.blockLabel.contains("Twitter") || result.blockLabel.contains("X") || result.domain.contains("x.com") || result.domain.contains("twitter")))
            }
            if (counterpartConfig != null) {
                targetPackage = counterpartConfig.packageName
            }
        }
        val targetConfig = configs[targetPackage]

        com.example.service.UsageTracker.updateState(targetPackage, result.shouldBlock)
        
        val cfg = targetConfig ?: config
        val isAppBlocked = cfg?.isBlockedNow == true || ((cfg?.dailyLimitMinutes ?: 0) > 0 && (cfg?.timeUsedTodaySeconds ?: 0L) >= (cfg?.dailyLimitMinutes ?: 0) * 60L)
        val limitMinutes = cfg?.shortsLimitMinutes ?: 0
        val usedSeconds = cfg?.shortsUsedTodaySeconds ?: 0L
        val isLimitExceeded = limitMinutes > 0 && usedSeconds >= limitMinutes * 60L
        val isShortsBlockedFlag = cfg?.isShortsBlockedNow == true

        val shouldBlockReelsOrSite = if (limitMinutes > 0) {
            isLimitExceeded || isShortsBlockedFlag || isAppBlocked
        } else {
            cfg?.blockShortsReels == true || isAppBlocked
        }

        if (result.shouldBlock || (isReliableBrowser && result.domain.isNotEmpty() && shouldBlockReelsOrSite)) {
            if (shouldBlockReelsOrSite) {
                triggerWebCounterpartBlock(result.blockLabel.ifEmpty { cfg?.appName ?: "Browser Site" }, result.domain.ifEmpty { "blocked_site" })
            } else if (!isReliableBrowser) {
                triggerSurgicalBlock(result.blockLabel)
            }
        }
    }

    override fun onInterrupt() {
        // Handle interrupt
    }

    private fun triggerSurgicalBlock(label: String) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun triggerWebCounterpartBlock(appName: String, domain: String) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        
        val intent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BLOCKED_APP_PACKAGE", "browser_bypass_$domain")
            putExtra("BLOCKED_APP_NAME", "$appName (Web)")
        }
        service.startActivity(intent)
    }
}
