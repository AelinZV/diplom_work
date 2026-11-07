package com.example.greenplanner.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LocationReminderDao {
    @Query("SELECT * FROM location_reminders")
    fun observeAll(): Flow<List<LocationReminderEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(reminder: LocationReminderEntity)

    @Query("DELETE FROM location_reminders WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)

    @Query("DELETE FROM location_reminders WHERE placeId = :placeId")
    suspend fun deleteForPlace(placeId: Long)

    @Query("""
        SELECT r.taskId, r.transition, p.latitude, p.longitude, p.radiusMeters
        FROM location_reminders r
        INNER JOIN tasks t ON t.id = r.taskId
        INNER JOIN places p ON p.id = r.placeId
        WHERE r.fired = 0 AND t.completed = 0
        ORDER BY r.taskId
    """)
    suspend fun activeTargets(): List<GeofenceTarget>

    /** Обновление атомарное: повторное событие не создаст ещё одно уведомление. */
    @Query("""
        UPDATE location_reminders SET fired = 1
        WHERE taskId = :taskId AND fired = 0 AND transition = :transition
        AND EXISTS (SELECT 1 FROM tasks WHERE id = :taskId AND completed = 0)
    """)
    suspend fun markFiredIfActive(taskId: Long, transition: Int): Int

    @Query("""
        SELECT t.title AS title, p.name AS placeName
        FROM location_reminders r
        INNER JOIN tasks t ON t.id = r.taskId
        INNER JOIN places p ON p.id = r.placeId
        WHERE r.taskId = :taskId
        LIMIT 1
    """)
    suspend fun notificationData(taskId: Long): ReminderNotificationData?
}
