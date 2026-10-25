package com.example.greenplanner.ui.tasks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.greenplanner.data.local.AppDatabase
import com.example.greenplanner.data.local.TaskEntity
import com.example.greenplanner.data.repository.TaskRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class TaskViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = TaskRepository(AppDatabase.get(application).taskDao())

    val tasks = repository.tasks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(task: TaskEntity) = viewModelScope.launch { repository.save(task) }
    fun toggle(task: TaskEntity) = viewModelScope.launch { repository.toggle(task) }
    fun delete(task: TaskEntity) = viewModelScope.launch { repository.delete(task) }
}
