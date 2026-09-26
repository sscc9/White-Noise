package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.audio.SleepNoiseManager
import com.example.audio.SleepNoiseService
import com.example.audio.SoundType
import com.example.database.CustomPreset
import com.example.database.SleepDatabase
import com.example.database.PresetRepository
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SleepNoiseScreen(
                        modifier = Modifier.fillMaxSize(),
                        innerPadding = innerPadding,
                        onSendAction = { action -> sendServiceAction(action) }
                    )
                }
            }
        }
    }

    private fun sendServiceAction(action: String) {
        val intent = Intent(this, SleepNoiseService::class.java).apply {
            this.action = action
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to send action to service", e)
        }
    }
}

@Composable
fun SleepNoiseScreen(
    modifier: Modifier = Modifier,
    innerPadding: PaddingValues,
    onSendAction: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Observe player states from SleepNoiseManager
    val isPlaying by SleepNoiseManager.isPlaying.collectAsState()
    val volumes by SleepNoiseManager.volumes.collectAsState()
    val timerTotalMinutes by SleepNoiseManager.timerTotalMinutes.collectAsState()
    val timerRemainingSeconds by SleepNoiseManager.timerRemainingSeconds.collectAsState()
    val isTimerActive by SleepNoiseManager.isTimerActive.collectAsState()
    val activePreset by SleepNoiseManager.activePreset.collectAsState()

    val database = remember { SleepDatabase.getDatabase(context) }
    val repository = remember { PresetRepository(database.customPresetDao()) }
    val customPresets by repository.allPresets.collectAsState(initial = emptyList())

    var showSaveDialog by remember { mutableStateOf(false) }
    var newPresetName by remember { mutableStateOf("") }

    // Observe Binaural Beats states from SleepNoiseManager
    val activeMode by SleepNoiseManager.activeMode.collectAsState()
    val playingMode by SleepNoiseManager.playingMode.collectAsState()
    val binauralThemeId by SleepNoiseManager.binauralThemeId.collectAsState()
    val binauralDurationMinutes by SleepNoiseManager.binauralDurationMinutes.collectAsState()
    val binauralElapsedSeconds by SleepNoiseManager.binauralElapsedSeconds.collectAsState()
    val binauralSeparationPromptEnabled by SleepNoiseManager.binauralSeparationPromptEnabled.collectAsState()
    val binauralCalibrationVolume by SleepNoiseManager.binauralCalibrationVolume.collectAsState()
    val binauralAlarmTimeA by SleepNoiseManager.binauralAlarmTimeA.collectAsState()
    val binauralAlarmTimeB by SleepNoiseManager.binauralAlarmTimeB.collectAsState()

    // Observe Lucid Dream Cue (WBTB) states from SleepNoiseManager
    val isLucidCueEnabled by SleepNoiseManager.isLucidCueEnabled.collectAsState()
    val lucidCueDelayMinutes by SleepNoiseManager.lucidCueDelayMinutes.collectAsState()
    val lucidCueElapsedSeconds by SleepNoiseManager.lucidCueElapsedSeconds.collectAsState()
    val lucidCueSoundType by SleepNoiseManager.lucidCueSoundType.collectAsState()
    val lucidCueVolume by SleepNoiseManager.lucidCueVolume.collectAsState()
    val isProgressiveVolume by SleepNoiseManager.isProgressiveVolume.collectAsState()
    val lucidCueMaxTriggers by SleepNoiseManager.lucidCueMaxTriggers.collectAsState()
    val isLucidCueTesting by SleepNoiseManager.isLucidCueTesting.collectAsState()
    val lucidCueTriggerCount by SleepNoiseManager.lucidCueTriggerCount.collectAsState()
    val isTlrActive by SleepNoiseManager.isTlrActive.collectAsState()
    val tlrStep by SleepNoiseManager.tlrStep.collectAsState()
    val tlrCountdown by SleepNoiseManager.tlrCountdown.collectAsState()

    var isCalibrating by remember { mutableStateOf(false) }

    // Request notification permission on Android 13+
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    LaunchedEffect(Unit) {
        SleepNoiseManager.initPreferences(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        // Send initial action to pre-initialize service
        onSendAction(SleepNoiseService.ACTION_START)
    }

    // Generate random fixed star offsets for the canvas background
    val stars = remember {
        List(45) {
            Offset(
                x = Random.nextFloat(),
                y = Random.nextFloat()
            )
        }
    }

    // Star twinkling animations
    val infiniteTransition = rememberInfiniteTransition(label = "sleep_anims")
    val twinkleAlpha1 by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle_1"
    )
    val twinkleAlpha2 by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(3500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "twinkle_2"
    )

    // Visual breathing ring guide (concentric expansion and contraction)
    val breatheScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(4500, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathe"
    )

    // Glowing blob motion
    val blobTranslationX by infiniteTransition.animateFloat(
        initialValue = -50f,
        targetValue = 50f,
        animationSpec = infiniteRepeatable(
            animation = tween(8000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "blobX"
    )

    Box(modifier = modifier.background(NightDeepBg)) {
        // 1. Beautiful Starry Night Canvas Background with Frosted Glow Blobs
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Base deep cosmos color
            drawRect(color = NightDeepBg)

            // Top-left giant indigo glow blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x592D2A6E), Color.Transparent),
                    center = Offset(width * 0.1f + blobTranslationX, height * 0.1f),
                    radius = width * 0.75f
                ),
                radius = width * 0.75f,
                center = Offset(width * 0.1f + blobTranslationX, height * 0.1f)
            )

            // Bottom-right giant purple glow blob
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x406750A4), Color.Transparent),
                    center = Offset(width * 0.9f - blobTranslationX, height * 0.85f),
                    radius = width * 0.7f
                ),
                radius = width * 0.7f,
                center = Offset(width * 0.9f - blobTranslationX, height * 0.85f)
            )

            // Draw twinkling stars
            stars.forEachIndexed { index, starOffset ->
                val realX = starOffset.x * width
                val realY = starOffset.y * height
                val alpha = if (index % 2 == 0) twinkleAlpha1 else twinkleAlpha2
                val starRadius = if (index % 4 == 0) 3.0f else 1.8f
                
                drawCircle(
                    color = GlowingStar.copy(alpha = alpha),
                    radius = starRadius,
                    center = Offset(realX, realY)
                )
            }
        }

        // 2. Main Content Layout
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(
                top = innerPadding.calculateTopPadding() + 16.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header Title Block with Frosted Glass Action Button
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (activeMode == "BINAURAL") "双脑同步冥想" else "静谧之夜",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            letterSpacing = (-0.5).sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (activeMode == "BINAURAL") "基于差频正弦波与全息噪声实时合成" else "伴你入眠，祝好梦",
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f)
                        )
                    }

                    // Frosted Glass round settings button
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(GlassCardBg)
                            .border(
                                width = 1.dp,
                                brush = Brush.verticalGradient(
                                    colors = listOf(GlassCardBorderTop, GlassCardBorderBottom)
                                ),
                                shape = CircleShape
                            )
                            .clickable {
                                // Light interactive feedback
                                if (activeMode == "WHITE_NOISE") {
                                    val types = SoundType.values()
                                    val randomSound = types[Random.nextInt(types.size)]
                                    SleepNoiseManager.setVolume(randomSound, (0.2f + Random.nextFloat() * 0.6f))
                                } else {
                                    val randomTheme = Random.nextInt(6) + 1
                                    SleepNoiseManager.setBinauralThemeId(randomTheme)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (activeMode == "BINAURAL") Icons.Rounded.Psychology else Icons.Rounded.Bedtime,
                            contentDescription = "Quick Action",
                            tint = AccentIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // Mode Selector Tabs (White Noise vs. Binaural Beats)
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(GlassCardBg)
                        .border(
                            width = 1.dp,
                            brush = Brush.verticalGradient(
                                colors = listOf(GlassCardBorderTop, GlassCardBorderBottom)
                            ),
                            shape = RoundedCornerShape(24.dp)
                        )
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val modes = listOf("WHITE_NOISE" to "静谧白噪", "BINAURAL" to "双脑同步")
                    modes.forEach { (modeKey, modeName) ->
                        val isSelected = activeMode == modeKey
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(20.dp))
                                .then(
                                    if (isSelected) {
                                        Modifier.background(
                                            Brush.horizontalGradient(
                                                listOf(AccentIndigo.copy(alpha = 0.45f), AccentPurple.copy(alpha = 0.35f))
                                            )
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable {
                                    SleepNoiseManager.setActiveMode(modeKey)
                                }
                                .padding(vertical = 10.dp)
                                .testTag("mode_tab_$modeKey"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = modeName,
                                fontSize = 14.sp,
                                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.5f),
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // Traditional Presets Row (Only show in White Noise mode with smooth transition)
            item {
                AnimatedVisibility(
                    visible = activeMode == "WHITE_NOISE",
                    enter = expandVertically(animationSpec = tween(350)) + fadeIn(animationSpec = tween(350)),
                    exit = shrinkVertically(animationSpec = tween(350)) + fadeOut(animationSpec = tween(350))
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Text(
                            text = "精选安眠意境",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.padding(start = 2.dp)
                        )
                        
                        val defaultPresets = listOf("雨夜安眠", "旷野篝火", "静水灵心", "深海奇遇", "红泥煮雪")
                        val presets = defaultPresets + customPresets.map { it.name }
                        
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(presets) { preset ->
                                val isSelected = activePreset == preset
                                val isCustom = !defaultPresets.contains(preset)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isSelected) AccentIndigo.copy(alpha = 0.25f)
                                            else GlassCardBg
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = if (isSelected) {
                                                Brush.verticalGradient(colors = listOf(AccentIndigo, AccentPurple))
                                            } else {
                                                Brush.verticalGradient(colors = listOf(GlassCardBorderTop, GlassCardBorderBottom))
                                            },
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            if (isPlaying) {
                                                SleepNoiseManager.setPlayingMode("WHITE_NOISE")
                                            }
                                            if (isCustom) {
                                                val customObj = customPresets.find { it.name == preset }
                                                if (customObj != null) {
                                                    SleepNoiseManager.applyPreset(preset, customObj.toVolumesMap())
                                                } else {
                                                    SleepNoiseManager.applyPreset(preset)
                                                }
                                            } else {
                                                SleepNoiseManager.applyPreset(preset)
                                            }
                                        }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .testTag("preset_$preset")
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = preset,
                                            fontSize = 13.sp,
                                            color = if (isSelected) AccentIndigo else Color.White.copy(alpha = 0.8f),
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                        if (isCustom) {
                                            Icon(
                                                imageVector = Icons.Rounded.Close,
                                                contentDescription = "删除自定义预设",
                                                tint = if (isSelected) AccentIndigo.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier
                                                    .size(14.dp)
                                                    .clickable {
                                                        coroutineScope.launch {
                                                            repository.deleteByName(preset)
                                                            if (activePreset == preset) {
                                                                SleepNoiseManager.applyPreset("雨夜安眠")
                                                            }
                                                        }
                                                    }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Warning bar if White Noise timer cuts off Binaural theme with smooth transition
            item {
                val themeMins = binauralDurationMinutes
                val showWarning = activeMode == "BINAURAL" && isTimerActive && themeMins != -1 && timerTotalMinutes < themeMins
                AnimatedVisibility(
                    visible = showWarning,
                    enter = expandVertically(animationSpec = tween(350)) + fadeIn(animationSpec = tween(350)),
                    exit = shrinkVertically(animationSpec = tween(350)) + fadeOut(animationSpec = tween(350))
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0x33EF4444)),
                        border = BorderStroke(1.dp, Color(0x66EF4444)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = "Warning",
                                tint = Color(0xFFFCA5A5),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "将跳过回升段：当前定时（${timerTotalMinutes}分钟）小于冥想主题总时长（${themeMins}分钟），音频届时将被截断。",
                                color = Color(0xFFFECACA),
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // Centered Dome / Sphere (Interactive Animation)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(230.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // Concentric pulsing breath guide rings
                    if (isPlaying) {
                        Canvas(modifier = Modifier.size(220.dp)) {
                            drawCircle(
                                color = BreathCircle,
                                radius = (85.dp.toPx() * breatheScale),
                                style = Stroke(width = 1.5f.dp.toPx())
                            )
                            drawCircle(
                                color = BreathCircleSecondary,
                                radius = (105.dp.toPx() * (breatheScale * 0.95f)),
                                style = Stroke(width = 1.dp.toPx())
                            )
                        }
                    }

                    // Frosted Gradient Center Sphere
                    Box(
                        modifier = Modifier
                            .size(165.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        AccentIndigo.copy(alpha = 0.85f),
                                        AccentPurple.copy(alpha = 0.85f)
                                    )
                                )
                            )
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(12.dp)
                        ) {
                            Icon(
                                imageVector = if (activeMode == "BINAURAL") Icons.Rounded.Psychology else (if (isPlaying) Icons.Rounded.Bedtime else Icons.Rounded.Snooze),
                                contentDescription = "Mode Icon",
                                tint = if (isPlaying) GlowingStar else Color.White.copy(alpha = 0.7f),
                                modifier = Modifier.size(32.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            if (activeMode == "BINAURAL") {
                                // Calculate realtime difference frequency
                                val currentDiff = getBinauralDiffHz(
                                    binauralThemeId,
                                    binauralElapsedSeconds,
                                    binauralDurationMinutes,
                                    binauralAlarmTimeA,
                                    binauralAlarmTimeB
                                )
                                val waveLabel = getWaveTypeLabel(currentDiff)
                                
                                Text(
                                    text = String.format("%.1f Hz", currentDiff),
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = waveLabel,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            } else {
                                val timerText = when {
                                    isTimerActive -> formatTime(timerRemainingSeconds)
                                    isLucidCueEnabled && isPlaying -> {
                                        val delaySec = lucidCueDelayMinutes * 60
                                        if (lucidCueElapsedSeconds < delaySec) {
                                            formatTime(delaySec - lucidCueElapsedSeconds)
                                        } else {
                                            "线索x$lucidCueTriggerCount"
                                        }
                                    }
                                    isLucidCueEnabled -> "${lucidCueDelayMinutes}m 线索"
                                    else -> "无定时"
                                }
                                Text(
                                    text = timerText,
                                    fontSize = if (timerText.length > 5) 24.sp else 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    letterSpacing = 1.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                val sphereSubtitle = when {
                                    isTimerActive && isLucidCueEnabled -> "定时关闭 + 梦境线索"
                                    isTimerActive -> if (isPlaying) "深慢呼吸..." else "静候睡眠..."
                                    isLucidCueEnabled && isPlaying -> {
                                        val delaySec = lucidCueDelayMinutes * 60
                                        if (lucidCueElapsedSeconds < delaySec) "清醒梦线索倒计时" else "梦境中已唤醒 $lucidCueTriggerCount 次"
                                    }
                                    isLucidCueEnabled -> "清醒梦提醒已就绪"
                                    else -> if (isPlaying) "深慢呼吸..." else "静候睡眠..."
                                }
                                Text(
                                    text = sphereSubtitle,
                                    fontSize = 11.sp,
                                    color = if (isLucidCueEnabled) GlowingStar else Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }

            // Quick Controller Layout (Play Button, Calibration and Phase Indicators)
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    val isCurrentTabPlaying = isPlaying && (playingMode == activeMode)

                    // Play / Pause Toggle Button
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.White, Color(0xFFE2E8F0))
                                )
                            )
                            .clickable {
                                if (isCurrentTabPlaying) {
                                    SleepNoiseManager.setPlaying(false)
                                    onSendAction(SleepNoiseService.ACTION_PAUSE)
                                } else {
                                    SleepNoiseManager.setPlayingMode(activeMode)
                                    SleepNoiseManager.setPlaying(true)
                                    onSendAction(SleepNoiseService.ACTION_PLAY)
                                }
                            }
                            .testTag("play_pause_button"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isCurrentTabPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = "Play Pause Toggle",
                            tint = Color(0xFF0D0B14),
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    // Display active phase labels when playing in Binaural mode or Timer buttons in White Noise mode with smooth transition
                    AnimatedContent(
                        targetState = activeMode,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(300)) togetherWith fadeOut(animationSpec = tween(300))
                        },
                        modifier = Modifier.animateContentSize(animationSpec = tween(300)),
                        label = "quick_controller_transition"
                    ) { mode ->
                        if (mode == "BINAURAL") {
                            val phaseName = getBinauralPhaseName(
                                binauralThemeId,
                                binauralElapsedSeconds,
                                binauralDurationMinutes,
                                binauralAlarmTimeA,
                                binauralAlarmTimeB
                            )
                            Text(
                                text = if (isPlaying && playingMode == "BINAURAL") phaseName else "暂停中 (佩戴耳机效果最佳)",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 12.dp)
                            )
                        } else {
                            // Traditional White Noise Timer Buttons
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "选择睡眠定时关闭时间",
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.5f)
                                )
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val timerOptions = listOf(15, 30, 45, 60)
                                    timerOptions.forEach { mins ->
                                        val isTimerSelected = timerTotalMinutes == mins
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(
                                                    if (isTimerSelected) AccentIndigo.copy(alpha = 0.2f)
                                                    else GlassCardBg
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    brush = if (isTimerSelected) {
                                                        Brush.verticalGradient(colors = listOf(AccentIndigo, AccentPurple))
                                                    } else {
                                                        Brush.verticalGradient(colors = listOf(GlassCardBorderTop, GlassCardBorderBottom))
                                                    },
                                                    shape = RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    SleepNoiseManager.startTimer(mins)
                                                    SleepNoiseManager.setPlayingMode("WHITE_NOISE")
                                                    if (!isPlaying) {
                                                        SleepNoiseManager.setPlaying(true)
                                                        onSendAction(SleepNoiseService.ACTION_PLAY)
                                                    }
                                                }
                                                .padding(horizontal = 14.dp, vertical = 6.dp)
                                                .testTag("timer_$mins")
                                        ) {
                                            Text(
                                                text = "${mins}分",
                                                fontSize = 12.sp,
                                                color = if (isTimerSelected) AccentIndigo else Color.White.copy(alpha = 0.7f),
                                                fontWeight = if (isTimerSelected) FontWeight.SemiBold else FontWeight.Normal
                                            )
                                        }
                                    }

                                    // Clear Timer Option
                                    if (isTimerActive) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(16.dp))
                                                .background(Color(0x26EF4444))
                                                .border(
                                                    1.dp,
                                                    Brush.verticalGradient(colors = listOf(Color(0x66EF4444), Color(0x1AEF4444))),
                                                    RoundedCornerShape(16.dp)
                                                )
                                                .clickable {
                                                    SleepNoiseManager.stopTimer()
                                                }
                                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                                .testTag("timer_clear")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Rounded.TimerOff,
                                                contentDescription = "Clear Timer",
                                                tint = Color(0xFFFCA5A5),
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Segmented Panel rendering: Sliders (White noise) or Setting Panel + Theme Cards (Binaural beats) with smooth slide transition
            item {
                AnimatedContent(
                    targetState = activeMode,
                    transitionSpec = {
                        if (targetState == "BINAURAL") {
                            (slideInHorizontally { width -> width } + fadeIn(animationSpec = tween(350)))
                                .togetherWith(slideOutHorizontally { width -> -width } + fadeOut(animationSpec = tween(350)))
                        } else {
                            (slideInHorizontally { width -> -width } + fadeIn(animationSpec = tween(350)))
                                .togetherWith(slideOutHorizontally { width -> width } + fadeOut(animationSpec = tween(350)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().animateContentSize(animationSpec = tween(350)),
                    label = "mode_panel_transition"
                ) { mode ->
                    if (mode == "WHITE_NOISE") {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Lucid Dream Reality Check Audio Cue (WBTB) Card
                            LucidDreamCueCard(
                                isEnabled = isLucidCueEnabled,
                                onEnabledChange = { SleepNoiseManager.setLucidCueEnabled(it) },
                                delayMinutes = lucidCueDelayMinutes,
                                onDelayChange = { SleepNoiseManager.setLucidCueDelayMinutes(it) },
                                soundType = lucidCueSoundType,
                                onSoundTypeChange = { SleepNoiseManager.setLucidCueSoundType(it) },
                                maxTriggers = lucidCueMaxTriggers,
                                onMaxTriggersChange = { SleepNoiseManager.setLucidCueMaxTriggers(it) },
                                volume = lucidCueVolume,
                                onVolumeChange = { SleepNoiseManager.setLucidCueVolume(it) },
                                isProgressiveVolume = isProgressiveVolume,
                                onProgressiveVolumeChange = { SleepNoiseManager.setProgressiveVolume(it) },
                                isTlrActive = isTlrActive,
                                tlrStep = tlrStep,
                                tlrCountdown = tlrCountdown,
                                onStartTlr = {
                                    SleepNoiseManager.startTlrTraining()
                                    onSendAction(SleepNoiseService.ACTION_START)
                                },
                                onNextTlrStep = { SleepNoiseManager.nextTlrStep() },
                                onCancelTlr = {
                                    SleepNoiseManager.cancelTlrTraining()
                                },
                                elapsedSeconds = lucidCueElapsedSeconds,
                                triggerCount = lucidCueTriggerCount,
                                isPlaying = isPlaying && playingMode == "WHITE_NOISE",
                                isTesting = isLucidCueTesting,
                                onPreviewClick = {
                                    SleepNoiseManager.triggerLucidCuePreview()
                                    onSendAction(SleepNoiseService.ACTION_START)
                                },
                                onResetElapsed = { SleepNoiseManager.resetLucidCueElapsed() }
                            )

                            // Sliders Mixer section header
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Tune,
                                    contentDescription = "Mixer Icon",
                                    tint = AccentIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "自定义混合音效",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = { showSaveDialog = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = AccentIndigo),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.testTag("save_preset_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Save,
                                        contentDescription = "Save Preset",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("保存当前混音", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }

                            // Sound Channel Sliders
                            SoundType.values().forEach { sound ->
                                val volume = volumes[sound] ?: 0.0f
                                val isActiveSound = volume > 0.01f

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(
                                            if (isActiveSound) Color(0x1F818CF8) else GlassCardBg
                                        )
                                        .border(
                                            width = 1.dp,
                                            brush = if (isActiveSound) {
                                                Brush.verticalGradient(colors = listOf(AccentIndigo.copy(alpha = 0.4f), AccentPurple.copy(alpha = 0.2f)))
                                            } else {
                                                Brush.verticalGradient(colors = listOf(GlassCardBorderTop, GlassCardBorderBottom))
                                            },
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .padding(horizontal = 16.dp, vertical = 12.dp)
                                        .testTag("sound_card_${sound.id}"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = {
                                            val targetVol = if (isActiveSound) 0.0f else 0.5f
                                            SleepNoiseManager.setVolume(sound, targetVol)
                                        },
                                        modifier = Modifier.size(40.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .clip(CircleShape)
                                                .background(
                                                    if (isActiveSound) AccentIndigo.copy(alpha = 0.2f)
                                                    else Color.White.copy(alpha = 0.05f)
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = getSoundIcon(sound.iconName),
                                                contentDescription = sound.displayName,
                                                tint = if (isActiveSound) AccentIndigo else Color.White.copy(alpha = 0.4f),
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(12.dp))

                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = sound.displayName,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Normal,
                                                color = if (isActiveSound) Color.White else Color.White.copy(alpha = 0.5f)
                                            )
                                            
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(
                                                    text = "${(volume * 100).toInt()}%",
                                                    fontSize = 11.sp,
                                                    color = if (isActiveSound) AccentIndigo else Color.White.copy(alpha = 0.3f),
                                                    fontWeight = if (isActiveSound) FontWeight.SemiBold else FontWeight.Normal
                                                )
                                                if (isActiveSound) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(5.dp)
                                                            .clip(CircleShape)
                                                            .background(AccentIndigo)
                                                    )
                                                }
                                            }
                                        }
                                        
                                        Slider(
                                            value = volume,
                                            onValueChange = { newVal ->
                                                SleepNoiseManager.setVolume(sound, newVal)
                                            },
                                            valueRange = 0f..1f,
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = AccentIndigo,
                                                inactiveTrackColor = Color(0x1F818CF8),
                                                thumbColor = AccentIndigo
                                            ),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(32.dp)
                                                .testTag("slider_${sound.id}")
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // --- Binaural Beats Mode Layout Panels ---
                        Column(
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // 1. Binaural Settings Dashboard
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    text = "双脑同步精细配置",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(GlassCardBg)
                                        .border(1.dp, GlassCardBorderTop, RoundedCornerShape(20.dp))
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Separation prompts toggle
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = "分离意识触发提示音",
                                                color = Color.White,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                text = "播放数学精确合成的柔和下行辅音",
                                                color = Color.White.copy(alpha = 0.5f),
                                                fontSize = 11.sp
                                            )
                                        }
                                        Switch(
                                            checked = binauralSeparationPromptEnabled,
                                            onCheckedChange = { SleepNoiseManager.setSeparationPromptEnabled(it) },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = AccentIndigo,
                                                checkedTrackColor = AccentIndigo.copy(alpha = 0.4f)
                                            ),
                                            modifier = Modifier.testTag("prompt_switch")
                                        )
                                    }

                                    HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.08f))

                                    // Master calibration volume control
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "暗示与校准声道主音量",
                                                    color = Color.White,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium
                                                )
                                                Text(
                                                    text = "调至清醒时刚好能在耳中听到的细微级别",
                                                    color = Color.White.copy(alpha = 0.5f),
                                                    fontSize = 11.sp
                                                )
                                            }
                                            Text(
                                                text = "${(binauralCalibrationVolume * 100).toInt()}%",
                                                color = AccentIndigo,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        
                                        Slider(
                                            value = binauralCalibrationVolume,
                                            onValueChange = { SleepNoiseManager.setBinauralCalibrationVolume(it) },
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = AccentIndigo,
                                                thumbColor = AccentIndigo
                                            ),
                                            modifier = Modifier.fillMaxWidth().testTag("binaural_volume")
                                        )

                                        // Real testing calibration button for Theme 6
                                        if (binauralThemeId == 6) {
                                            Button(
                                                onClick = {
                                                    isCalibrating = true
                                                    SleepNoiseManager.setActiveMode("BINAURAL")
                                                    SleepNoiseManager.setPlayingMode("BINAURAL")
                                                    SleepNoiseManager.setBinauralThemeId(6)
                                                    // Directly skip to alarm A playback to hear prompt
                                                    SleepNoiseManager.setBinauralElapsedSeconds(binauralAlarmTimeA.toDouble() * 3600.0 + 1.0)
                                                    SleepNoiseManager.setPlaying(true)
                                                    onSendAction(SleepNoiseService.ACTION_PLAY)

                                                    coroutineScope.launch {
                                                        delay(10000)
                                                        if (isCalibrating) {
                                                            isCalibrating = false
                                                            SleepNoiseManager.setPlaying(false)
                                                            onSendAction(SleepNoiseService.ACTION_PAUSE)
                                                            SleepNoiseManager.setBinauralElapsedSeconds(0.0)
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = if (isCalibrating) AccentPurple else AccentIndigo.copy(alpha = 0.2f)
                                                ),
                                                shape = RoundedCornerShape(12.dp),
                                                modifier = Modifier.fillMaxWidth().height(36.dp).testTag("calibrate_button")
                                            ) {
                                                Icon(
                                                    imageVector = if (isCalibrating) Icons.Rounded.GraphicEq else Icons.Rounded.VolumeUp,
                                                    contentDescription = "Calibrate",
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = if (isCalibrating) "校准中 (10秒提示音试听)..." else "开始 10秒提示音音量试听校准",
                                                    fontSize = 11.sp,
                                                    color = Color.White
                                                )
                                            }
                                        }
                                    }

                                    // Theme 6 specific setting panels
                                    if (binauralThemeId == 6) {
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.08f))
                                        
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "REM快速眼动期定时触发点 (小时)",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                // Alarm A controller
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                                    Text(text = "第一阶段 (REM-A)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = { SleepNoiseManager.setBinauralAlarmTimeA(binauralAlarmTimeA - 0.5f) },
                                                            modifier = Modifier.size(28.dp).testTag("rem_a_minus")
                                                        ) {
                                                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease", tint = Color.White)
                                                        }
                                                        Text(
                                                            text = String.format("%.1f h", binauralAlarmTimeA),
                                                            fontSize = 14.sp,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp)
                                                        )
                                                        IconButton(
                                                            onClick = { SleepNoiseManager.setBinauralAlarmTimeA(binauralAlarmTimeA + 0.5f) },
                                                            modifier = Modifier.size(28.dp).testTag("rem_a_plus")
                                                        ) {
                                                            Icon(Icons.Rounded.Add, contentDescription = "Increase", tint = Color.White)
                                                        }
                                                    }
                                                }

                                                // Alarm B controller
                                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                                                    Text(text = "第二阶段 (REM-B)", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        IconButton(
                                                            onClick = { SleepNoiseManager.setBinauralAlarmTimeB(binauralAlarmTimeB - 0.5f) },
                                                            modifier = Modifier.size(28.dp).testTag("rem_b_minus")
                                                        ) {
                                                            Icon(Icons.Rounded.Remove, contentDescription = "Decrease", tint = Color.White)
                                                        }
                                                        Text(
                                                            text = String.format("%.1f h", binauralAlarmTimeB),
                                                            fontSize = 14.sp,
                                                            color = Color.White,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(horizontal = 4.dp)
                                                        )
                                                        IconButton(
                                                            onClick = { SleepNoiseManager.setBinauralAlarmTimeB(binauralAlarmTimeB + 0.5f) },
                                                            modifier = Modifier.size(28.dp).testTag("rem_b_plus")
                                                        ) {
                                                            Icon(Icons.Rounded.Add, contentDescription = "Increase", tint = Color.White)
                                                        }
                                                    }
                                                }
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                text = "🔌 首次使用提示：整夜播放请连接手机电源以防电池耗尽。",
                                                color = GlowingStar,
                                                fontSize = 11.sp,
                                                textAlign = TextAlign.Center,
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Binaural Themes selection list Header
                            Text(
                                text = "选择双脑同步冥想音频主题 (戴耳机体验)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White,
                                modifier = Modifier.padding(start = 2.dp, top = 8.dp)
                            )

                            // Theme cards list
                            (1..6).forEach { themeId ->
                                val isThemeSelected = binauralThemeId == themeId
                                
                                val themeName = when (themeId) {
                                    1 -> "主题 1: 身心分离 (出体训练)"
                                    2 -> "主题 2: 抽离 (解离训练)"
                                    3 -> "主题 3: 辽阔 (宏大感体验)"
                                    4 -> "主题 4: 心流 (专注写作)"
                                    5 -> "主题 5: 意象流 (半醒自由联想)"
                                    6 -> "主题 6: 清明梦 (整夜睡眠暗示)"
                                    else -> ""
                                }
                                val themeDesc = when (themeId) {
                                    1 -> "身体逐步进入深度睡眠状态（θ波），而意识依然保持醒觉。配合独立双声道粉噪弱化空间锚定，高载波β波拉扯意识分层。"
                                    2 -> "专为弱化空间方位设计。通过长周期低通滤波器、混响比例和整体输出声压缓慢起伏，使声音向远处悄然沉去，引导意识解离。"
                                    3 -> "极低频音符共振（40Hz）与持续无限上扬的 Shepard 幻想音阶完美编织。带来难以估量的辽阔空间宏大感与震撼飞升意象。"
                                    4 -> "稳定的 8Hz 差频与全波形棕色噪音混音，在静寂中完全隐匿存在。极大限度平息周遭白噪，给思维铺设极度心流的创作基座。"
                                    5 -> "双载波微调干涉。左右声道正弦基波具有 3Hz 极其细微的物理碰撞，产生不规则干涉慢浮沉，能够快速触发出神奇幻象意境。"
                                    6 -> "长达8小时定制时间轴。前半段协助轻松入眠，随后处于长时间静默区防打扰，仅在4.5小时与6小时的REM期渐进引入轻柔提示音。"
                                    else -> ""
                                }
                                val themeDurationText = when (themeId) {
                                    1 -> "$binauralDurationMinutes 分钟"
                                    2 -> "45 分钟"
                                    3 -> "30 分钟"
                                    4 -> "无限循环"
                                    5 -> "40 分钟"
                                    6 -> "8 小时"
                                    else -> ""
                                }
                                val themeSpecs = when (themeId) {
                                    1 -> "载波 100 Hz / AM 16Hz 细密颤音 / 200Hz分层"
                                    2 -> "载波 90 Hz / 220 Hz 退向远方 / IIR Schroeder"
                                    3 -> "载波 110 Hz / 40 Hz 极低共鸣 / 谢泼德上行"
                                    4 -> "载波 120 Hz / 8 Hz 恒定 / 棕色噪音"
                                    5 -> "双基载 100 & 103 Hz 失谐 / 粉噪 25%"
                                    6 -> "载波 100 Hz / REM期 3音现实检验触发器"
                                    else -> ""
                                }

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(if (isThemeSelected) AccentIndigo.copy(alpha = 0.2f) else GlassCardBg)
                                        .border(
                                            width = 1.dp,
                                            brush = if (isThemeSelected) {
                                                Brush.verticalGradient(colors = listOf(AccentIndigo, AccentPurple))
                                            } else {
                                                Brush.verticalGradient(colors = listOf(GlassCardBorderTop, GlassCardBorderBottom))
                                            },
                                            shape = RoundedCornerShape(20.dp)
                                        )
                                        .clickable {
                                            SleepNoiseManager.setBinauralThemeId(themeId)
                                            if (isPlaying) {
                                                SleepNoiseManager.setPlayingMode("BINAURAL")
                                            }
                                        }
                                        .padding(16.dp)
                                        .testTag("binaural_theme_card_$themeId")
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = themeName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isThemeSelected) AccentIndigo else Color.White
                                        )
                                        
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(Color.White.copy(alpha = 0.05f))
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                        ) {
                                            Text(
                                                text = themeDurationText,
                                                fontSize = 11.sp,
                                                color = Color.White.copy(alpha = 0.6f)
                                            )
                                        }
                                    }
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = themeDesc,
                                        fontSize = 12.sp,
                                        color = Color.White.copy(alpha = 0.6f),
                                        lineHeight = 18.sp
                                    )
                                    
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "技术规格: $themeSpecs",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.4f)
                                        )
                                        
                                        if (isThemeSelected) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(AccentIndigo)
                                            )
                                        }
                                    }

                                    // Theme 1 duration pills selection
                                    if (themeId == 1 && isThemeSelected) {
                                        Spacer(modifier = Modifier.height(12.dp))
                                        HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.08f))
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Text(
                                            text = "调节出体训练冥想时长设定:",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(bottom = 6.dp)
                                        )
                                        
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            val durationOptions = listOf(45, 60, 90)
                                            durationOptions.forEach { mins ->
                                                val isDurSelected = binauralDurationMinutes == mins
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .background(if (isDurSelected) AccentIndigo.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.05f))
                                                        .border(
                                                            1.dp,
                                                            if (isDurSelected) AccentIndigo else Color.Transparent,
                                                            RoundedCornerShape(12.dp)
                                                        )
                                                        .clickable { SleepNoiseManager.setBinauralDurationMinutes(mins) }
                                                        .padding(horizontal = 12.dp, vertical = 6.dp)
                                                        .testTag("theme_1_dur_$mins")
                                                ) {
                                                    Text(
                                                        text = "$mins 分钟",
                                                        fontSize = 11.sp,
                                                        color = if (isDurSelected) AccentIndigo else Color.White.copy(alpha = 0.7f),
                                                        fontWeight = if (isDurSelected) FontWeight.SemiBold else FontWeight.Normal
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                                
                                if (themeId < 6) {
                                    Spacer(modifier = Modifier.height(14.dp))
                                }
                            }
                        }
                    }
                }
            }

            // Beautiful footer
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🌌 愿双脑同步带您步入深邃的意识之境 🌌",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.35f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }

    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { 
                showSaveDialog = false
                newPresetName = ""
            },
            title = {
                Text(
                    text = "保存自定义音效组合",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "将当前的各项白噪音与疗愈音乐的音量比例保存为自定义安眠意境。",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 13.sp
                    )
                    OutlinedTextField(
                        value = newPresetName,
                        onValueChange = { newPresetName = it },
                        placeholder = { Text("例：午后小憩、空山新雨...", color = Color.White.copy(alpha = 0.35f)) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = AccentIndigo,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            cursorColor = AccentIndigo
                        ),
                        modifier = Modifier.fillMaxWidth().testTag("preset_name_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val nameToSave = newPresetName.trim().ifEmpty { 
                            "自定义混音 ${customPresets.size + 1}" 
                        }
                        coroutineScope.launch {
                            val entity = CustomPreset.fromVolumesMap(nameToSave, volumes)
                            repository.insert(entity)
                            SleepNoiseManager.applyPreset(nameToSave, volumes)
                            showSaveDialog = false
                            newPresetName = ""
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo)
                ) {
                    Text("保存", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showSaveDialog = false
                        newPresetName = ""
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.6f))
                ) {
                    Text("取消")
                }
            },
            containerColor = Color(0xFF1E1B2C),
            shape = RoundedCornerShape(24.dp)
        )
    }
}

