package com.example.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SleepNoiseManager {
    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    fun getPresetVolumes(presetName: String): Map<SoundType, Float> {
        val presetMap = when (presetName) {
            "雨夜安眠" -> mapOf(
                SoundType.WHITE_NOISE to 0.0f,
                SoundType.RAIN to 0.8f,
                SoundType.OCEAN to 0.0f,
                SoundType.WIND to 0.0f,
                SoundType.CAMPFIRE to 0.0f,
                SoundType.CRICKETS to 0.0f,
                SoundType.SINGING_BOWL to 0.0f,
                SoundType.STREAM to 0.0f,
                SoundType.SNOW_TEA to 0.0f
            )
            "旷野篝火" -> mapOf(
                SoundType.WHITE_NOISE to 0.0f,
                SoundType.RAIN to 0.0f,
                SoundType.OCEAN to 0.0f,
                SoundType.WIND to 0.3f,
                SoundType.CAMPFIRE to 0.7f,
                SoundType.CRICKETS to 0.4f,
                SoundType.SINGING_BOWL to 0.0f,
                SoundType.STREAM to 0.0f,
                SoundType.SNOW_TEA to 0.0f
            )
            "静水灵心" -> mapOf(
                SoundType.WHITE_NOISE to 0.0f,
                SoundType.RAIN to 0.1f,
                SoundType.OCEAN to 0.0f,
                SoundType.WIND to 0.0f,
                SoundType.CAMPFIRE to 0.0f,
                SoundType.CRICKETS to 0.0f,
                SoundType.SINGING_BOWL to 0.6f,
                SoundType.STREAM to 0.5f,
                SoundType.SNOW_TEA to 0.0f
            )
            "深海奇遇" -> mapOf(
                SoundType.WHITE_NOISE to 0.0f,
                SoundType.RAIN to 0.0f,
                SoundType.OCEAN to 0.8f,
                SoundType.WIND to 0.0f,
                SoundType.CAMPFIRE to 0.0f,
                SoundType.CRICKETS to 0.0f,
                SoundType.SINGING_BOWL to 0.3f,
                SoundType.STREAM to 0.0f,
                SoundType.SNOW_TEA to 0.0f
            )
            "红泥煮雪" -> mapOf(
                SoundType.WHITE_NOISE to 0.0f,
                SoundType.RAIN to 0.0f,
                SoundType.OCEAN to 0.0f,
                SoundType.WIND to 0.2f,
                SoundType.CAMPFIRE to 0.0f,
                SoundType.CRICKETS to 0.0f,
                SoundType.SINGING_BOWL to 0.0f,
                SoundType.STREAM to 0.0f,
                SoundType.SNOW_TEA to 0.8f
            )
            else -> emptyMap()
        }
        return SoundType.values().associate { it to (presetMap[it] ?: 0.0f) }
    }

    private val _volumes = MutableStateFlow<Map<SoundType, Float>>(
        getPresetVolumes("雨夜安眠")
    )
    val volumes: StateFlow<Map<SoundType, Float>> = _volumes.asStateFlow()

    private val _timerTotalMinutes = MutableStateFlow(0) // 0 means no timer
    val timerTotalMinutes: StateFlow<Int> = _timerTotalMinutes.asStateFlow()

    private val _timerRemainingSeconds = MutableStateFlow(0)
    val timerRemainingSeconds: StateFlow<Int> = _timerRemainingSeconds.asStateFlow()

    private val _isTimerActive = MutableStateFlow(false)
    val isTimerActive: StateFlow<Boolean> = _isTimerActive.asStateFlow()

    private val _activePreset = MutableStateFlow("雨夜安眠")
    val activePreset: StateFlow<String> = _activePreset.asStateFlow()

    // --- Playing Mode and Viewed UI Mode ---
    private val _playingMode = MutableStateFlow("WHITE_NOISE") // "WHITE_NOISE" or "BINAURAL"
    val playingMode: StateFlow<String> = _playingMode.asStateFlow()

    // --- Binaural Beats Meditation Mode States ---
    private val _activeMode = MutableStateFlow("WHITE_NOISE") // "WHITE_NOISE" or "BINAURAL"
    val activeMode: StateFlow<String> = _activeMode.asStateFlow()

    private val _binauralThemeId = MutableStateFlow(1)
    val binauralThemeId: StateFlow<Int> = _binauralThemeId.asStateFlow()

    private val _binauralDurationMinutes = MutableStateFlow(60)
    val binauralDurationMinutes: StateFlow<Int> = _binauralDurationMinutes.asStateFlow()

    private val _binauralElapsedSeconds = MutableStateFlow(0.0)
    val binauralElapsedSeconds: StateFlow<Double> = _binauralElapsedSeconds.asStateFlow()

    private val _binauralSeparationPromptEnabled = MutableStateFlow(true)
    val binauralSeparationPromptEnabled: StateFlow<Boolean> = _binauralSeparationPromptEnabled.asStateFlow()

    private val _binauralCalibrationVolume = MutableStateFlow(0.25f)
    val binauralCalibrationVolume: StateFlow<Float> = _binauralCalibrationVolume.asStateFlow()

    private val _binauralAlarmTimeA = MutableStateFlow(4.5f) // hours
    val binauralAlarmTimeA: StateFlow<Float> = _binauralAlarmTimeA.asStateFlow()

    private val _binauralAlarmTimeB = MutableStateFlow(6.0f) // hours
    val binauralAlarmTimeB: StateFlow<Float> = _binauralAlarmTimeB.asStateFlow()

    // --- Lucid Dream Cue (WBTB Reality Check in White Noise) ---
    private val _isLucidCueEnabled = MutableStateFlow(false)
    val isLucidCueEnabled: StateFlow<Boolean> = _isLucidCueEnabled.asStateFlow()

    // Delay in minutes until cue first triggers (default 45 min - optimal scientific sweet spot for WBTB)
    private val _lucidCueDelayMinutes = MutableStateFlow(45)
    val lucidCueDelayMinutes: StateFlow<Int> = _lucidCueDelayMinutes.asStateFlow()

    // Elapsed seconds for current lucid cue cycle
    private val _lucidCueElapsedSeconds = MutableStateFlow(0)
    val lucidCueElapsedSeconds: StateFlow<Int> = _lucidCueElapsedSeconds.asStateFlow()

    // 1: 藏地磬钵, 2: 现实检验双音钟, 3: 幽潭灵露, 4: 潜意识和弦
    private val _lucidCueSoundType = MutableStateFlow(1)
    val lucidCueSoundType: StateFlow<Int> = _lucidCueSoundType.asStateFlow()

    // Cue volume multiplier (0.1f ~ 0.9f, default 0.35f)
    private val _lucidCueVolume = MutableStateFlow(0.35f)
    val lucidCueVolume: StateFlow<Float> = _lucidCueVolume.asStateFlow()

    // Repeat interval after first trigger: 0 (once only), 15 (every 15 min), 20, 30
    private val _lucidCueRepeatIntervalMinutes = MutableStateFlow(15)
    val lucidCueRepeatIntervalMinutes: StateFlow<Int> = _lucidCueRepeatIntervalMinutes.asStateFlow()

    // Event signal to trigger cue playback in audio synthesizer
    private val _lucidCueTriggerEvent = MutableStateFlow(0L)
    val lucidCueTriggerEvent: StateFlow<Long> = _lucidCueTriggerEvent.asStateFlow()

    // Number of times cue has sounded during this sleep session
    private val _lucidCueTriggerCount = MutableStateFlow(0)
    val lucidCueTriggerCount: StateFlow<Int> = _lucidCueTriggerCount.asStateFlow()

    // Preview state (awake testing)
    private val _isLucidCueTesting = MutableStateFlow(false)
    val isLucidCueTesting: StateFlow<Boolean> = _isLucidCueTesting.asStateFlow()

    fun setLucidCueEnabled(enabled: Boolean) {
        _isLucidCueEnabled.value = enabled
        if (!enabled) {
            _lucidCueElapsedSeconds.value = 0
            _lucidCueTriggerCount.value = 0
        }
    }

    fun setLucidCueDelayMinutes(mins: Int) {
        _lucidCueDelayMinutes.value = mins.coerceIn(5, 240)
    }

    fun setLucidCueSoundType(type: Int) {
        _lucidCueSoundType.value = type
    }

    fun setLucidCueVolume(vol: Float) {
        _lucidCueVolume.value = vol.coerceIn(0.05f, 1.0f)
    }

    fun setLucidCueRepeatIntervalMinutes(mins: Int) {
        _lucidCueRepeatIntervalMinutes.value = mins
    }

    fun resetLucidCueElapsed() {
        _lucidCueElapsedSeconds.value = 0
        _lucidCueTriggerCount.value = 0
    }

    fun triggerLucidCuePreview() {
        _isLucidCueTesting.value = true
        _lucidCueTriggerEvent.value = System.currentTimeMillis()
    }

    fun stopLucidCuePreview() {
        _isLucidCueTesting.value = false
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
    }

    fun setPlayingMode(mode: String) {
        _playingMode.value = mode
        if (mode == "BINAURAL") {
            // Stop any white noise timer
            stopTimer()
        }
    }

    fun setActiveMode(mode: String) {
        _activeMode.value = mode
    }

    fun setBinauralThemeId(id: Int) {
        _binauralThemeId.value = id
        _binauralDurationMinutes.value = when (id) {
            1 -> 60
            2 -> 45
            3 -> 30
            4 -> -1 // Infinite loop
            5 -> 40
            6 -> 480 // 8 hours
            else -> 60
        }
        _binauralElapsedSeconds.value = 0.0
    }

    fun setBinauralDurationMinutes(mins: Int) {
        _binauralDurationMinutes.value = mins
    }

    fun setBinauralElapsedSeconds(secs: Double) {
        _binauralElapsedSeconds.value = secs
    }

    fun setSeparationPromptEnabled(enabled: Boolean) {
        _binauralSeparationPromptEnabled.value = enabled
    }

    fun setBinauralCalibrationVolume(vol: Float) {
        _binauralCalibrationVolume.value = vol.coerceIn(0f, 1f)
    }

    fun setBinauralAlarmTimeA(hours: Float) {
        _binauralAlarmTimeA.value = hours.coerceIn(0f, 24f)
    }

    fun setBinauralAlarmTimeB(hours: Float) {
        _binauralAlarmTimeB.value = hours.coerceIn(0f, 24f)
    }

    fun setVolume(type: SoundType, volume: Float) {
        val current = _volumes.value.toMutableMap()
        current[type] = volume.coerceIn(0f, 1f)
        _volumes.value = current
        _activePreset.value = "自定义"
    }

    fun applyPreset(presetName: String, customVolumes: Map<SoundType, Float>? = null) {
        _activePreset.value = presetName
        if (customVolumes != null) {
            _volumes.value = SoundType.values().associate { it to (customVolumes[it] ?: 0.0f) }
            return
        }
        _volumes.value = getPresetVolumes(presetName)
    }

    fun startTimer(minutes: Int) {
        _timerTotalMinutes.value = minutes
        _timerRemainingSeconds.value = minutes * 60
        _isTimerActive.value = minutes > 0
    }

    fun stopTimer() {
        _timerTotalMinutes.value = 0
        _timerRemainingSeconds.value = 0
        _isTimerActive.value = false
    }

    fun tickTimer() {
        if (_isPlaying.value) {
            if (_playingMode.value == "BINAURAL") {
                val current = _binauralElapsedSeconds.value
                val maxSec = _binauralDurationMinutes.value * 60.0
                if (_binauralDurationMinutes.value == -1 || current < maxSec) {
                    _binauralElapsedSeconds.value = current + 1.0
                } else {
                    _binauralElapsedSeconds.value = maxSec
                    _isPlaying.value = false // session completed
                }
            } else {
                // White noise mode
                // 1. Handle Lucid Dream Cue progression if enabled
                if (_isLucidCueEnabled.value) {
                    _lucidCueElapsedSeconds.value += 1
                    val elapsed = _lucidCueElapsedSeconds.value
                    val delaySec = _lucidCueDelayMinutes.value * 60
                    if (elapsed == delaySec) {
                        _lucidCueTriggerEvent.value = System.currentTimeMillis()
                        _lucidCueTriggerCount.value += 1
                    } else if (elapsed > delaySec && _lucidCueRepeatIntervalMinutes.value > 0) {
                        val repeatSec = _lucidCueRepeatIntervalMinutes.value * 60
                        if ((elapsed - delaySec) % repeatSec == 0) {
                            _lucidCueTriggerEvent.value = System.currentTimeMillis()
                            _lucidCueTriggerCount.value += 1
                        }
                    }
                }

                // 2. Handle white noise timer countdown
                if (_isTimerActive.value) {
                    val remaining = _timerRemainingSeconds.value
                    if (remaining > 1) {
                        _timerRemainingSeconds.value = remaining - 1
                    } else {
                        _timerRemainingSeconds.value = 0
                        _isTimerActive.value = false
                        // If lucid cue is active, keep background playback alive so the dream cue will sound!
                        if (!_isLucidCueEnabled.value) {
                            _isPlaying.value = false
                        }
                    }
                }
            }
        }
    }
}
