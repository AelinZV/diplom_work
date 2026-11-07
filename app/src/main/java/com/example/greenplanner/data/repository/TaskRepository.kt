package com.example.greenplanner.data.repository

import com.example.greenplanner.data.local.TaskDao
import com.example.greenplanner.data.local.TaskEntity
import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    val tasks: Flow<List<TaskEntity>> = dao.observeTasks()

    suspend fun save(task: TaskEntity): Long = if (task.id == 0L) {
        dao.insert(task)
    } else {
        dao.update(task)
        task.id
    }

    suspend fun delete(task: TaskEntity) = dao.delete(task)
    suspend fun toggle(task: TaskEntity) = dao.update(task.copy(completed = !task.completed))
}
