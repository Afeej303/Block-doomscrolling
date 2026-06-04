package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "app_block_config")
data class AppBlockConfig(
    @PrimaryKey val packageName: String,
    val appName: String,
    val blockShortsReels: Boolean = true,
    val dailyLimitMinutes: Int = 0, // 0 = unlimited
    val timeUsedTodaySeconds: Long = 0,
    val lastUsedTimestamp: Long = 0,
    val isBlockedNow: Boolean = false
)
