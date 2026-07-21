package com.example.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomPresetDao {
    @Query("SELECT * FROM custom_presets ORDER BY name ASC")
    fun getAllPresets(): Flow<List<CustomPreset>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreset(preset: CustomPreset)

    @Query("DELETE FROM custom_presets WHERE name = :name")
    suspend fun deletePresetByName(name: String)
}
