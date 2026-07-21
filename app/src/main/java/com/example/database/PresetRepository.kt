package com.example.database

import kotlinx.coroutines.flow.Flow

class PresetRepository(private val customPresetDao: CustomPresetDao) {
    val allPresets: Flow<List<CustomPreset>> = customPresetDao.getAllPresets()

    suspend fun insert(preset: CustomPreset) {
        customPresetDao.insertPreset(preset)
    }

    suspend fun deleteByName(name: String) {
        customPresetDao.deletePresetByName(name)
    }
}
