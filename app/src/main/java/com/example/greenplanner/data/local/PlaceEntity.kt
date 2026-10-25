package com.example.greenplanner.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "places")
data class PlaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Int = 1000
)
