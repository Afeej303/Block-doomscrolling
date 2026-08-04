package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_settings")
data class FocusSettings(
    @PrimaryKey val id: Int = 1,
    val isScheduleEnabled: Boolean = false,
    val startHour: Int = 9,
    val startMinute: Int = 0,
    val endHour: Int = 17,
    val endMinute: Int = 0,
    val allowedApps: String = "", // Comma-separated package names
    val schedules: String = "09:00-17:00" // Comma-separated time ranges: "HH:mm-HH:mm,HH:mm-HH:mm"
) {
    fun getScheduleList(): List<FocusScheduleItem> {
        val parsed = FocusScheduleItem.parseList(schedules)
        if (parsed.isNotEmpty()) return parsed
        return listOf(FocusScheduleItem(startHour, startMinute, endHour, endMinute))
    }
}

data class FocusScheduleItem(
    val startHour: Int,
    val startMinute: Int,
    val endHour: Int,
    val endMinute: Int
) {
    fun toRangeString(): String = String.format("%02d:%02d-%02d:%02d", startHour, startMinute, endHour, endMinute)

    companion object {
        fun fromRangeString(str: String): FocusScheduleItem? {
            val parts = str.split("-")
            if (parts.size != 2) return null
            val startParts = parts[0].split(":")
            val endParts = parts[1].split(":")
            if (startParts.size != 2 || endParts.size != 2) return null
            val sH = startParts[0].toIntOrNull() ?: return null
            val sM = startParts[1].toIntOrNull() ?: return null
            val eH = endParts[0].toIntOrNull() ?: return null
            val eM = endParts[1].toIntOrNull() ?: return null
            return FocusScheduleItem(sH, sM, eH, eM)
        }

        fun parseList(schedulesStr: String): List<FocusScheduleItem> {
            if (schedulesStr.isBlank()) return emptyList()
            return schedulesStr.split(",")
                .mapNotNull { fromRangeString(it.trim()) }
        }

        fun serializeList(list: List<FocusScheduleItem>): String {
            return list.joinToString(",") { it.toRangeString() }
        }
    }
}