// Convert realtime elapsed seconds and configuration to precise current difference frequency (Hz)
fun getBinauralDiffHz(
    themeId: Int,
    elapsedSeconds: Double,
    totalMins: Int,
    alarmTimeA: Float,
    alarmTimeB: Float
): Double {
    val totalSecs = totalMins * 60.0
    return when (themeId) {
        1 -> {
            val ratio = if (totalSecs > 0.0) (elapsedSeconds / totalSecs).coerceIn(0.0, 1.0) else 0.0
            when {
                ratio < 0.1667 -> lerp(10.0, 6.0, ratio / 0.1667)
                ratio < 0.25 -> lerp(6.0, 4.5, (ratio - 0.1667) / (0.25 - 0.1667))
                ratio < 0.8333 -> 4.5
                else -> lerp(4.5, 8.0, (ratio - 0.8333) / (1.0 - 0.8333))
            }
        }
        2 -> {
            when {
                elapsedSeconds < 480.0 -> lerp(9.0, 5.0, elapsedSeconds / 480.0)
                elapsedSeconds < 2400.0 -> 4.5
                else -> lerp(4.5, 7.0, (elapsedSeconds - 2400.0) / 300.0)
            }
        }
        3 -> 6.0
        4 -> 8.0
        5 -> 5.5 // Group A baseline
        6 -> {
            val t20 = 20.0 * 60.0
            val t40 = 40.0 * 60.0
            val alarmStartA = alarmTimeA.toDouble() * 3600.0
            val alarmEndA = alarmStartA + 15.0 * 60.0
            val alarmStartB = alarmTimeB.toDouble() * 3600.0
            val alarmEndB = alarmStartB + 15.0 * 60.0
            when {
                elapsedSeconds < t20 -> lerp(9.0, 2.0, elapsedSeconds / t20)
                elapsedSeconds < t40 -> 2.0
                elapsedSeconds >= alarmStartA && elapsedSeconds < alarmEndA -> 5.5
                elapsedSeconds >= alarmStartB && elapsedSeconds < alarmEndB -> 5.5
                else -> 0.0
            }
        }
        else -> 0.0
    }
}

