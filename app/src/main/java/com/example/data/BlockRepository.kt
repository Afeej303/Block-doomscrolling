package com.example.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BlockRepository(private val dao: BlockDao) {

    val allConfigs: Flow<List<AppBlockConfig>> = dao.getAllConfigsFlow()
    val securitySettings: Flow<SecuritySettings?> = dao.getSecuritySettingsFlow()

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
    }

    suspend fun updateConfig(config: AppBlockConfig) {
        dao.insertOrUpdateConfig(config)
    }

    suspend fun updateSecurity(settings: SecuritySettings) {
        dao.insertOrUpdateSecurity(settings)
    }

    suspend fun incrementAppUsage(packageName: String, secondsPassed: Long): AppBlockConfig? {
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

        // Check if limit is active and exceeded
        val limitSeconds = config.dailyLimitMinutes * 60L
        val isBlockedNow = config.dailyLimitMinutes > 0 && newTimeToday >= limitSeconds

        val updated = config.copy(
            timeUsedTodaySeconds = newTimeToday,
            lastUsedTimestamp = now,
            isBlockedNow = isBlockedNow
        )
        dao.insertOrUpdateConfig(updated)
        return updated
    }

    suspend fun checkAndResetAppUsage(packageName: String): AppBlockConfig? {
        val config = dao.getConfigForApp(packageName) ?: return null
        val now = System.currentTimeMillis()
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val lastDay = sdf.format(Date(config.lastUsedTimestamp))
        val today = sdf.format(Date(now))
        
        if (lastDay != today && config.lastUsedTimestamp > 0) {
            val updated = config.copy(
                timeUsedTodaySeconds = 0,
                lastUsedTimestamp = now,
                isBlockedNow = false
            )
            dao.insertOrUpdateConfig(updated)
            return updated
        }
        return config
    }
}
