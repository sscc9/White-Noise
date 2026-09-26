package com.example.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

object SleepNoiseManager {
    private val managerScope = CoroutineScope(Dispatchers.Main.immediate + SupervisorJob())
    private var tlrJob: Job? = null

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
    private var prefs: SharedPreferences? = null

    private val _isLucidCueEnabled = MutableStateFlow(false)
    val isLucidCueEnabled: StateFlow<Boolean> = _isLucidCueEnabled.asStateFlow()

    // Delay in minutes until cue first triggers (default 45 min)
    private val _lucidCueDelayMinutes = MutableStateFlow(45)
    val lucidCueDelayMinutes: StateFlow<Int> = _lucidCueDelayMinutes.asStateFlow()

    // Elapsed seconds for current lucid cue cycle
    private val _lucidCueElapsedSeconds = MutableStateFlow(0)
    val lucidCueElapsedSeconds: StateFlow<Int> = _lucidCueElapsedSeconds.asStateFlow()

    // 1: 现实检验双音钟, 2: 梦境水晶风铃, 3: 灵性八音盒, 4: 幽潭灵露, 5: 传统空灵颂钵
    private val _lucidCueSoundType = MutableStateFlow(1)
    val lucidCueSoundType: StateFlow<Int> = _lucidCueSoundType.asStateFlow()

    // Cue volume multiplier (0.1f ~ 0.8f, default 0.35f)
    private val _lucidCueVolume = MutableStateFlow(0.35f)
    val lucidCueVolume: StateFlow<Float> = _lucidCueVolume.asStateFlow()

    // Progressive Volume Ladder: 1st trigger is soft (70%), 2nd is 85%, 3rd+ reaches full volume
    // Prevents startling the user on initial play while ensuring eventual penetration
    private val _isProgressiveVolume = MutableStateFlow(true)
    val isProgressiveVolume: StateFlow<Boolean> = _isProgressiveVolume.asStateFlow()

    // Repeat interval after first trigger: 0 (once only), 15 (every 15 min), 20, 30
    private val _lucidCueRepeatIntervalMinutes = MutableStateFlow(15)
    val lucidCueRepeatIntervalMinutes: StateFlow<Int> = _lucidCueRepeatIntervalMinutes.asStateFlow()

    // Max trigger count per sleep session: 1, 2, 3 (optimal WBTB REM window), 0 (continuous)
    private val _lucidCueMaxTriggers = MutableStateFlow(3)
    val lucidCueMaxTriggers: StateFlow<Int> = _lucidCueMaxTriggers.asStateFlow()

    // Event signal to trigger cue playback in audio synthesizer (SharedFlow replay=0 prevents false replay upon reconnection!)
    private val _lucidCueTriggerEvent = MutableSharedFlow<Long>(replay = 0, extraBufferCapacity = 1)
    val lucidCueTriggerEvent: SharedFlow<Long> = _lucidCueTriggerEvent.asSharedFlow()

    // Number of times cue has sounded during this sleep session
    private val _lucidCueTriggerCount = MutableStateFlow(0)
    val lucidCueTriggerCount: StateFlow<Int> = _lucidCueTriggerCount.asStateFlow()

    // Preview state (awake testing)
    private val _isLucidCueTesting = MutableStateFlow(false)
    val isLucidCueTesting: StateFlow<Boolean> = _isLucidCueTesting.asStateFlow()

    // --- Targeted Lucidity Reactivation (TLR) Awake Pairing Training ---
    private val _isTlrActive = MutableStateFlow(false)
    val isTlrActive: StateFlow<Boolean> = _isTlrActive.asStateFlow()

    // Step 0: idle, 1: Step 1 (Look at Hands), 2: Step 2 (Test Reality/Levitate), 3: Step 3 (Anchor Intent), 4: Finished
    private val _tlrStep = MutableStateFlow(0)
    val tlrStep: StateFlow<Int> = _tlrStep.asStateFlow()

    private val _tlrCountdown = MutableStateFlow(15)
    val tlrCountdown: StateFlow<Int> = _tlrCountdown.asStateFlow()

    fun initPreferences(context: Context) {
        if (prefs != null) return
        val p = context.getSharedPreferences("lucid_dream_prefs", Context.MODE_PRIVATE)
        prefs = p
        _isLucidCueEnabled.value = p.getBoolean("lucid_enabled", false)
        _lucidCueDelayMinutes.value = p.getInt("lucid_delay", 45)
        _lucidCueSoundType.value = p.getInt("lucid_sound_type", 1)
        _lucidCueVolume.value = p.getFloat("lucid_volume", 0.35f)
        _lucidCueRepeatIntervalMinutes.value = p.getInt("lucid_repeat_interval", 15)
        _isProgressiveVolume.value = p.getBoolean("lucid_progressive", true)
        _lucidCueMaxTriggers.value = p.getInt("lucid_max_triggers", 3)
    }