// Map frequency to appropriate brainwave spectrum description
fun getWaveTypeLabel(freq: Double): String {
    if (freq <= 0.1) return "静默睡眠守护期"
    return when {
        freq < 4.0 -> "Delta波 (%.1f Hz | 深度无梦眠态)".format(freq)
        freq < 8.0 -> "Theta波 (%.1f Hz | 潜意识意象联想)".format(freq)
        freq < 12.0 -> "Alpha波 (%.1f Hz | 专注创造与放松)".format(freq)
        freq < 30.0 -> "Beta波 (%.1f Hz | 清醒思考)".format(freq)
        else -> "Gamma波 (%.1f Hz | 高频感知)".format(freq)
    }
}

// Generate human readable phase descriptions based on current elapsed seconds
fun getBinauralPhaseName(
    themeId: Int,
    elapsedSeconds: Double,
    totalMins: Int,
    alarmTimeA: Float,
    alarmTimeB: Float
): String {
    val totalSecs = totalMins * 60.0
    return when (themeId) {
        1 -> {
            val ratio = if (totalSecs > 0.0) elapsedSeconds / totalSecs else 0.0
            when {
                ratio < 0.1667 -> "🧘 身体放松下行阶段 (Relaxing)"
                ratio < 0.25 -> "🌀 深层 Theta 脑电导入期"
                ratio < 0.8333 -> "✨ θ波意识维持期 (出体核心探索段)"
                else -> "🌅 缓慢回升自然唤醒阶段"
            }
        }
        2 -> {
            when {
                elapsedSeconds < 480.0 -> "🧘 解离放松导入中 (Relaxing)"
                elapsedSeconds < 2400.0 -> "🌌 θ波意识撤离段 (注意力向远方抽离)"
                else -> "🌅 意识轻柔回收回升中"
            }
        }
        3 -> "🌌 辽阔空间与永远上升的 Shepard 音阶共振中"
        4 -> "✍️ 高效心流 8Hz Alpha 波专注维持中..."
        5 -> "💭 意象漂移：双载波干涉慢起伏自由联想中..."
        6 -> {
            val t20 = 20.0 * 60.0
            val t40 = 40.0 * 60.0
            val alarmStartA = alarmTimeA.toDouble() * 3600.0
            val alarmEndA = alarmStartA + 15.0 * 60.0
            val alarmStartB = alarmTimeB.toDouble() * 3600.0
            val alarmEndB = alarmStartB + 15.0 * 60.0
            when {
                elapsedSeconds < t20 -> "💤 助眠入睡差频引导阶段..."
                elapsedSeconds < t40 -> "🛌 深度睡眠 Theta 波巩固阶段..."
                elapsedSeconds < alarmStartA -> "🌌 无干扰深睡静默守护期..."
                elapsedSeconds < alarmEndA -> "🚨 REM快速眼动期 A阶：提示音唤醒意识中..."
                elapsedSeconds < alarmStartB -> "🌌 无干扰深睡静默守护期..."
                elapsedSeconds < alarmEndB -> "🚨 REM快速眼动期 B阶：提示音唤醒意识中..."
                else -> "🌅 清晨自然静默守护中..."
            }
        }
        else -> "双脑同步冥想中..."
    }
}

