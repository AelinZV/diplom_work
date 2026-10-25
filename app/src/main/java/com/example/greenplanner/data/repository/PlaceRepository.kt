package com.example.greenplanner.data.repository

import com.example.greenplanner.data.local.PlaceDao
import com.example.greenplanner.data.local.PlaceEntity

class PlaceRepository(private val dao: PlaceDao) {
    val places = dao.observePlaces()

    suspend fun save(place: PlaceEntity) {
        if (place.id == 0L) dao.insert(place) else dao.update(place)
    }

    suspend fun delete(place: PlaceEntity) = dao.delete(place)
}
