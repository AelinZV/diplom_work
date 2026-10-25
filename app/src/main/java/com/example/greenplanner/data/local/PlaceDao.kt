package com.example.greenplanner.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places ORDER BY name COLLATE NOCASE ASC, id ASC")
    fun observePlaces(): Flow<List<PlaceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: PlaceEntity)

    @Update
    suspend fun update(place: PlaceEntity)

    @Delete
    suspend fun delete(place: PlaceEntity)
}
