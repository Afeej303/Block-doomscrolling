package com.example.service

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.MainActivity
import com.example.data.BlockRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object UsageTracker {
    private var currentPackage: String = ""
    private var isShortsActive: Boolean = false
    private var isTracking: Boolean = false
    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private var repository: BlockRepository? = null
    private var context: Context? = null
    private var onShortsBlock: (() -> Unit)? = null

    fun initialize(ctx: Context, repo: BlockRepository, onShortsLimitReached: () -> Unit) {
        context = ctx.applicationContext
        repository = repo
        onShortsBlock = onShortsLimitReached
        if (!isTracking) {
            isTracking = true
            startTracking()
        }
    }

    fun updateState(packageName: String, isShorts: Boolean) {
        currentPackage = packageName
        isShortsActive = isShorts
    }

    private fun startTracking() {
        scope.launch {
            while (isTracking) {
                val pkg = currentPackage
                val shorts = isShortsActive
                val repo = repository
                
                if (pkg.isNotEmpty() && repo != null) {
                    val updatedConfig = repo.incrementAppUsage(pkg, 1, shorts)
                    if (updatedConfig != null) {
                        if (updatedConfig.isBlockedNow) {
                            // Wholesale App Block
                            val intent = Intent(context, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                                putExtra("BLOCKED_APP_PACKAGE", updatedConfig.packageName)
                                putExtra("BLOCKED_APP_NAME", updatedConfig.appName)
                            }
                            context?.startActivity(intent)
                            
                            currentPackage = ""
                            isShortsActive = false
                        } else if (updatedConfig.isShortsBlockedNow && shorts) {
                            // Surgical Shorts Block
                            onShortsBlock?.invoke()
                            isShortsActive = false // Reset so it doesn't spam back presses
                        }
                    }
                }
                delay(1000)
            }
        }
    }
}