private fun lerp(start: Double, end: Double, fraction: Double): Double {
    return start + (end - start) * fraction
}

@Composable
fun HorizontalDivider(modifier: Modifier = Modifier, color: Color) {
    Spacer(modifier = modifier.height(1.dp).background(color))
}

fun formatTime(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return String.format("%02d:%02d", m, s)
}

fun getSoundIcon(name: String): ImageVector {
    return when (name) {
        "white_noise" -> Icons.Rounded.GraphicEq
        "rain" -> Icons.Rounded.WaterDrop
        "ocean" -> Icons.Rounded.Waves
        "wind" -> Icons.Rounded.Air
        "campfire" -> Icons.Rounded.LocalFireDepartment
        "crickets" -> Icons.Rounded.NaturePeople
        "singing_bowl" -> Icons.Rounded.SelfImprovement
        "stream" -> Icons.Rounded.Water
        "snow_tea" -> Icons.Rounded.EmojiFoodBeverage
        else -> Icons.Rounded.MusicNote
    }
}

@Composable
fun LucidDreamCueCard(
    isEnabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    delayMinutes: Int,
    onDelayChange: (Int) -> Unit,
    soundType: Int,
    onSoundTypeChange: (Int) -> Unit,
    maxTriggers: Int,
    onMaxTriggersChange: (Int) -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    isProgressiveVolume: Boolean,
    onProgressiveVolumeChange: (Boolean) -> Unit,
    isTlrActive: Boolean,
    tlrStep: Int,
    tlrCountdown: Int,
    onStartTlr: () -> Unit,
    onNextTlrStep: () -> Unit,
    onCancelTlr: () -> Unit,
    elapsedSeconds: Int,
    triggerCount: Int,
    isPlaying: Boolean,
    isTesting: Boolean,
    onPreviewClick: () -> Unit,
    onResetElapsed: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isEnabled) Color(0x1F818CF8) else GlassCardBg
            )
            .border(
                width = 1.dp,
                brush = if (isEnabled) {
                    Brush.verticalGradient(listOf(AccentIndigo.copy(alpha = 0.5f), AccentPurple.copy(alpha = 0.3f)))
                } else {
                    Brush.verticalGradient(listOf(GlassCardBorderTop, GlassCardBorderBottom))
                },
                shape = RoundedCornerShape(20.dp)
            )
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Header with Title & Master Switch
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (isEnabled) AccentIndigo.copy(alpha = 0.25f) else Color.White.copy(alpha = 0.06f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Rounded.Visibility else Icons.Rounded.Bedtime,
                        contentDescription = "Lucid Cue Icon",
                        tint = if (isEnabled) AccentIndigo else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "清醒梦 · 现实检验提醒 (WBTB)",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isEnabled) Color.White else Color.White.copy(alpha = 0.85f)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = if (isEnabled) "已就绪 · 入睡做梦时轻柔呼唤，助你觉察在做梦" else "关闭状态 · 完全维持日常普通声音原样",
                        fontSize = 11.sp,
                        color = if (isEnabled) AccentIndigo else Color.White.copy(alpha = 0.45f)
                    )
                }
            }

            Switch(
                checked = isEnabled,
                onCheckedChange = onEnabledChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = AccentIndigo,
                    uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                ),
                modifier = Modifier.testTag("lucid_cue_switch")
            )
        }

        // 2. Expandable Rich Controls (Visible when switch is ON)
        AnimatedVisibility(
            visible = isEnabled,
            enter = expandVertically(tween(350)) + fadeIn(tween(350)),
            exit = shrinkVertically(tween(350)) + fadeOut(tween(350))
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                HorizontalDivider(modifier = Modifier.fillMaxWidth(), color = Color.White.copy(alpha = 0.08f))

                // Scientific WBTB Tip Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0x25818CF8)),
                    border = BorderStroke(1.dp, Color(0x40818CF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Lightbulb,
                                contentDescription = "WBTB Tip",
                                tint = GlowingStar,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "夜醒重睡 (WBTB) 清醒梦黄金律",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = "后半夜睡了3~4小时醒来重睡（WBTB），大脑的慢波深睡眠已基本充足。此时醒来看手机、稍作清醒后重睡，人体通常在 35~60 分钟内更容易快速进入高密度的 REM（快速眼动做梦期），但具体潜伏期因人而异。\n\n🌟 推荐初设：45 分钟左右。线索音起效的关键是‘高频反差动机’（例如双音钟 528:660Hz 的上行大三度），能自然穿透低沉海浪，在梦中留下鲜明印记。",
                            fontSize = 11.sp,
                            color = Color.White.copy(alpha = 0.82f),
                            lineHeight = 17.sp
                        )
                    }
                }

                // 🎯 TLR (Targeted Lucidity Reactivation) Awake Pairing Card
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = if (isTlrActive) Color(0x356366F1) else Color(0x18818CF8)),
                    border = BorderStroke(1.dp, if (isTlrActive) AccentIndigo else Color(0x30818CF8)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Rounded.Psychology,
                                    contentDescription = "TLR Training",
                                    tint = if (isTlrActive) GlowingStar else AccentIndigo,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "🎯 现实检验配对 (TLR 觉察训练)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }

                            if (!isTlrActive) {
                                Button(
                                    onClick = onStartTlr,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.height(30.dp).testTag("start_tlr_button")
                                ) {
                                    Text("开始配对 (45秒)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        if (!isTlrActive) {
                            Text(
                                text = "科学清醒梦核心秘诀：线索音只有在清醒时建立过反射，梦中才不会被当成普通的杂音。夜醒后建议花45秒跟随提示做3次“看手觉察练习”，让前额叶深刻联结！",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.70f),
                                lineHeight = 16.sp
                            )
                        } else {
                            // Active TLR Training step
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                LinearProgressIndicator(
                                    progress = { tlrStep / 4f },
                                    modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
                                    color = GlowingStar,
                                    trackColor = Color.White.copy(alpha = 0.1f)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = if (tlrStep <= 3) "正在进行第 $tlrStep / 3 组觉察配对" else "✨ 配对已顺利完成！",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlowingStar
                                    )
                                    if (tlrStep <= 3) {
                                        Text(
                                            text = "${tlrCountdown}s 自动下一组",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.6f)
                                        )
                                    }
                                }

                                val promptText = when (tlrStep) {
                                    1 -> "🔔 听到了刚刚的声音吗？请低头仔细注视你的双手手心，数一数手指，认真质问自己：‘我现在是在现实，还是在做梦？’"
                                    2 -> "🔔 再次听到声音！请回忆你刚才几分钟是怎么醒来的，确认周围物理规律。在大脑中建立锚定：‘今夜只要在梦中听到这个声音，我就会看手检验！’"
                                    3 -> "🔔 很好！最后一次深呼吸，感受全身放松。潜意识已牢牢锁定了这段线索。在心底默念：‘等下做梦时，这声呼唤会让我瞬间清醒！’"
                                    else -> "🎉 联结已成功建立！大脑听觉与反思皮层已准备就绪。现在安心放空，伴随「深海奇遇」入睡，在梦境高潮时等待呼唤吧！"
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .padding(10.dp)
                                ) {
                                    Text(
                                        text = promptText,
                                        fontSize = 12.sp,
                                        color = Color.White,
                                        lineHeight = 18.sp
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(onClick = onCancelTlr) {
                                        Text(if (tlrStep >= 4) "完成" else "退出", fontSize = 11.sp, color = Color.White.copy(alpha = 0.6f))
                                    }
                                    if (tlrStep in 1..3) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Button(
                                            onClick = onNextTlrStep,
                                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = AccentIndigo),
                                            shape = RoundedCornerShape(10.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("完成本组 · 下一步", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Delay Selection: How many minutes until cue sounds
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "入睡后多久播放梦境线索",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "$delayMinutes 分钟",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentIndigo
                        )
                    }

                    // Preset Chips
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val delayPresets = listOf(
                            30 to "30分",
                            45 to "45分 🌟",
                            60 to "60分",
                            75 to "75分",
                            90 to "90分"
                        )
                        delayPresets.forEach { (mins, label) ->
                            val isSelected = delayMinutes == mins
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) AccentIndigo.copy(alpha = 0.35f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) AccentIndigo else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onDelayChange(mins) }
                                    .padding(vertical = 8.dp)
                                    .testTag("lucid_delay_$mins"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    // Slider for fine adjustments
                    Slider(
                        value = delayMinutes.toFloat(),
                        onValueChange = { onDelayChange(it.toInt()) },
                        valueRange = 15f..120f,
                        steps = 20,
                        colors = SliderDefaults.colors(
                            activeTrackColor = AccentIndigo,
                            thumbColor = AccentIndigo
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("lucid_delay_slider")
                    )
                }

                // Realtime Status & Countdown Indicator
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color.White.copy(alpha = 0.05f))
                        .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            if (isPlaying) {
                                val delaySec = delayMinutes * 60
                                if (elapsedSeconds < delaySec) {
                                    val remSec = delaySec - elapsedSeconds
                                    Text(
                                        text = "⏳ 梦境线索就绪倒计时中",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = GlowingStar
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = "已入睡计时 ${formatTime(elapsedSeconds)} · 将在 ${remSec / 60}分${remSec % 60}秒 后播放线索",
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.75f)
                                    )
                                } else {
                                    val isLimitReached = maxTriggers in 1..triggerCount
                                    if (isLimitReached) {
                                        Text(
                                            text = "🌙 黄金做梦期线索已播完 ($triggerCount/$maxTriggers)",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GlowingStar
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "已自动静音休眠，深海奇遇将持续伴睡，守护整夜优质睡眠",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                    } else {
                                        Text(
                                            text = "✨ 梦境线索已激活",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = GlowingStar
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        val intervalSec = 15 * 60
                                        val passed = (elapsedSeconds - delaySec) % intervalSec
                                        val nextRepeatSec = intervalSec - passed
                                        val repeatTip = if (maxTriggers > 1) {
                                            "，下次在 ${nextRepeatSec / 60}分${nextRepeatSec % 60}秒 后"
                                        } else " (单次已完成)"
                                        Text(
                                            text = "已在梦中唤醒 $triggerCount/$maxTriggers 次$repeatTip",
                                            fontSize = 11.sp,
                                            color = Color.White.copy(alpha = 0.75f)
                                        )
                                    }
                                }
                            } else {
                                Text(
                                    text = "💤 伴睡待启动",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "点击上方主播放键伴睡，将在入睡 $delayMinutes 分钟后轻柔呼唤梦中意识",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.55f)
                                )
                            }
                        }

                        if (elapsedSeconds > 0) {
                            TextButton(
                                onClick = onResetElapsed,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("lucid_reset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Refresh,
                                    contentDescription = "Reset Timer",
                                    tint = AccentIndigo,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("重置", fontSize = 11.sp, color = AccentIndigo)
                            }
                        }
                    }
                }

                // Sound Cue Selection (4 options)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "选择梦境现实检验线索音效",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.White
                    )

                    val soundOptions = listOf(
                        1 to ("🎶 现实检验双音钟" to "528Hz与660Hz空灵双音阶 · 强烈材质反差，深海奇遇首选 🌟"),
                        2 to ("✨ 梦境水晶风铃" to "4声清越高频银铃 · 晶莹剔透，完全跳脱水声与铜钵"),
                        3 to ("🎼 灵性八音盒" to "4音纯净机械拨片 · 童话感清脆旋律，梦中极易察觉"),
                        4 to ("💧 幽潭灵露" to "三声晶莹短促水滴 · 灵动自然，水系梦境奇点"),
                        5 to ("🔔 空灵颂钵" to "传统432Hz泛音 · 适合雨夜/无钵声预设，深海奇遇不建议")
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        soundOptions.forEach { (typeId, pair) ->
                            val (title, desc) = pair
                            val isSelected = soundType == typeId
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) AccentIndigo.copy(alpha = 0.25f)
                                        else Color.White.copy(alpha = 0.04f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) AccentIndigo.copy(alpha = 0.6f) else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onSoundTypeChange(typeId) }
                                    .padding(horizontal = 12.dp, vertical = 10.dp)
                                    .testTag("lucid_sound_$typeId"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) AccentIndigo else Color.White
                                    )
                                    Text(
                                        text = desc,
                                        fontSize = 11.sp,
                                        color = Color.White.copy(alpha = 0.5f)
                                    )
                                }

                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(AccentIndigo)
                                    )
                                }
                            }
                        }
                    }
                }

                // Trigger Frequency & REM Sleep Protection
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = "Sleep Protection",
                                tint = AccentIndigo,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "提醒频次 (做梦期防打扰保护)",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                        }
                        Text(
                            text = when (maxTriggers) {
                                1 -> "仅响 1 次"
                                2 -> "响 2 次"
                                3 -> "响 3 次 (推荐🌟)"
                                else -> "响 $maxTriggers 次"
                            },
                            fontSize = 11.sp,
                            color = AccentIndigo,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val limits = listOf(
                            1 to "仅响 1 次",
                            2 to "响 2 次",
                            3 to "响 3 次 🌟"
                        )
                        limits.forEach { (count, label) ->
                            val isSelected = maxTriggers == count
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) AccentIndigo.copy(alpha = 0.35f)
                                        else Color.White.copy(alpha = 0.06f)
                                    )
                                    .border(
                                        1.dp,
                                        if (isSelected) AccentIndigo else Color.Transparent,
                                        RoundedCornerShape(12.dp)
                                    )
                                    .clickable { onMaxTriggersChange(count) }
                                    .padding(vertical = 10.dp)
                                    .testTag("lucid_max_trigger_$count"),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = label,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    Text(
                        text = "💡 科学防打扰机制：首次提醒在设定延迟（如 45 分钟）时触发；若设为 2~3 次，后续按做梦期快速眼动（REM）节律每隔 15 分钟轻柔触发一次。播完后系统立即自动休眠转为纯背景音，绝不反复打扰后半夜深睡眠。",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        lineHeight = 15.sp
                    )
                }

                // Cue Volume & Preview
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "提醒音量微调",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "${(volume * 100).toInt()}%",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentIndigo
                        )
                    }

                    Slider(
                        value = volume,
                        onValueChange = onVolumeChange,
                        valueRange = 0.10f..0.80f,
                        colors = SliderDefaults.colors(
                            activeTrackColor = AccentIndigo,
                            thumbColor = AccentIndigo
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(28.dp)
                            .testTag("lucid_volume_slider")
                    )

                    // Progressive Volume Ladder Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.04f))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "阶梯渐进微音量 (推荐)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = Color.White
                            )
                            Text(
                                text = "首次提醒从70%微音量开始，若未唤醒则后续递增，避免初次惊醒",
                                fontSize = 10.sp,
                                color = Color.White.copy(alpha = 0.5f)
                            )
                        }
                        Switch(
                            checked = isProgressiveVolume,
                            onCheckedChange = onProgressiveVolumeChange,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = AccentIndigo,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.6f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.15f)
                            ),
                            modifier = Modifier.testTag("progressive_volume_switch")
                        )
                    }

                    Text(
                        text = "💡 建议调至清醒时隐约能听到的轻柔级别即可，避免直接惊醒，刚好让梦中感知。",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.45f)
                    )

                    // Preview Button
                    Button(
                        onClick = onPreviewClick,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isTesting) AccentPurple else AccentIndigo.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .testTag("lucid_preview_button")
                    ) {
                        Icon(
                            imageVector = if (isTesting) Icons.Rounded.GraphicEq else Icons.Rounded.VolumeUp,
                            contentDescription = "Preview Cue",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (isTesting) "正在试听梦境提醒声 (约6秒)..." else "试听提醒声音量 (Preview)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

