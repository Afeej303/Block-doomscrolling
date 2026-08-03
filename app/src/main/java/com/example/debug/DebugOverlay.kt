package com.example.debug

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class DebugOverlay(private val service: AccessibilityService) {
    private var windowManager: WindowManager? = null
    private var overlayView: LinearLayout? = null
    private var textPkg: TextView? = null
    private var textAct: TextView? = null
    private var textMatched: TextView? = null
    
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var isShowing = false

    init {
        windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        
        scope.launch {
            DebugManager.state.collect { state ->
                if (state.isEnabled && !isShowing) {
                    showOverlay()
                } else if (!state.isEnabled && isShowing) {
                    hideOverlay()
                }
                
                if (isShowing) {
                    textPkg?.text = "Package: ${state.currentPackage}"
                    textAct?.text = "Activity: ${state.currentActivity}"
                    textMatched?.text = "Matched: ${state.matchedRule}"
                }
            }
        }
    }
    
    private fun showOverlay() {
        if (overlayView == null) {
            overlayView = LinearLayout(service).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.parseColor("#CC000000"))
                setPadding(32, 32, 32, 32)
                
                textPkg = TextView(service).apply { setTextColor(Color.WHITE); textSize = 14f }
                textAct = TextView(service).apply { setTextColor(Color.WHITE); textSize = 14f }
                textMatched = TextView(service).apply { setTextColor(Color.WHITE); textSize = 14f }
                
                addView(textPkg)
                addView(textAct)
                addView(textMatched)
            }
        }
        
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            y = 200 // offset
        }
        
        try {
            windowManager?.addView(overlayView, params)
            isShowing = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
    
    private fun hideOverlay() {
        if (isShowing) {
            try {
                windowManager?.removeView(overlayView)
                isShowing = false
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
