package com.example.greenplanner.ui.places

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.greenplanner.data.local.AppDatabase
import com.example.greenplanner.data.local.PlaceEntity
import com.example.greenplanner.data.repository.PlaceRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PlacesViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = PlaceRepository(AppDatabase.get(application).placeDao())

    val places = repository.places.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun save(place: PlaceEntity) = viewModelScope.launch { repository.save(place) }
    fun delete(place: PlaceEntity) = viewModelScope.launch { repository.delete(place) }
}
