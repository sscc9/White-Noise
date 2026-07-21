package com.example.database

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.audio.SoundType

@Entity(tableName = "custom_presets")
data class CustomPreset(
    @PrimaryKey val name: String,
    val whiteNoiseVol: Float = 0f,
    val rainVol: Float = 0f,
    val oceanVol: Float = 0f,
    val windVol: Float = 0f,
    val campfireVol: Float = 0f,
    val cricketsVol: Float = 0f,
    val singingBowlVol: Float = 0f,
    val streamVol: Float = 0f,
    val snowTeaVol: Float = 0f
) {
    fun toVolumesMap(): Map<SoundType, Float> {
        return mapOf(
            SoundType.WHITE_NOISE to whiteNoiseVol,
            SoundType.RAIN to rainVol,
            SoundType.OCEAN to oceanVol,
            SoundType.WIND to windVol,
            SoundType.CAMPFIRE to campfireVol,
            SoundType.CRICKETS to cricketsVol,
            SoundType.SINGING_BOWL to singingBowlVol,
            SoundType.STREAM to streamVol,
            SoundType.SNOW_TEA to snowTeaVol
        )
    }

    companion object {
        fun fromVolumesMap(name: String, map: Map<SoundType, Float>): CustomPreset {
            return CustomPreset(
                name = name,
                whiteNoiseVol = map[SoundType.WHITE_NOISE] ?: 0f,
                rainVol = map[SoundType.RAIN] ?: 0f,
                oceanVol = map[SoundType.OCEAN] ?: 0f,
                windVol = map[SoundType.WIND] ?: 0f,
                campfireVol = map[SoundType.CAMPFIRE] ?: 0f,
                cricketsVol = map[SoundType.CRICKETS] ?: 0f,
                singingBowlVol = map[SoundType.SINGING_BOWL] ?: 0f,
                streamVol = map[SoundType.STREAM] ?: 0f,
                snowTeaVol = map[SoundType.SNOW_TEA] ?: 0f
            )
        }
    }
}
