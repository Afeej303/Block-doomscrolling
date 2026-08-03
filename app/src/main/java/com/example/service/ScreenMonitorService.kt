package com.example.service

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.example.accessibility.EventDispatcher
import com.example.data.BlockDatabase
import com.example.data.BlockRepository
import com.example.focus.FocusAccessibilityListener
import com.example.focus.FocusManager
import com.example.shorts.ShortsController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ScreenMonitorService : AccessibilityService() {

    private val dispatcher = EventDispatcher()
    private lateinit var focusListener: FocusAccessibilityListener
    private lateinit var shortsController: ShortsController
    private lateinit var repository: BlockRepository
    
    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d("ScreenMonitor", "Service Connected - Architectural Refactor")
        
        repository = BlockRepository(BlockDatabase.getDatabase(this).dao())
        
        focusListener = FocusAccessibilityListener(this)
        shortsController = ShortsController(this, repository)
        
        dispatcher.registerListener(focusListener)
        dispatcher.registerListener(shortsController)
        
        // Listen to security settings for FocusManager
        serviceScope.launch {
            repository.securitySettings.collect { settings ->
                val isLocked = settings?.isLockActive == true && settings.lockUntilTimestamp > System.currentTimeMillis()
                FocusManager.updateSettingsLock(isLocked)
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            event.eventType == AccessibilityEvent.TYPE_WINDOWS_CHANGED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
            
            val rootNode = rootInActiveWindow
            val packageName = event.packageName?.toString() ?: rootNode?.packageName?.toString() ?: return
            
            dispatcher.dispatchEvent(event, rootNode, packageName)
            
            rootNode?.recycle()
        }
    }

    override fun onInterrupt() {
        dispatcher.dispatchInterrupt()
    }
}