    private fun persist() {
        prefs?.edit()?.apply {
            putBoolean("lucid_enabled", _isLucidCueEnabled.value)
            putInt("lucid_delay", _lucidCueDelayMinutes.value)
            putInt("lucid_sound_type", _lucidCueSoundType.value)
            putFloat("lucid_volume", _lucidCueVolume.value)
            putInt("lucid_repeat_interval", _lucidCueRepeatIntervalMinutes.value)
            putBoolean("lucid_progressive", _isProgressiveVolume.value)
            putInt("lucid_max_triggers", _lucidCueMaxTriggers.value)
            apply()
        }
    }

    fun setLucidCueEnabled(enabled: Boolean) {
        _isLucidCueEnabled.value = enabled
        if (!enabled) {
            _lucidCueElapsedSeconds.value = 0
            _lucidCueTriggerCount.value = 0
        }
        persist()
    }

    fun setLucidCueDelayMinutes(mins: Int) {
        _lucidCueDelayMinutes.value = mins.coerceIn(5, 240)
        persist()
    }

    fun setLucidCueSoundType(type: Int) {
        _lucidCueSoundType.value = type
        persist()
    }

    fun setLucidCueVolume(vol: Float) {
        _lucidCueVolume.value = vol.coerceIn(0.05f, 1.0f)
        persist()
    }

    fun setProgressiveVolume(enabled: Boolean) {
        _isProgressiveVolume.value = enabled
        persist()
    }

    fun setLucidCueRepeatIntervalMinutes(mins: Int) {
        _lucidCueRepeatIntervalMinutes.value = mins
        persist()
    }

    fun setLucidCueMaxTriggers(max: Int) {
        _lucidCueMaxTriggers.value = max.coerceIn(1, 3)
        persist()
    }

    fun resetLucidCueElapsed() {
        _lucidCueElapsedSeconds.value = 0
        _lucidCueTriggerCount.value = 0
    }

    /**
     * Compute effective volume applying progressive escalation if enabled.
     */
    fun getEffectiveCueVolume(): Float {
        val base = _lucidCueVolume.value
        // During preview testing or TLR awake training, always play at full user volume!
        if (_isLucidCueTesting.value || _isTlrActive.value) return base
        if (!_isProgressiveVolume.value) return base

        val count = _lucidCueTriggerCount.value
        return when {
            count <= 1 -> (base * 0.70f).coerceAtLeast(0.12f)
            count == 2 -> (base * 0.85f).coerceAtLeast(0.18f)
            else -> base
        }
    }

    fun triggerLucidCuePreview() {
        _isLucidCueTesting.value = true
        _lucidCueTriggerEvent.tryEmit(System.currentTimeMillis())
    }

    fun stopLucidCuePreview() {
        _isLucidCueTesting.value = false
    }

    // TLR Pairing Training Actions
    fun startTlrTraining() {
        _isTlrActive.value = true
        _tlrStep.value = 1
        _tlrCountdown.value = 15
        _lucidCueTriggerEvent.tryEmit(System.currentTimeMillis())

        tlrJob?.cancel()
        tlrJob = managerScope.launch {
            while (_isTlrActive.value && _tlrStep.value in 1..3) {
                delay(1000)
                if (!_isTlrActive.value) break
                if (_tlrCountdown.value > 1) {
                    _tlrCountdown.value -= 1
                } else {
                    nextTlrStep()
                }
            }
        }
    }

    fun nextTlrStep() {
        val cur = _tlrStep.value
        if (cur in 1..2) {
            _tlrStep.value = cur + 1
            _tlrCountdown.value = 15
            _lucidCueTriggerEvent.tryEmit(System.currentTimeMillis())
        } else {
            _tlrStep.value = 4 // Completed all 3 steps
            _tlrCountdown.value = 0
            tlrJob?.cancel()
            tlrJob = null
        }
    }

    fun cancelTlrTraining() {
        _isTlrActive.value = false
        _tlrStep.value = 0
        _tlrCountdown.value = 15
        tlrJob?.cancel()
        tlrJob = null
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
                    // Enforce maximum safety trigger limit: 1, 2, or 3 times (default 3)
                    val maxLimit = _lucidCueMaxTriggers.value.coerceIn(1, 3)
                    val currentCount = _lucidCueTriggerCount.value

                    if (currentCount < maxLimit) {
                        if (elapsed == delaySec) {
                            _lucidCueTriggerEvent.tryEmit(System.currentTimeMillis())
                            _lucidCueTriggerCount.value += 1
                        } else if (elapsed > delaySec && maxLimit > 1) {
                            // Standard 15-minute REM sleep cycle interval
                            val repeatSec = 15 * 60
                            if ((elapsed - delaySec) % repeatSec == 0) {
                                _lucidCueTriggerEvent.tryEmit(System.currentTimeMillis())
                                _lucidCueTriggerCount.value += 1
                            }
                        }
                    }
                }

                // Handle white noise timer countdown
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
