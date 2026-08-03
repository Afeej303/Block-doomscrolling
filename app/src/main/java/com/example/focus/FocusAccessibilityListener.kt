package com.example.focus

import android.content.Context
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.accessibility.AccessibilityEventListener

class FocusAccessibilityListener(private val context: Context) : AccessibilityEventListener {
    override fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?, packageName: String) {
        FocusManager.onAccessibilityEvent(context, event, rootNode, packageName)
    }

    override fun onInterrupt() {
    }
}
