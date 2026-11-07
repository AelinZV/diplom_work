package com.example.greenplanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Одна геозависимая настройка на задачу; после срабатывания fired = true. */
@Entity(tableName = "location_reminders")
data class LocationReminderEntity(
    @PrimaryKey val taskId: Long,
    val placeId: Long,
    val transition: Int = 1, // 1 = вход, 2 = выход
    val fired: Boolean = false
)

data class GeofenceTarget(
    val taskId: Long,
    val transition: Int,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int
)

data class ReminderNotificationData(val title: String, val placeName: String)
