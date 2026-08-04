package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BlockRepository(private val dao: BlockDao) {

    private val dbMutex = Mutex()

    val allConfigs: Flow<List<AppBlockConfig>> = dao.getAllConfigsFlow()
    val securitySettings: Flow<SecuritySettings?> = dao.getSecuritySettingsFlow()
    val focusSettings: Flow<FocusSettings?> = dao.getFocusSettingsFlow()

    suspend fun initializeDefaultConfigsIfNeeded() {
        val currentConfigs = dao.getAllConfigs()
        if (currentConfigs.isEmpty()) {
            val defaults = listOf(
                AppBlockConfig("com.google.android.youtube", "YouTube", blockShortsReels = true, dailyLimitMinutes = 30),
                AppBlockConfig("com.instagram.android", "Instagram", blockShortsReels = true, dailyLimitMinutes = 30),
                AppBlockConfig("com.twitter.android", "X (Twitter)", blockShortsReels = true, dailyLimitMinutes = 30),
                AppBlockConfig("com.facebook.katana", "Facebook", blockShortsReels = true, dailyLimitMinutes = 30)
            )
            dao.insertOrUpdateConfigs(defaults)
        }
        val security = dao.getSecuritySettings()
        if (security == null) {
            dao.insertOrUpdateSecurity(SecuritySettings())
        }
        val focus = dao.getFocusSettings()
        if (focus == null) {
            dao.insertOrUpdateFocusSettings(FocusSettings())
        }
    }

    suspend fun updateConfig(config: AppBlockConfig) {
        dao.insertOrUpdateConfig(config)
    }

    suspend fun updateSecurity(settings: SecuritySettings) {
        dao.insertOrUpdateSecurity(settings)
    }

    suspend fun updateFocusSettings(settings: FocusSettings) {
        dao.insertOrUpdateFocusSettings(settings)
    }

    suspend fun incrementAppUsage(packageName: String, secondsPassed: Long, isShorts: Boolean = false): AppBlockConfig? = dbMutex.withLock {
        val config = dao.getConfigForApp(packageName) ?: return null
        val now = System.currentTimeMillis()
        
        // Reset checks: has day changed since last used?
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val lastDay = sdf.format(Date(config.lastUsedTimestamp))
        val today = sdf.format(Date(now))
        
        val newTimeToday = if (lastDay != today && config.lastUsedTimestamp > 0) {
            secondsPassed // Reset and set to elapsed
        } else {
            config.timeUsedTodaySeconds + secondsPassed
        }

        val newShortsTimeToday = if (lastDay != today && config.lastUsedTimestamp > 0) {
            if (isShorts) secondsPassed else 0L
        } else {
            config.shortsUsedTodaySeconds + (if (isShorts) secondsPassed else 0L)
        }
        
        // Check if limit is active and exceeded
        val limitSeconds = config.dailyLimitMinutes * 60L
        val isBlockedNow = config.dailyLimitMinutes > 0 && newTimeToday >= limitSeconds

        val shortsLimitSeconds = config.shortsLimitMinutes * 60L
        val isShortsBlockedNow = config.shortsLimitMinutes > 0 && newShortsTimeToday >= shortsLimitSeconds
        
        val updated = config.copy(
            timeUsedTodaySeconds = newTimeToday,
            shortsUsedTodaySeconds = newShortsTimeToday,
            lastUsedTimestamp = now,
            isBlockedNow = isBlockedNow,
            isShortsBlockedNow = isShortsBlockedNow
        )
        dao.insertOrUpdateConfig(updated)
        updated
    }

    suspend fun checkAndResetAppUsage(packageName: String): AppBlockConfig? = dbMutex.withLock {
        val config = dao.getConfigForApp(packageName) ?: return null
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val lastDay = sdf.format(Date(config.lastUsedTimestamp))
        val today = sdf.format(Date(now))
        
        if (lastDay != today && config.lastUsedTimestamp > 0) {
            val updated = config.copy(
                timeUsedTodaySeconds = 0,
                shortsUsedTodaySeconds = 0,
                lastUsedTimestamp = now,
                isBlockedNow = false,
                isShortsBlockedNow = false
            )
            dao.insertOrUpdateConfig(updated)
            return updated
        }
        config
    }
}
