package com.example.focus

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.MainActivity
import com.example.accessibility.AccessibilityEventListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

sealed class FocusState {
    object Inactive : FocusState()
    data class Running(val remainingSeconds: Int) : FocusState()
    object Paused : FocusState()
    object Finishing : FocusState()
}

class FocusManager(private val context: Context) : AccessibilityEventListener {
    
    private val _state = MutableStateFlow<FocusState>(FocusState.Inactive)
    val state: StateFlow<FocusState> = _state
    
    private var isSettingsLockedCached = false
    private var lastAppInfoBlockTime = 0L
    
    // In a real implementation, we'd depend on SettingsRepository
    fun updateSettingsLock(isLocked: Boolean) {
        isSettingsLockedCached = isLocked
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?, packageName: String) {
        if (rootNode == null) return
        
        // This is where we intercept app launches during an active Focus Session
        // For now, we migrate the "App Info / Device Admin block" logic here.
        scanAndBlockAppInfo(rootNode)
    }

    override fun onInterrupt() {
    }

    private fun scanAndBlockAppInfo(rootNode: AccessibilityNodeInfo) {
        if (!isSettingsLockedCached) return
        val now = System.currentTimeMillis()
        if (now - lastAppInfoBlockTime < 2000) return

        var hasZenScrollText = false
        var hasForceStop = false
        var hasUninstall = false
        var hasDeactivateButton = false
        var hasAdminPageTitle = false

        fun collect(node: AccessibilityNodeInfo?, depth: Int = 0) {
            if (node == null) return
            try {
                val text = node.text?.toString()?.lowercase() ?: ""
                val desc = node.contentDescription?.toString()?.lowercase() ?: ""
                val viewId = node.viewIdResourceName?.lowercase() ?: ""

                if (text.contains("zenscroll") || desc.contains("zenscroll")) hasZenScrollText = true
                if (text.contains("force stop") || desc.contains("force stop") || viewId.contains("force_stop")) hasForceStop = true
                if (text.contains("uninstall") || desc.contains("uninstall") || viewId.contains("uninstall")) hasUninstall = true
                if (text.contains("device admin") || desc.contains("device admin") || viewId.contains("device_admin")) hasAdminPageTitle = true
                if (text.contains("deactivate") || desc.contains("remove") || viewId.contains("deactivate")) hasDeactivateButton = true

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    collect(child, depth + 1)
                    child?.recycle()
                }
            } catch (e: Exception) {
                // Ignore
            }
        }

        collect(rootNode)

        val isAppInfoPage = hasZenScrollText && (hasForceStop || hasUninstall)
        val isDeviceAdminPage = (hasAdminPageTitle && hasDeactivateButton) || 
                                (hasZenScrollText && hasDeactivateButton) || 
                                (hasAdminPageTitle && hasZenScrollText)

        if (isAppInfoPage || isDeviceAdminPage) {
            lastAppInfoBlockTime = now
            
            // Perform global action BACK
            if (context is AccessibilityService) {
                context.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            }
            
            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("SHOW_UNLOCK_PROMPT", true)
            }
            context.startActivity(intent)
        }
    }
}
