package com.example.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class SleepNoiseService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Default + serviceJob)

    private var audioTrack: AudioTrack? = null
    private var playbackJob: Job? = null
    private var timerJob: Job? = null
    private val isAudioRunning = AtomicBoolean(false)

    private var targetMasterFade = 1f
    private var currentMasterFade = 0f

    private val synthesizer = SoundSynthesizer()
    private val binauralSynthesizer = BinauralSynthesizer()
    private val lucidCueSynthesizer = LucidDreamCueSynthesizer()
    private val soundTypes = SoundType.values()

    companion object {
        private const val TAG = "SleepNoiseService"
        private const val NOTIFICATION_ID = 8888
        private const val CHANNEL_ID = "sleep_noise_channel"

        const val ACTION_START = "com.example.audio.action.START"
        const val ACTION_PLAY = "com.example.audio.action.PLAY"
        const val ACTION_PAUSE = "com.example.audio.action.PAUSE"
        const val ACTION_STOP = "com.example.audio.action.STOP"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
        createNotificationChannel()
        SleepNoiseManager.initPreferences(applicationContext)
        
        // Observe playing state changes in the manager
        serviceScope.launch {
            SleepNoiseManager.isPlaying.collect { isPlaying ->
                if (isPlaying) {
                    startAudioPlayback()
                }
                updateNotification()
            }
        }

        // Observe TLR training state changes
        serviceScope.launch {
            SleepNoiseManager.isTlrActive.collect { isTlr ->
                if (isTlr) {
                    startAudioPlayback()
                }
                updateNotification()
            }
        }

        // Observe Lucid Dream Cue trigger events
        serviceScope.launch {
            SleepNoiseManager.lucidCueTriggerEvent.collect { timestamp ->
                if (timestamp > 0L) {
                    val cueType = SleepNoiseManager.lucidCueSoundType.value
                    lucidCueSynthesizer.triggerCue(cueType)
                    startAudioPlayback()
                    updateNotification()
                    if (SleepNoiseManager.isLucidCueTesting.value) {
                        delay(6500)
                        SleepNoiseManager.stopLucidCuePreview()
                        updateNotification()
                    }
                }
            }
        }

        // Start 1-second timer ticking
        timerJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                SleepNoiseManager.tickTimer()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(TAG, "onStartCommand action: $action")
        
        when (action) {
            ACTION_START -> {
                startForegroundService()
            }
            ACTION_PLAY -> {
                SleepNoiseManager.setPlaying(true)
            }
            ACTION_PAUSE -> {
                SleepNoiseManager.setPlaying(false)
            }
            ACTION_STOP -> {
                stopAndCleanUp()
            }
        }
        return START_STICKY
    }

    private fun startForegroundService() {
        val notification = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service", e)
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val activeMode = SleepNoiseManager.playingMode.value
        val isPlaying = SleepNoiseManager.isPlaying.value

        val title = if (isPlaying) {
            if (activeMode == "BINAURAL") {
                val themeName = when (SleepNoiseManager.binauralThemeId.value) {
                    1 -> "身心分离(出体训练)"
                    2 -> "抽离(解离训练)"
                    3 -> "辽阔(宏大感体验)"
                    4 -> "心流(专注写作)"
                    5 -> "意象流(半醒自由联想)"
                    6 -> "清明梦(REM期定时提示)"
                    else -> "双脑同步冥想"
                }
                "双脑同步：$themeName"
            } else {
                "深眠白噪音 正在播放中"
            }
        } else {
            if (activeMode == "BINAURAL") {
                "双脑同步冥想 已暂停"
            } else {
                "深眠白噪音 已暂停"
            }
        }
        
        val text = if (activeMode == "BINAURAL") {
            val mins = SleepNoiseManager.binauralDurationMinutes.value
            val currentSecs = SleepNoiseManager.binauralElapsedSeconds.value.toInt()
            val elapsedStr = String.format("%02d:%02d", currentSecs / 60, currentSecs % 60)
            if (mins == -1) {
                "当前时间: $elapsedStr (无限循环)"
            } else {
                val totalStr = String.format("%02d:00", mins)
                "冥想进度: $elapsedStr / $totalStr"
            }
        } else {
            val preset = SleepNoiseManager.activePreset.value
            if (SleepNoiseManager.isTlrActive.value) {
                val step = SleepNoiseManager.tlrStep.value
                "🎯 现实检验配对中 · 步骤 $step/3 (环境静音)"
            } else if (SleepNoiseManager.isLucidCueEnabled.value) {
                val elapsed = SleepNoiseManager.lucidCueElapsedSeconds.value
                val delaySec = SleepNoiseManager.lucidCueDelayMinutes.value * 60
                if (elapsed < delaySec) {
                    val rem = delaySec - elapsed
                    val m = rem / 60
                    val s = rem % 60
                    "$preset · 梦境提醒: 倒计时 ${m}分${s}秒"
                } else {
                    val count = SleepNoiseManager.lucidCueTriggerCount.value
                    "$preset · 清醒梦线索已提醒 $count 次"
                }
            } else {
                "当前混音模式: $preset"
            }
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Play/Pause Action Button
        val playPauseAction = if (isPlaying) {
            val pauseIntent = Intent(this, SleepNoiseService::class.java).apply { action = ACTION_PAUSE }
            val pausePendingIntent = PendingIntent.getService(
                this, 1, pauseIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_pause, "暂停", pausePendingIntent
            ).build()
        } else {
            val playIntent = Intent(this, SleepNoiseService::class.java).apply { action = ACTION_PLAY }
            val playPendingIntent = PendingIntent.getService(
                this, 2, playIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            NotificationCompat.Action.Builder(
                android.R.drawable.ic_media_play, "播放", playPendingIntent
            ).build()
        }

        // Close Action Button
        val stopIntent = Intent(this, SleepNoiseService::class.java).apply { action = ACTION_STOP }
        val stopPendingIntent = PendingIntent.getService(
            this, 3, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopAction = NotificationCompat.Action.Builder(
            android.R.drawable.ic_menu_close_clear_cancel, "退出", stopPendingIntent
        ).build()

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(isPlaying)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(playPauseAction)
            .addAction(stopAction)
            .setSilent(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "深眠白噪音服务"
            val descriptionText = "用于白噪音和双脑同步后台播放及定时控制"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    @Synchronized
    private fun startAudioPlayback() {
        if (isAudioRunning.get()) {
            return
        }
        isAudioRunning.set(true)
        currentMasterFade = 0f

        playbackJob = serviceScope.launch(Dispatchers.Default) {
            // Elevate thread priority for urgent real-time audio thread
            try {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to set urgent audio thread priority", e)
            }

            val sampleRate = 44100
            val bufferSize = AudioTrack.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            )

            try {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()

                if (track.state != AudioTrack.STATE_INITIALIZED) {
                    Log.e(TAG, "AudioTrack failed to initialize")
                    return@launch
                }

                audioTrack = track
                track.play()
                Log.d(TAG, "Stereo AudioTrack started playing")

                binauralSynthesizer.reset()
                binauralSynthesizer.setFade(true)

                var localElapsedSeconds = SleepNoiseManager.binauralElapsedSeconds.value
                val buffer = ShortArray(2048) // Interleaved stereo buffer
                val cueStereo = FloatArray(2) // Reusable stereo buffer for lucid dream cues
                val fadeStep = 1.0f / (sampleRate.toFloat() * 1.5f) // 1.5 seconds linear fade for background

                while (isActive && isAudioRunning.get()) {
                    val isCueActive = lucidCueSynthesizer.isActive
                    val isTlrActive = SleepNoiseManager.isTlrActive.value
                    val isTesting = SleepNoiseManager.isLucidCueTesting.value
                    val isBgPlaying = SleepNoiseManager.isPlaying.value && !isTlrActive && !isTesting
                    val targetBgFade = if (isBgPlaying) 1.0f else 0.0f

                    // Exit condition: if background stopped and faded out, and no cue/TLR/preview is running
                    val shouldAudioRun = isBgPlaying || isTlrActive || isTesting || isCueActive || (currentMasterFade > 0.001f)
                    if (!shouldAudioRun) {
                        break
                    }

                    val cueVolume = SleepNoiseManager.getEffectiveCueVolume()
                    val activeMode = SleepNoiseManager.playingMode.value

                    if (activeMode == "BINAURAL") {
                        val themeId = SleepNoiseManager.binauralThemeId.value
                        val managerElapsed = SleepNoiseManager.binauralElapsedSeconds.value

                        if (Math.abs(managerElapsed - localElapsedSeconds) > 1.5) {
                            localElapsedSeconds = managerElapsed
                        }

                        val duration = SleepNoiseManager.binauralDurationMinutes.value * 60.0
                        val promptEnabled = SleepNoiseManager.binauralSeparationPromptEnabled.value
                        val calVol = SleepNoiseManager.binauralCalibrationVolume.value
                        val alarmA = SleepNoiseManager.binauralAlarmTimeA.value
                        val alarmB = SleepNoiseManager.binauralAlarmTimeB.value

                        for (i in 0 until (buffer.size / 2)) {
                            if (currentMasterFade < targetBgFade) {
                                currentMasterFade = (currentMasterFade + fadeStep).coerceAtMost(targetBgFade)
                            } else if (currentMasterFade > targetBgFade) {
                                currentMasterFade = (currentMasterFade - fadeStep).coerceAtLeast(targetBgFade)
                            }

                            var sampleL = 0f
                            var sampleR = 0f
                            if (isBgPlaying && currentMasterFade > 0.001f) {
                                binauralSynthesizer.nextSample(
                                    themeId = themeId,
                                    elapsedSeconds = localElapsedSeconds,
                                    totalDurationSeconds = duration,
                                    promptEnabled = promptEnabled,
                                    calibrationVolume = calVol,
                                    alarmTimeA = alarmA,
                                    alarmTimeB = alarmB
                                )
                                sampleL = kotlin.math.tanh(binauralSynthesizer.outL) * currentMasterFade
                                sampleR = kotlin.math.tanh(binauralSynthesizer.outR) * currentMasterFade
                            }

                            // Mix in gentle Lucid Dream Cue without background master fade attenuation
                            if (isCueActive) {
                                lucidCueSynthesizer.nextStereoSample(cueStereo)
                                sampleL += cueStereo[0] * cueVolume
                                sampleR += cueStereo[1] * cueVolume
                            }

                            val limitedL = kotlin.math.tanh(sampleL.toDouble()).toFloat() * 0.95f
                            val limitedR = kotlin.math.tanh(sampleR.toDouble()).toFloat() * 0.95f

                            buffer[2 * i] = (limitedL * 32767f).toInt().toShort()
                            buffer[2 * i + 1] = (limitedR * 32767f).toInt().toShort()
                            localElapsedSeconds += 1.0 / sampleRate.toDouble()
                        }
                    } else {
                        // White noise: Dual Mono with zero-allocation cache loop + true stereo cues
                        val currentVolumes = SleepNoiseManager.volumes.value
                        for (i in 0 until (buffer.size / 2)) {
                            if (currentMasterFade < targetBgFade) {
                                currentMasterFade = (currentMasterFade + fadeStep).coerceAtMost(targetBgFade)
                            } else if (currentMasterFade > targetBgFade) {
                                currentMasterFade = (currentMasterFade - fadeStep).coerceAtLeast(targetBgFade)
                            }

                            var mixedSample = 0f
                            if (isBgPlaying && currentMasterFade > 0.001f) {
                                val size = soundTypes.size
                                for (j in 0 until size) {
                                    val type = soundTypes[j]
                                    val vol = currentVolumes[type] ?: 0f
                                    if (vol > 0.01f) {
                                        mixedSample += synthesizer.nextSample(type) * vol
                                    }
                                }
                            }

                            var sampleL = mixedSample * currentMasterFade
                            var sampleR = mixedSample * currentMasterFade

                            // Mix in gentle Lucid Dream Cue with immediate crisp attack (NO master fade attenuation)
                            if (isCueActive) {
                                lucidCueSynthesizer.nextStereoSample(cueStereo)
                                sampleL += cueStereo[0] * cueVolume
                                sampleR += cueStereo[1] * cueVolume
                            }

                            val limitedL = kotlin.math.tanh(sampleL.toDouble()).toFloat() * 0.95f
                            val limitedR = kotlin.math.tanh(sampleR.toDouble()).toFloat() * 0.95f

                            buffer[2 * i] = (limitedL * 32767f).toInt().toShort()
                            buffer[2 * i + 1] = (limitedR * 32767f).toInt().toShort()
                        }
                    }

                    try {
                        if (track.state == AudioTrack.STATE_INITIALIZED) {
                            track.write(buffer, 0, buffer.size)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "AudioTrack write failed", e)
                        break
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in audio synthesis loop", e)
            } finally {
                releaseAudioTrack()
                isAudioRunning.set(false)
                playbackJob = null
            }
        }
    }

    @Synchronized
    private fun stopAudioPlayback() {
        // Background target fade is smoothly handled in audio loop
    }

    private fun releaseAudioTrack() {
        try {
            audioTrack?.apply {
                if (state == AudioTrack.STATE_INITIALIZED) {
                    try { stop() } catch (ignored: Exception) {}
                }
                try { release() } catch (ignored: Exception) {}
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing AudioTrack", e)
        } finally {
            audioTrack = null
        }
    }

    private fun stopAndCleanUp() {
        Log.d(TAG, "Stopping service")
        SleepNoiseManager.setPlaying(false)
        SleepNoiseManager.stopTimer()
        SleepNoiseManager.cancelTlrTraining()
        SleepNoiseManager.stopLucidCuePreview()
        isAudioRunning.set(false)
        
        timerJob?.cancel()
        serviceJob.cancel()
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
        stopAndCleanUp()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
