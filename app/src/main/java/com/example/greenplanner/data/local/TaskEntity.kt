package com.example.greenplanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

// Категория и приоритет сохраняются прямо с задачей (без миграции справочников на данном этапе).
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val note: String = "",
    val date: String, // ISO-8601: yyyy-MM-dd
    val category: String = "Личное",
    val priority: Int = 1, // 0 — низкий, 1 — средний, 2 — высокий
    val completed: Boolean = false
)
