package com.example.audio

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

object SleepNoiseManager {
    private const val LUCID_PREFS_NAME = "lucid_cue_prefs"
    private const val KEY_LUCID_ENABLED = "enabled"
    private const val KEY_LUCID_DELAY = "delay_minutes"
    private const val KEY_LUCID_SOUND = "sound_type"
    private const val KEY_LUCID_VOLUME = "volume"
    private const val KEY_LUCID_REPEAT = "repeat_minutes"
    private const val KEY_SESSION_ACTIVE = "session_active"
    private const val KEY_SESSION_ELAPSED = "session_elapsed"
    private const val KEY_SESSION_COUNT = "session_count"
    private const val KEY_SESSION_LAST_FIRED = "session_last_fired"
    private const val KEY_SESSION_PRESET = "session_preset"
    private const val KEY_SESSION_VOLUMES = "session_volumes"
    private const val SESSION_SAVE_INTERVAL_SECONDS = 30

    private var lucidPrefs: SharedPreferences? = null

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

    // 1: 现实检验双音钟, 2: 梦境水晶风铃, 3: 灵性八音盒, 4: 幽潭灵露, 5: 空灵颂钵
    private val _lucidCueSoundType = MutableStateFlow(1)
    val lucidCueSoundType: StateFlow<Int> = _lucidCueSoundType.asStateFlow()

    // Cue volume multiplier (0.1f ~ 0.9f, default 0.35f)
    private val _lucidCueVolume = MutableStateFlow(0.35f)
    val lucidCueVolume: StateFlow<Float> = _lucidCueVolume.asStateFlow()

    // Repeat interval after first trigger: 0 (once only), 15 (every 15 min), 20, 30
    private val _lucidCueRepeatIntervalMinutes = MutableStateFlow(15)
    val lucidCueRepeatIntervalMinutes: StateFlow<Int> = _lucidCueRepeatIntervalMinutes.asStateFlow()

    // One-shot trigger events for the audio synthesizer. A conflated channel (not a StateFlow) so a
    // (re)created service never replays an old trigger, while a preview sent just before the service
    // starts collecting is still delivered once.
    private val lucidCueTriggerChannel = Channel<Unit>(Channel.CONFLATED)
    val lucidCueTriggerEvents: Flow<Unit> = lucidCueTriggerChannel.receiveAsFlow()

    // Elapsed second at which the cue last sounded in this session (valid when trigger count > 0)
    private var lucidCueLastFiredAtSeconds = 0

    // Number of times cue has sounded during this sleep session
    private val _lucidCueTriggerCount = MutableStateFlow(0)
    val lucidCueTriggerCount: StateFlow<Int> = _lucidCueTriggerCount.asStateFlow()

    // Preview state (awake testing)
    private val _isLucidCueTesting = MutableStateFlow(false)
    val isLucidCueTesting: StateFlow<Boolean> = _isLucidCueTesting.asStateFlow()

    /**
     * Load persisted lucid cue settings. Safe to call more than once; only the first call has effect.
     * Call from both the Activity and the Service, since either may be the first to start the process.
     */
    fun init(context: Context) {
        if (lucidPrefs != null) return
        val prefs = context.applicationContext.getSharedPreferences(LUCID_PREFS_NAME, Context.MODE_PRIVATE)
        lucidPrefs = prefs
        _isLucidCueEnabled.value = prefs.getBoolean(KEY_LUCID_ENABLED, _isLucidCueEnabled.value)
        _lucidCueDelayMinutes.value = prefs.getInt(KEY_LUCID_DELAY, _lucidCueDelayMinutes.value).coerceIn(5, 240)
        _lucidCueSoundType.value = prefs.getInt(KEY_LUCID_SOUND, _lucidCueSoundType.value).coerceIn(1, 5)
        _lucidCueVolume.value = prefs.getFloat(KEY_LUCID_VOLUME, _lucidCueVolume.value).coerceIn(0.05f, 1.0f)
        _lucidCueRepeatIntervalMinutes.value =
            prefs.getInt(KEY_LUCID_REPEAT, _lucidCueRepeatIntervalMinutes.value).coerceAtLeast(0)
    }

    /**
     * If the process was killed in the middle of a lucid cue night (the service was restarted by
     * START_STICKY), restore the mix and cue progress and resume playback.
     * Returns true if a session was resumed.
     */
    fun restoreInterruptedLucidSession(): Boolean {
        val prefs = lucidPrefs ?: return false
        if (!prefs.getBoolean(KEY_SESSION_ACTIVE, false) || !_isLucidCueEnabled.value) return false

        val savedVolumes = prefs.getString(KEY_SESSION_VOLUMES, null)
        if (savedVolumes != null) {
            val parsed = savedVolumes.split(';').mapNotNull { entry ->
                val parts = entry.split('=')
                val type = SoundType.values().firstOrNull { it.name == parts.getOrNull(0) }
                val vol = parts.getOrNull(1)?.toFloatOrNull()
                if (type != null && vol != null) type to vol.coerceIn(0f, 1f) else null
            }.toMap()
            _volumes.value = SoundType.values().associate { it to (parsed[it] ?: 0.0f) }
        }
        prefs.getString(KEY_SESSION_PRESET, null)?.let { _activePreset.value = it }
        _lucidCueElapsedSeconds.value = prefs.getInt(KEY_SESSION_ELAPSED, 0).coerceAtLeast(0)
        _lucidCueTriggerCount.value = prefs.getInt(KEY_SESSION_COUNT, 0).coerceAtLeast(0)
        lucidCueLastFiredAtSeconds = prefs.getInt(KEY_SESSION_LAST_FIRED, 0).coerceAtLeast(0)

        setPlayingMode("WHITE_NOISE")
        setPlaying(true)
        return true
    }

