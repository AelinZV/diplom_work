package com.example.greenplanner.ui.tasks

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.greenplanner.data.local.AppDatabase
import com.example.greenplanner.data.local.LocationReminderEntity
import com.example.greenplanner.data.local.TaskEntity
import com.example.greenplanner.data.repository.TaskRepository
import com.example.greenplanner.location.GeofenceRegistrar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val repository = TaskRepository(db.taskDao())
    private val reminderDao = db.locationReminderDao()
    private val geofences = GeofenceRegistrar(application)

    val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val reminders = reminderDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    var monitoringMessage by mutableStateOf("Геозоны пока не проверены")
        private set

    init { refreshGeofences() }

    fun save(task: TaskEntity, placeId: Long?, transition: Int) = viewModelScope.launch {
        val id = repository.save(task)
        if (placeId == null) reminderDao.deleteForTask(id)
        else reminderDao.upsert(LocationReminderEntity(taskId = id, placeId = placeId, transition = transition))
        monitoringMessage = geofences.sync()
    }

    fun toggle(task: TaskEntity) = viewModelScope.launch {
        repository.toggle(task)
        monitoringMessage = geofences.sync()
    }

    fun delete(task: TaskEntity) = viewModelScope.launch {
        reminderDao.deleteForTask(task.id)
        repository.delete(task)
        monitoringMessage = geofences.sync()
    }

    fun refreshGeofences() = viewModelScope.launch { monitoringMessage = geofences.sync() }
}
