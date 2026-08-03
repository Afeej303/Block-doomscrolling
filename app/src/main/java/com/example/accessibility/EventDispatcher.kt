package com.example.accessibility

import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

interface AccessibilityEventListener {
    fun onAccessibilityEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?, packageName: String)
    fun onInterrupt()
}

class EventDispatcher {
    private val listeners = mutableListOf<AccessibilityEventListener>()

    fun registerListener(listener: AccessibilityEventListener) {
        listeners.add(listener)
    }

    fun unregisterListener(listener: AccessibilityEventListener) {
        listeners.remove(listener)
    }

    fun dispatchEvent(event: AccessibilityEvent, rootNode: AccessibilityNodeInfo?, packageName: String) {
        for (listener in listeners) {
            listener.onAccessibilityEvent(event, rootNode, packageName)
        }
    }

    fun dispatchInterrupt() {
        for (listener in listeners) {
            listener.onInterrupt()
        }
    }
}
