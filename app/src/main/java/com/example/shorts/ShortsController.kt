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
        
        val config = configs[packageName] ?: configs.values.find { it.appName == "X (Twitter)" && packageName.contains("twitter") }
        
        val isBrowser = packageName == "com.android.chrome" || packageName == "org.mozilla.firefox" || packageName.contains("browser")
        val isReliableBrowser = isBrowser // simplify for now

        val result = detector.detect(rootNode, packageName, config, isReliableBrowser)

        if (result.shouldBlock) {
            if (result.isWebCounterpart) {
                triggerWebCounterpartBlock(result.blockLabel, result.domain)
            } else {
                triggerSurgicalBlock(result.blockLabel)
            }
        }
    }

    override fun onInterrupt() {
        // Handle interrupt
    }

    private fun triggerSurgicalBlock(label: String) {
        Log.d("ShortsController", "Blocking $label")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
    }

    private fun triggerWebCounterpartBlock(appName: String, domain: String) {
        Log.d("ShortsController", "Web block for $appName ($domain)")
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        val intent = Intent(service, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("BLOCKED_APP_PACKAGE", "browser_bypass_$domain")
            putExtra("BLOCKED_APP_NAME", "$appName (Web)")
        }
        service.startActivity(intent)
    }
}
