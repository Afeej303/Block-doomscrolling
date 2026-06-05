package com.example.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.example.service.ScreenMonitorService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.UnsupportedEncodingException
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException

class BlockViewModel(application: Application) : AndroidViewModel(application) {

    private val db = BlockDatabase.getDatabase(application)
    private val repository = BlockRepository(db.dao())

    val appConfigs: StateFlow<List<AppBlockConfig>> = repository.allConfigs
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val securitySettings: StateFlow<SecuritySettings?> = repository.securitySettings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val selectedTab = MutableStateFlow(0) // 0: Dashboard, 1: Block Rules, 2: Security Lock
    val blockedAppNotification = MutableStateFlow<Pair<String, String>?>(null) // (PackageName, AppName)

    init {
        viewModelScope.launch(Dispatchers.IO) {
            repository.initializeDefaultConfigsIfNeeded()
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val expectedComponentName = android.content.ComponentName(context, ScreenMonitorService::class.java)
        val enabledServicesSetting = android.provider.Settings.Secure.getString(
            context.contentResolver,
            android.provider.Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = android.text.TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServicesSetting)
        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledService = android.content.ComponentName.unflattenFromString(componentNameString)
            if (enabledService != null && enabledService == expectedComponentName) {
                return true
            }
        }
        return false
    }

    fun updateSurgicalRule(config: AppBlockConfig, enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isConfigurationLocked()) return@launch
            repository.updateConfig(config.copy(blockShortsReels = enabled))
        }
    }

    fun updateDailyLimit(config: AppBlockConfig, minutes: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isConfigurationLocked()) return@launch
            val safeMinutes = minutes.coerceIn(0, 1440)
            repository.updateConfig(config.copy(dailyLimitMinutes = safeMinutes))
        }
    }

    fun isConfigurationLocked(): Boolean {
        val sec = securitySettings.value ?: return false
        return sec.isLockActive && sec.lockUntilTimestamp > System.currentTimeMillis()
    }

    fun getLockRemainingTimeMs(): Long {
        val sec = securitySettings.value ?: return 0L
        if (!sec.isLockActive) return 0L
        val diff = sec.lockUntilTimestamp - System.currentTimeMillis()
        return if (diff > 0) diff else 0L
    }

    fun tryToUnlockSettings(password: String): Boolean {
        val sec = securitySettings.value ?: return false
        val hashed = hashPassword(password)
        if (hashed == sec.passwordHash) {
            viewModelScope.launch(Dispatchers.IO) {
                repository.updateSecurity(
                    sec.copy(
                        isLockActive = false,
                        lockUntilTimestamp = 0
                    )
                )
            }
            return true
        }
        return false
    }

    fun setLockSettings(password: String, durationDays: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            val sec = securitySettings.value ?: SecuritySettings()
            val hashed = hashPassword(password)
            val durationMs = durationDays * 24 * 60 * 60 * 1000L
            val lockUntil = System.currentTimeMillis() + durationMs
            
            repository.updateSecurity(
                sec.copy(
                    isLockActive = true,
                    lockUntilTimestamp = lockUntil,
                    passwordHash = hashed
                )
            )
        }
    }

    fun forceResetUsageForTesting(config: AppBlockConfig) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isConfigurationLocked()) return@launch
            val limitSeconds = config.dailyLimitMinutes * 60L
            val isBlocked = config.dailyLimitMinutes > 0 && config.timeUsedTodaySeconds >= limitSeconds
            repository.updateConfig(
                config.copy(
                    isBlockedNow = isBlocked
                )
            )
            if (isBlocked) {
                blockedAppNotification.value = Pair(config.packageName, config.appName)
            } else if (config.timeUsedTodaySeconds == 0L) {
                val current = blockedAppNotification.value
                if (current != null && current.first == config.packageName) {
                    blockedAppNotification.value = null
                }
            }
        }
    }

    private fun hashPassword(password: String): String {
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val bytes = digest.digest(password.toByteArray(charset("UTF-8")))
            return bytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            throw RuntimeException("SHA-256 encryption failed: ${e.message}", e)
        }
    }
}