    private fun saveLucidSettings() {
        lucidPrefs?.edit()
            ?.putBoolean(KEY_LUCID_ENABLED, _isLucidCueEnabled.value)
            ?.putInt(KEY_LUCID_DELAY, _lucidCueDelayMinutes.value)
            ?.putInt(KEY_LUCID_SOUND, _lucidCueSoundType.value)
            ?.putFloat(KEY_LUCID_VOLUME, _lucidCueVolume.value)
            ?.putInt(KEY_LUCID_REPEAT, _lucidCueRepeatIntervalMinutes.value)
            ?.apply()
    }

    private fun saveLucidSession() {
        val prefs = lucidPrefs ?: return
        val active = _isPlaying.value && _playingMode.value == "WHITE_NOISE" && _isLucidCueEnabled.value
        val editor = prefs.edit().putBoolean(KEY_SESSION_ACTIVE, active)
        if (active) {
            editor
                .putInt(KEY_SESSION_ELAPSED, _lucidCueElapsedSeconds.value)
                .putInt(KEY_SESSION_COUNT, _lucidCueTriggerCount.value)
                .putInt(KEY_SESSION_LAST_FIRED, lucidCueLastFiredAtSeconds)
                .putString(KEY_SESSION_PRESET, _activePreset.value)
                .putString(
                    KEY_SESSION_VOLUMES,
                    _volumes.value.entries.joinToString(";") { "${it.key.name}=${it.value}" }
                )
        }
        editor.apply()
    }

    fun setLucidCueEnabled(enabled: Boolean) {
        _isLucidCueEnabled.value = enabled
        if (!enabled) {
            _lucidCueElapsedSeconds.value = 0
            _lucidCueTriggerCount.value = 0
            lucidCueLastFiredAtSeconds = 0
        }
        saveLucidSettings()
        saveLucidSession()
    }

    fun setLucidCueDelayMinutes(mins: Int) {
        _lucidCueDelayMinutes.value = mins.coerceIn(5, 240)
        saveLucidSettings()
    }

    fun setLucidCueSoundType(type: Int) {
        _lucidCueSoundType.value = type.coerceIn(1, 5)
        saveLucidSettings()
    }

    fun setLucidCueVolume(vol: Float) {
        _lucidCueVolume.value = vol.coerceIn(0.05f, 1.0f)
        saveLucidSettings()
    }

    fun setLucidCueRepeatIntervalMinutes(mins: Int) {
        _lucidCueRepeatIntervalMinutes.value = mins.coerceAtLeast(0)
        saveLucidSettings()
    }

    fun resetLucidCueElapsed() {
        _lucidCueElapsedSeconds.value = 0
        _lucidCueTriggerCount.value = 0
        lucidCueLastFiredAtSeconds = 0
        saveLucidSession()
    }

    fun triggerLucidCuePreview() {
        _isLucidCueTesting.value = true
        lucidCueTriggerChannel.trySend(Unit)
    }

    /**
     * Elapsed second at which the next cue is due, or null if no more cues are scheduled.
     * Computed from the current settings, so changing the delay or repeat interval mid-session
     * reschedules instead of skipping cues (e.g. a delay shortened below the elapsed time fires
     * on the next tick rather than never).
     */
    private fun nextLucidCueAtSeconds(): Int? {
        return if (_lucidCueTriggerCount.value == 0) {
            _lucidCueDelayMinutes.value * 60
        } else {
            val repeatMinutes = _lucidCueRepeatIntervalMinutes.value
            if (repeatMinutes > 0) lucidCueLastFiredAtSeconds + repeatMinutes * 60 else null
        }
    }

    fun stopLucidCuePreview() {
        _isLucidCueTesting.value = false
    }

    fun setPlaying(playing: Boolean) {
        _isPlaying.value = playing
        saveLucidSession()
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
                    val elapsed = _lucidCueElapsedSeconds.value + 1
                    _lucidCueElapsedSeconds.value = elapsed
                    val nextCueAt = nextLucidCueAtSeconds()
                    if (nextCueAt != null && elapsed >= nextCueAt) {
                        lucidCueLastFiredAtSeconds = elapsed
                        _lucidCueTriggerCount.value += 1
                        lucidCueTriggerChannel.trySend(Unit)
                        saveLucidSession()
                    } else if (elapsed % SESSION_SAVE_INTERVAL_SECONDS == 0) {
                        saveLucidSession()
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
