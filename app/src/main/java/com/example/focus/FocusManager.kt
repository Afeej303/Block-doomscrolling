package com.example.focus

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.MainActivity
import com.example.overlay.FocusLockActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

sealed class FocusState {
    object Inactive : FocusState()
    data class Running(val remainingSeconds: Int) : FocusState()
    object Paused : FocusState()
    object Finishing : FocusState()
}

object FocusManager {
    private val _state = MutableStateFlow<FocusState>(FocusState.Inactive)
    val state: StateFlow<FocusState> = _state
    
    private var isSettingsLockedCached = false
    private var lastAppInfoBlockTime = 0L

    private val allowedPackages = setOf(
        "com.android.systemui",
        "com.google.android.permissioncontroller",
        "com.google.android.dialer",
        "com.android.server.telecom",
        "com.android.phone",
        "com.android.settings"
    )
    
    fun start(context: Context) {
        _state.value = FocusState.Running(0)
        launchFocusLockActivity(context)
    }
    
    fun stop() {
        _state.value = FocusState.Inactive
    }

    fun updateSettingsLock(isLocked: Boolean) {
        isSettingsLockedCached = isLocked
    }

    fun onAccessibilityEvent(context: Context, event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?, packageName: String) {
        // Enforce focus mode
        if (_state.value is FocusState.Running) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
                enforceFocus(context, packageName, event.className?.toString())
            }
        }
        
        if (rootNode != null) {
            scanAndBlockAppInfo(context, rootNode)
        }
    }

    private fun enforceFocus(context: Context, packageName: String, className: String?) {
        // Allow our own app, but ensure they don't navigate away from LockActivity
        if (packageName == context.packageName) {
            if (className != null && className.contains("FocusLockActivity")) {
                return // We are where we should be
            }
            // Block them from going to MainActivity or other parts of the app
            launchFocusLockActivity(context)
            return
        }
        
        // Allow system screens
        if (allowedPackages.contains(packageName)) {
            return
        }
        
        // For any other app, block it!
        Log.d("FocusManager", "Blocking app $packageName during focus session. Target class: $className")
        launchFocusLockActivity(context)
    }

    private fun launchFocusLockActivity(context: Context) {
        val intent = Intent(context, FocusLockActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }
    
    private fun scanAndBlockAppInfo(context: Context, rootNode: AccessibilityNodeInfo) {
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
