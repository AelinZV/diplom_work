package com.example.greenplanner.ui.places

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.greenplanner.data.local.AppDatabase
import com.example.greenplanner.data.local.PlaceEntity
import com.example.greenplanner.data.repository.PlaceRepository
import com.example.greenplanner.location.GeofenceRegistrar
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlacesViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.get(application)
    private val repository = PlaceRepository(db.placeDao())
    private val reminders = db.locationReminderDao()
    private val geofences = GeofenceRegistrar(application)

    val places = repository.places.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun save(place: PlaceEntity) = viewModelScope.launch {
        repository.save(place)
        geofences.sync() // Изменение координат обновляет геозону.
    }

    fun delete(place: PlaceEntity) = viewModelScope.launch {
        reminders.deleteForPlace(place.id) // Удаляем привязки задач к удаляемому месту.
        repository.delete(place)
        geofences.sync()
    }
}
