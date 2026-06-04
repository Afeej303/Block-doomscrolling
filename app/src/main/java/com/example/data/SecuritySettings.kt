package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "security_settings")
data class SecuritySettings(
    @PrimaryKey val id: Int = 1,
    val isLockActive: Boolean = false,
    val lockUntilTimestamp: Long = 0,
    val passwordHash: String = ""
)
