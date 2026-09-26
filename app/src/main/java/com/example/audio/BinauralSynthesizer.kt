package com.example.audio

import kotlin.math.sin
import kotlin.math.PI
import kotlin.math.exp
import java.util.Random

class BinauralSynthesizer {
    private val sampleRate = 44100.0

    // Theme 1 state
    private var t1PhaseL = 0.0
    private var t1PhaseR = 0.0
    private var t1PhaseL2 = 0.0
    private var t1PhaseR2 = 0.0
    private var t1AmPhase = 0.0
    private var promptPhase = 0.0
    private var promptSamplesLeft = 0L
    private var promptTriggered28 = false
    private var promptTriggered40 = false

    // Theme 2 state
    private var t2PhaseL = 0.0
    private var t2PhaseR = 0.0
    private var t2FarAwayPhase = 0.0
    private var t2LfoPhase = 0.0
    private val svfLeft = StateVariableFilter()
    private val svfRight = StateVariableFilter()
    private val reverb2Left = SchroederReverb(
        combDelays = intArrayOf(1116, 1356, 1422, 1656),
        allPassDelays = intArrayOf(225, 341),
        combFeedback = 0.88f // Lush but highly controlled resonance to prevent metallic ringing
    )
    private val reverb2Right = SchroederReverb(
        combDelays = intArrayOf(1187, 1311, 1481, 1613), // prime & decorrelated delay values to widen stereo image
        allPassDelays = intArrayOf(251, 317),
        combFeedback = 0.88f
    )

    // Theme 3 state
    private var t3PhaseL = 0.0
    private var t3PhaseR = 0.0
    private var t3SubPhase = 0.0
    private var t3SubFadeSamples = 0L
    private val shepardPhases = DoubleArray(6) { 0.0 }
    private val reverb3Left = SchroederReverb(
        combDelays = intArrayOf(1116, 1356, 1422, 1656),
        allPassDelays = intArrayOf(225, 341),
        combFeedback = 0.90f
    )
    private val reverb3Right = SchroederReverb(
        combDelays = intArrayOf(1187, 1311, 1481, 1613), // decorrelated delay lines for massive stereo field
        allPassDelays = intArrayOf(251, 317),
        combFeedback = 0.90f
    )

    // Theme 4 state
    private var t4PhaseL = 0.0
    private var t4PhaseR = 0.0

    // Theme 5 state
    private var t5PhaseL = 0.0
    private var t5PhaseR = 0.0
    private var t5PhaseL2 = 0.0
    private var t5PhaseR2 = 0.0

    // Theme 6 state
    private var t6PhaseL = 0.0
    private var t6PhaseR = 0.0
    private var theme6PromptPhase = 0.0
    private var theme6PromptSamplesLeft = 0L
    private var theme6PromptIntervalSamplesLeft = 0L

    // Pink Noise generators for stereo
    private val pinkGenL = PinkNoiseGenerator(12345L)
    private val pinkGenR = PinkNoiseGenerator(67890L)

    // Master Fade
    private var currentFadeGain = 0f
    private var targetFadeGain = 1f

    // Theme Switch / Transition State (Linear Cross-Fade >= 2 seconds)
    private var activeThemeId = -1
    private var prevThemeId = -1
    private var transitionProgress = 1.0f // 1.0 means fully activeThemeId, no transition
    private val transitionDurationSamples = (44100.0 * 2.0).toFloat() // 2.0 seconds

    // Zero-allocation stereo output properties
    var outL: Float = 0f
    var outR: Float = 0f

    // Intermediate theme output fields to avoid Pair allocations during cross-fade
    private var outThemeL = 0f
    private var outThemeR = 0f

    init {
        // Initialize Shepard phases to different starting points to avoid phase correlation
        val rand = Random()
        for (i in 0..5) {
            shepardPhases[i] = rand.nextDouble() * 2.0 * PI
        }
    }

    fun reset() {
        // Theme 1 state reset
        t1PhaseL = 0.0
        t1PhaseR = 0.0
        t1PhaseL2 = 0.0
        t1PhaseR2 = 0.0
        t1AmPhase = 0.0
        promptPhase = 0.0
        promptSamplesLeft = 0L
        promptTriggered28 = false
        promptTriggered40 = false

        // Theme 2 state reset
        t2PhaseL = 0.0
        t2PhaseR = 0.0
        t2FarAwayPhase = 0.0
        t2LfoPhase = 0.0
        svfLeft.reset()
        svfRight.reset()

        // Theme 3 state reset
        t3PhaseL = 0.0
        t3PhaseR = 0.0
        t3SubPhase = 0.0
        t3SubFadeSamples = 0L

        // Theme 4 state reset
        t4PhaseL = 0.0
        t4PhaseR = 0.0

        // Theme 5 state reset
        t5PhaseL = 0.0
        t5PhaseR = 0.0
        t5PhaseL2 = 0.0
        t5PhaseR2 = 0.0

        // Theme 6 state reset
        t6PhaseL = 0.0
        t6PhaseR = 0.0
        theme6PromptPhase = 0.0
        theme6PromptSamplesLeft = 0L
        theme6PromptIntervalSamplesLeft = 0L

        // Master Fade & Theme Transition reset
        currentFadeGain = 0f
        targetFadeGain = 1f
        activeThemeId = -1
        prevThemeId = -1
        transitionProgress = 1.0f
    }

    fun setFade(fadeIn: Boolean) {
        targetFadeGain = if (fadeIn) 1f else 0f
    }

    fun isFadeOutComplete(): Boolean {
        return targetFadeGain == 0f && currentFadeGain <= 0.001f
    }

    // Process a single stereo sample pair. Updates outL and outR
    fun nextSample(
        themeId: Int,
        elapsedSeconds: Double,
        totalDurationSeconds: Double,
        promptEnabled: Boolean,
        calibrationVolume: Float,
        alarmTimeA: Float,
        alarmTimeB: Float
    ) {
        // Master fade interpolation (2 seconds = 88200 samples)
        val fadeStep = 1.0f / (sampleRate.toFloat() * 2.0f)
        if (currentFadeGain < targetFadeGain) {
            currentFadeGain = (currentFadeGain + fadeStep).coerceAtMost(targetFadeGain)
        } else if (currentFadeGain > targetFadeGain) {
            currentFadeGain = (currentFadeGain - fadeStep).coerceAtLeast(targetFadeGain)
        }

        // 1. Handle theme changes (linear cross-fade >= 2 seconds)
        if (activeThemeId == -1) {
            activeThemeId = themeId
            prevThemeId = -1
            transitionProgress = 1.0f
        } else if (themeId != activeThemeId) {
            prevThemeId = activeThemeId
            activeThemeId = themeId
            transitionProgress = 0.0f
        }

        // 2. Interpolate transition progress
        if (transitionProgress < 1.0f) {
            val step = 1.0f / transitionDurationSamples
            transitionProgress = (transitionProgress + step).coerceAtMost(1.0f)
        }

        val sampleL: Float
        val sampleR: Float

        if (transitionProgress < 1.0f && prevThemeId != -1) {
            generateThemeSample(
                prevThemeId,
                elapsedSeconds,
                totalDurationSeconds,
                promptEnabled,
                calibrationVolume,
                alarmTimeA,
                alarmTimeB
            )
            val pL = outThemeL
            val pR = outThemeR

            generateThemeSample(
                activeThemeId,
                elapsedSeconds,
                totalDurationSeconds,
                promptEnabled,
                calibrationVolume,
                alarmTimeA,
                alarmTimeB
            )
            val aL = outThemeL
            val aR = outThemeR

            sampleL = pL * (1.0f - transitionProgress) + aL * transitionProgress
            sampleR = pR * (1.0f - transitionProgress) + aR * transitionProgress
        } else {
            generateThemeSample(
                activeThemeId,
                elapsedSeconds,
                totalDurationSeconds,
                promptEnabled,
                calibrationVolume,
                alarmTimeA,
                alarmTimeB
            )
            sampleL = outThemeL
            sampleR = outThemeR
        }

        // Apply master fade
        var finalL = sampleL * currentFadeGain
        var finalR = sampleR * currentFadeGain

        // Prevent clipping & scaling
        finalL = finalL.coerceIn(-1.0f, 1.0f) * 0.9f
        finalR = finalR.coerceIn(-1.0f, 1.0f) * 0.9f

        outL = finalL
        outR = finalR
    }

    private fun generateThemeSample(
        themeId: Int,
        elapsedSeconds: Double,
        totalDurationSeconds: Double,
        promptEnabled: Boolean,
        calibrationVolume: Float,
        alarmTimeA: Float,
        alarmTimeB: Float
    ) {
        var sampleL = 0f
        var sampleR = 0f

        when (themeId) {
            1 -> {
                // Theme 1: 身心分离(出体)
                val carrier = 100.0
                // Difference frequency curve
                val ratio = if (totalDurationSeconds > 0.0) {
                    (elapsedSeconds / totalDurationSeconds).coerceIn(0.0, 1.0)
                } else {
                    0.0
                }
                val diff = when {
                    ratio < 0.1667 -> lerp(10.0, 6.0, ratio / 0.1667)
                    ratio < 0.25 -> lerp(6.0, 4.5, (ratio - 0.1667) / (0.25 - 0.1667))
                    ratio < 0.8333 -> 4.5
                    else -> lerp(4.5, 8.0, (ratio - 0.8333) / (1.0 - 0.8333))
                }

                // Phase update using Theme 1 local phase variables
                t1PhaseL += (2.0 * PI * carrier) / sampleRate
                if (t1PhaseL > 2.0 * PI) t1PhaseL -= 2.0 * PI
                t1PhaseR += (2.0 * PI * (carrier + diff)) / sampleRate
                if (t1PhaseR > 2.0 * PI) t1PhaseR -= 2.0 * PI

                var bL = sin(t1PhaseL).toFloat()
                var bR = sin(t1PhaseR).toFloat()

                // Special Layer (starting at 15th minute): 2nd oscillator, carrier 200 Hz, diff 15 Hz
                // Fade in over 60 seconds (approx. 2646000 samples)
                val t15 = 15.0 * 60.0
                if (elapsedSeconds >= t15) {
                    val fade2 = ((elapsedSeconds - t15) / 60.0).coerceIn(0.0, 1.0).toFloat()
                    t1PhaseL2 += (2.0 * PI * 200.0) / sampleRate
                    if (t1PhaseL2 > 2.0 * PI) t1PhaseL2 -= 2.0 * PI
                    t1PhaseR2 += (2.0 * PI * (200.0 + 15.0)) / sampleRate
                    if (t1PhaseR2 > 2.0 * PI) t1PhaseR2 -= 2.0 * PI

                    bL += sin(t1PhaseL2).toFloat() * 0.15f * fade2
                    bR += sin(t1PhaseR2).toFloat() * 0.15f * fade2
                }

                // AM Modulation starting at 22nd minute (1320.0 seconds)
                // Mod frequency 16 Hz, depth 3 minutes ramp to 25%, sustain to 45 min, 2 min decay to 0
                val amDepth = when {
                    elapsedSeconds < 1320.0 -> 0f
                    elapsedSeconds < 1500.0 -> {
                        val t = ((elapsedSeconds - 1320.0) / 180.0).toFloat()
                        0.25f * t
                    }
                    elapsedSeconds < 2700.0 -> 0.25f
                    elapsedSeconds < 2820.0 -> {
                        val t = ((elapsedSeconds - 2700.0) / 120.0).toFloat()
                        0.25f * (1f - t)
                    }
                    else -> 0f
                }

                if (amDepth > 0f) {
                    t1AmPhase += (2.0 * PI * 16.0) / sampleRate
                    if (t1AmPhase > 2.0 * PI) t1AmPhase -= 2.0 * PI
                    val amFactor = 1f - amDepth * (0.5f + 0.5f * sin(t1AmPhase).toFloat())
                    bL *= amFactor
                    bR *= amFactor
                }

                // Pink noise layer (40% volume). At 15th min, cross-fade to uncorrelated pink noise
                val noiseSampleL: Float
                val noiseSampleR: Float
                if (elapsedSeconds < t15) {
                    val noise = pinkGenL.nextSample()
                    noiseSampleL = noise * 0.40f
                    noiseSampleR = noise * 0.40f
                } else {
                    // cross-fade uncorrelated noise over 10 seconds
                    val crossFade = ((elapsedSeconds - t15) / 10.0).coerceIn(0.0, 1.0).toFloat()
                    val monoNoise = pinkGenL.nextSample() * 0.40f
                    val stereoNoiseL = pinkGenL.nextSample() * 0.40f
                    val stereoNoiseR = pinkGenR.nextSample() * 0.40f
                    noiseSampleL = monoNoise * (1f - crossFade) + stereoNoiseL * crossFade
                    noiseSampleR = monoNoise * (1f - crossFade) + stereoNoiseR * crossFade
                }

                sampleL = bL + noiseSampleL
                sampleR = bR + noiseSampleR

                // Separation prompt at 28 and 40 min (double tone 550Hz -> 440Hz, 0.6s each)
                if (promptEnabled) {
                    if (Math.abs(elapsedSeconds - 1680.0) < 0.1 && !promptTriggered28) {
                        promptTriggered28 = true
                        promptSamplesLeft = (sampleRate * 1.2).toLong()
                    }
                    if (Math.abs(elapsedSeconds - 2400.0) < 0.1 && !promptTriggered40) {
                        promptTriggered40 = true
                        promptSamplesLeft = (sampleRate * 1.2).toLong()
                    }

                    if (promptSamplesLeft > 0) {
                        val totalPromptSamples = (sampleRate * 1.2).toLong()
                        val elapsedSamples = totalPromptSamples - promptSamplesLeft
                        val inToneSamples = elapsedSamples % (sampleRate * 0.6).toLong()
                        val isFirstTone = elapsedSamples < (sampleRate * 0.6).toLong()
                        val freq = if (isFirstTone) 550.0 else 440.0

                        promptPhase += (2.0 * PI * freq) / sampleRate
                        if (promptPhase > 2.0 * PI) promptPhase -= 2.0 * PI

                        // Tone envelope: 0.2s fade-in, 0.2s sustain, 0.2s fade-out
                        val toneLength = (sampleRate * 0.6).toLong()
                        val fadeLength = (sampleRate * 0.2).toLong()
                        val env = when {
                            inToneSamples < fadeLength -> inToneSamples.toFloat() / fadeLength
                            inToneSamples > toneLength - fadeLength -> (toneLength - inToneSamples).toFloat() / fadeLength
                            else -> 1f
                        }

                        val promptValue = sin(promptPhase).toFloat() * env * 0.40f
                        sampleL += promptValue
                        sampleR += promptValue
                        promptSamplesLeft--
                    }
                }
            }
            2 -> {
                // Theme 2: 抽离(解离训练)
                val carrier = 90.0
                val diff = when {
                    elapsedSeconds < 480.0 -> lerp(9.0, 5.0, elapsedSeconds / 480.0)
                    elapsedSeconds < 2400.0 -> 4.5
                    else -> lerp(4.5, 7.0, (elapsedSeconds - 2400.0) / 300.0)
                }

                t2PhaseL += (2.0 * PI * carrier) / sampleRate
                if (t2PhaseL > 2.0 * PI) t2PhaseL -= 2.0 * PI
                t2PhaseR += (2.0 * PI * (carrier + diff)) / sampleRate
                if (t2PhaseR > 2.0 * PI) t2PhaseR -= 2.0 * PI

                var bL = sin(t2PhaseL).toFloat()
                var bR = sin(t2PhaseR).toFloat()

                // Far-away sound point: 220Hz, starts at 8 min, fades out at 40 min
                val t8 = 8.0 * 60.0
                val t33 = 33.0 * 60.0
                val t40 = 40.0 * 60.0
                var farL = 0f
                var farR = 0f

                if (elapsedSeconds >= t8 && elapsedSeconds < t40) {
                    t2FarAwayPhase += (2.0 * PI * 220.0) / sampleRate
                    if (t2FarAwayPhase > 2.0 * PI) t2FarAwayPhase -= 2.0 * PI
                    val rawFar = sin(t2FarAwayPhase).toFloat()

                    // Params interpolation
                    val t = ((elapsedSeconds - t8) / (25.0 * 60.0)).coerceIn(0.0, 1.0).toFloat()
                    
                    // Gradual fade-in for the "远去声点" starting at 8th minute (over 10 seconds, satisfying >= 2s linear fade)
                    val entryFade = ((elapsedSeconds - t8) / 10.0).coerceIn(0.0, 1.0).toFloat()
                    
                    val baseVol = 0.25f + (0.06f - 0.25f) * t
                    val targetVol = if (elapsedSeconds < t33) {
                        baseVol * entryFade
                    } else {
                        val fadeOutT = ((elapsedSeconds - t33) / (7.0 * 60.0)).coerceIn(0.0, 1.0).toFloat()
                        0.06f * (1f - fadeOutT)
                    }
                    val cutoff = 2000f + (500f - 2000f) * t
                    val reverbWet = 0.20f + (0.85f - 0.20f) * t

                    // SVF lowpass filtering
                    val filteredL = svfLeft.processLowPass(rawFar, cutoff, sampleRate.toFloat())
                    val filteredR = svfRight.processLowPass(rawFar, cutoff, sampleRate.toFloat())

                    // Reverb processing with decorrelated delay lines for beautiful stereo width
                    val revOutL = reverb2Left.process(filteredL)
                    val revOutR = reverb2Right.process(filteredR)

                    // Dry/Wet mixing
                    farL = (filteredL * (1f - reverbWet) + revOutL * reverbWet) * targetVol
                    farR = (filteredR * (1f - reverbWet) + revOutR * reverbWet) * targetVol
                }

                // Uncorrelated pink noise 30% for spatial de-anchoring
                val noiseL = pinkGenL.nextSample() * 0.30f
                val noiseR = pinkGenR.nextSample() * 0.30f

                sampleL = bL + noiseL + farL
                sampleR = bR + noiseR + farR

                // Slow output gain modulation (period 12s, depth ±12%)
                t2LfoPhase += (2.0 * PI) / (sampleRate * 12.0)
                if (t2LfoPhase > 2.0 * PI) t2LfoPhase -= 2.0 * PI
                val tremolo = 1.0f + 0.12f * sin(t2LfoPhase).toFloat()
                sampleL *= tremolo
                sampleR *= tremolo
            }
            3 -> {
                // Theme 3: 辽阔(宏大感)
                val carrier = 110.0
                val diff = 6.0

                t3PhaseL += (2.0 * PI * carrier) / sampleRate
                if (t3PhaseL > 2.0 * PI) t3PhaseL -= 2.0 * PI
                t3PhaseR += (2.0 * PI * (carrier + diff)) / sampleRate
                if (t3PhaseR > 2.0 * PI) t3PhaseR -= 2.0 * PI

                // 2. 主题3各层增益下调留出裕量: 双耳节拍层乘 0.55
                var bL = sin(t3PhaseL).toFloat() * 0.55f
                var bR = sin(t3PhaseR).toFloat() * 0.55f

                // Sub-bass layer: 40Hz with 4s fade-in (低频垫由 0.50 改为 0.35)
                val subFadeLimit = (sampleRate * 4.0).toLong()
                if (t3SubFadeSamples < subFadeLimit) {
                    t3SubFadeSamples++
                }
                val subFade = t3SubFadeSamples.toFloat() / subFadeLimit.toFloat()
                t3SubPhase += (2.0 * PI * 40.0) / sampleRate
                if (t3SubPhase > 2.0 * PI) t3SubPhase -= 2.0 * PI
                val subBass = sin(t3SubPhase).toFloat() * 0.35f * subFade
                bL += subBass
                bR += subBass

                // Shepard Tone: 6 octaves, Shepard 层由 0.30 改为 0.25
                val periodSeconds = 360.0
                val p = (elapsedSeconds / periodSeconds) % 1.0
                var shepardSum = 0f

                val fMin = 30.0
                val fMax = fMin * Math.pow(2.0, 6.0)

                for (k in 0..5) {
                    val x = ((k.toDouble() + p) / 6.0) % 1.0
                    val freq = fMin * Math.pow(fMax / fMin, x)
                    shepardPhases[k] += (2.0 * PI * freq) / sampleRate
                    if (shepardPhases[k] > 2.0 * PI) shepardPhases[k] -= 2.0 * PI

                    val sigma = 0.15
                    val weight = exp(-Math.pow(x - 0.5, 2.0) / (2.0 * sigma * sigma)).toFloat()
                    shepardSum += sin(shepardPhases[k]).toFloat() * weight
                }

                val shepardSample = shepardSum * 0.25f
                bL += shepardSample
                bR += shepardSample

                // Reverb processing with decorrelated delay lines for deep spaciousness
                val wetL = reverb3Left.process(bL)
                val wetR = reverb3Right.process(bR)

                // 混响后总输出再乘 0.85
                sampleL = (bL * 0.5f + wetL * 0.5f) * 0.85f
                sampleR = (bR * 0.5f + wetR * 0.5f) * 0.85f
            }
            4 -> {
                // Theme 4: 心流(专注写作)
                val carrier = 120.0
                val diff = 8.0

                t4PhaseL += (2.0 * PI * carrier) / sampleRate
                if (t4PhaseL > 2.0 * PI) t4PhaseL -= 2.0 * PI
                t4PhaseR += (2.0 * PI * (carrier + diff)) / sampleRate
                if (t4PhaseR > 2.0 * PI) t4PhaseR -= 2.0 * PI

                // Brown Noise 30%
                val brownNoise = nextBrown() * 0.30f

                sampleL = sin(t4PhaseL).toFloat() + brownNoise
                sampleR = sin(t4PhaseR).toFloat() + brownNoise

                sampleL *= 0.70f
                sampleR *= 0.70f
            }
            5 -> {
                // Theme 5: 意象流(半醒自由联想)
                val targetDiffA = 5.5
                val targetDiffB = 5.2

                val diffA = when {
                    elapsedSeconds < 300.0 -> lerp(8.0, targetDiffA, elapsedSeconds / 300.0)
                    elapsedSeconds < 2100.0 -> targetDiffA
                    else -> lerp(targetDiffA, 7.0, (elapsedSeconds - 2100.0) / 300.0)
                }

                val diffB = when {
                    elapsedSeconds < 300.0 -> lerp(8.0, targetDiffB, elapsedSeconds / 300.0)
                    elapsedSeconds < 2100.0 -> targetDiffB
                    else -> lerp(targetDiffB, 7.0, (elapsedSeconds - 2100.0) / 300.0)
                }

                t5PhaseL += (2.0 * PI * 100.0) / sampleRate
                if (t5PhaseL > 2.0 * PI) t5PhaseL -= 2.0 * PI
                t5PhaseR += (2.0 * PI * (100.0 + diffA)) / sampleRate
                if (t5PhaseR > 2.0 * PI) t5PhaseR -= 2.0 * PI

                t5PhaseL2 += (2.0 * PI * 103.0) / sampleRate
                if (t5PhaseL2 > 2.0 * PI) t5PhaseL2 -= 2.0 * PI
                t5PhaseR2 += (2.0 * PI * (103.0 + diffB)) / sampleRate
                if (t5PhaseR2 > 2.0 * PI) t5PhaseR2 -= 2.0 * PI

                val groupA_L = sin(t5PhaseL).toFloat()
                val groupA_R = sin(t5PhaseR).toFloat()

                val groupB_L = sin(t5PhaseL2).toFloat() * 0.60f
                val groupB_R = sin(t5PhaseR2).toFloat() * 0.60f

                val noiseSample = pinkGenL.nextSample() * 0.25f

                sampleL = (groupA_L + groupB_L) + noiseSample
                sampleR = (groupA_R + groupB_R) + noiseSample
            }
            6 -> {
                // Theme 6: 清明梦(整夜睡眠)
                val t20 = 20.0 * 60.0
                val t40 = 40.0 * 60.0
                val alarmStartA = alarmTimeA.toDouble() * 3600.0
                val alarmEndA = alarmStartA + 15.0 * 60.0
                val alarmStartB = alarmTimeB.toDouble() * 3600.0
                val alarmEndB = alarmStartB + 15.0 * 60.0

                var activeDiff = 2.0
                var activeVolumeMultiplier = 0f
                var isAlarmPhase = false
                var alarmPeakVol = calibrationVolume

                when {
                    elapsedSeconds < t20 -> {
                        activeDiff = lerp(9.0, 2.0, elapsedSeconds / t20)
                        activeVolumeMultiplier = 1.0f
                    }
                    elapsedSeconds < t40 -> {
                        activeDiff = 2.0
                        activeVolumeMultiplier = 1.0f - ((elapsedSeconds - t20) / (20.0 * 60.0)).toFloat()
                    }
                    elapsedSeconds >= alarmStartA && elapsedSeconds < alarmEndA -> {
                        activeDiff = 5.5
                        isAlarmPhase = true
                        alarmPeakVol = calibrationVolume

                        val elapsedAlarm = elapsedSeconds - alarmStartA
                        activeVolumeMultiplier = when {
                            elapsedAlarm < 15.0 -> (elapsedAlarm / 15.0).toFloat() * alarmPeakVol
                            elapsedAlarm < 600.0 -> alarmPeakVol
                            elapsedAlarm < 780.0 -> {
                                val t = ((elapsedAlarm - 600.0) / 180.0).toFloat()
                                alarmPeakVol * (1f - t)
                            }
                            else -> 0f
                        }
                    }
                    elapsedSeconds >= alarmStartB && elapsedSeconds < alarmEndB -> {
                        activeDiff = 5.5
                        isAlarmPhase = true
                        alarmPeakVol = calibrationVolume * 1.2f

                        val elapsedAlarm = elapsedSeconds - alarmStartB
                        activeVolumeMultiplier = when {
                            elapsedAlarm < 15.0 -> (elapsedAlarm / 15.0).toFloat() * alarmPeakVol
                            elapsedAlarm < 600.0 -> alarmPeakVol
                            elapsedAlarm < 780.0 -> {
                                val t = ((elapsedAlarm - 600.0) / 180.0).toFloat()
                                alarmPeakVol * (1f - t)
                            }
                            else -> 0f
                        }
                    }
                    else -> {
                        activeVolumeMultiplier = 0f
                    }
                }

                if (activeVolumeMultiplier > 0.0001f) {
                    t6PhaseL += (2.0 * PI * 100.0) / sampleRate
                    if (t6PhaseL > 2.0 * PI) t6PhaseL -= 2.0 * PI
                    t6PhaseR += (2.0 * PI * (100.0 + activeDiff)) / sampleRate
                    if (t6PhaseR > 2.0 * PI) t6PhaseR -= 2.0 * PI

                    var bL = sin(t6PhaseL).toFloat()
                    var bR = sin(t6PhaseR).toFloat()

                    // Add pink noise only in the first 40 minutes (35% volume) with smooth 10s fade-out (>= 2s)
                    val noiseFade = ((t40 - elapsedSeconds) / 10.0).coerceIn(0.0, 1.0).toFloat()
                    if (noiseFade > 0f) {
                        val noise = pinkGenL.nextSample() * 0.35f * noiseFade
                        bL += noise
                        bR += noise
                    }

                    sampleL = bL * activeVolumeMultiplier
                    sampleR = bR * activeVolumeMultiplier

                    // Alarm prompt addition
                    if (isAlarmPhase && promptEnabled) {
                        if (theme6PromptIntervalSamplesLeft <= 0L) {
                            theme6PromptIntervalSamplesLeft = (sampleRate * 45.0).toLong()
                            theme6PromptSamplesLeft = (sampleRate * 2.4).toLong()
                        }
                        theme6PromptIntervalSamplesLeft--

                        if (theme6PromptSamplesLeft > 0L) {
                            val totalPromptSamples = (sampleRate * 2.4).toLong()
                            val elapsedSamples = totalPromptSamples - theme6PromptSamplesLeft
                            val inToneSamples = elapsedSamples % (sampleRate * 0.8).toLong()
                            val toneIdx = elapsedSamples / (sampleRate * 0.8).toLong()
                            val freq = when (toneIdx) {
                                0L -> 660.0
                                1L -> 550.0
                                else -> 440.0
                            }

                            theme6PromptPhase += (2.0 * PI * freq) / sampleRate
                            if (theme6PromptPhase > 2.0 * PI) theme6PromptPhase -= 2.0 * PI

                            val toneLength = (sampleRate * 0.8).toLong()
                            val fadeLength = (sampleRate * 0.3).toLong()
                            val env = when {
                                inToneSamples < fadeLength -> inToneSamples.toFloat() / fadeLength
                                inToneSamples > toneLength - fadeLength -> (toneLength - inToneSamples).toFloat() / fadeLength
                                else -> 1f
                            }

                            val promptValue = sin(theme6PromptPhase).toFloat() * env * (alarmPeakVol * 0.5f)
                            sampleL += promptValue
                            sampleR += promptValue
                            theme6PromptSamplesLeft--
                        }
                    }
                } else {
                    theme6PromptIntervalSamplesLeft = 0L
                    theme6PromptSamplesLeft = 0L
                }
            }
        }

        outThemeL = sampleL
        outThemeR = sampleR
    }

    private fun lerp(start: Double, end: Double, fraction: Double): Double {
        return start + (end - start) * fraction
    }

    private fun nextBrown(): Float {
        return pinkGenL.nextSample() * 1.5f
    }
}

// Simple pink noise generator class with specific seeds
class PinkNoiseGenerator(seed: Long) {
    private val random = java.util.Random(seed)
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    fun nextSample(): Float {
        val white = random.nextFloat() * 2.0f - 1.0f
        b0 = 0.99886f * b0 + white * 0.0555179f
        b1 = 0.99332f * b1 + white * 0.0750759f
        b2 = 0.96900f * b2 + white * 0.1538520f
        b3 = 0.86650f * b3 + white * 0.3104856f
        b4 = 0.55000f * b4 + white * 0.5329522f
        b5 = -0.7616f * b5 - white * 0.0168980f
        val pink = b0 + b1 + b2 + b3 + b4 + b5 + b6 + white * 0.5362f
        b6 = white * 0.115926f
        return pink * 0.11f
    }
}

// Schroeder Reverb implementation (4 comb filters in parallel, 2 all-pass in series)
class SchroederReverb(
    private val combDelays: IntArray = intArrayOf(1116, 1356, 1422, 1656),
    private val allPassDelays: IntArray = intArrayOf(225, 341),
    private val combFeedback: Float = 0.90f
) {
    private val combFilters = combDelays.map { CombFilter(it, combFeedback) }
    private val allPassFilters = allPassDelays.map { AllPassFilter(it, 0.5f) }

    fun process(input: Float): Float {
        var combOutput = 0f
        val numCombs = combFilters.size
        for (i in 0 until numCombs) {
            combOutput += combFilters[i].process(input)
        }
        combOutput *= 0.25f

        var out = combOutput
        val numAps = allPassFilters.size
        for (i in 0 until numAps) {
            out = allPassFilters[i].process(out)
        }
        return out
    }

    fun reset() {
        combFilters.forEach { it.reset() }
        allPassFilters.forEach { it.reset() }
    }
}

class CombFilter(val delaySamples: Int, var feedback: Float) {
    private val buffer = FloatArray(delaySamples)
    private var writeIdx = 0

    fun process(input: Float): Float {
        val output = buffer[writeIdx]
        val newValue = input + output * feedback
        buffer[writeIdx] = newValue
        writeIdx = (writeIdx + 1) % delaySamples
        return output
    }

    fun reset() {
        buffer.fill(0f)
        writeIdx = 0
    }
}

class AllPassFilter(val delaySamples: Int, var feedback: Float) {
    private val buffer = FloatArray(delaySamples)
    private var writeIdx = 0

    fun process(input: Float): Float {
        val output = buffer[writeIdx]
        val newValue = input + output * feedback
        buffer[writeIdx] = newValue
        writeIdx = (writeIdx + 1) % delaySamples
        return output - feedback * newValue
    }

    fun reset() {
        buffer.fill(0f)
        writeIdx = 0
    }
}

// State Variable Filter (Chamberlin SVF)
class StateVariableFilter {
    private var low = 0f
    private var band = 0f

    fun reset() {
        low = 0f
        band = 0f
    }

    fun processLowPass(input: Float, cutoffHz: Float, sampleRate: Float, q: Float = 0.707f): Float {
        val f = (Math.PI * cutoffHz / sampleRate).toFloat().coerceIn(0.01f, 0.99f)
        val qInv = 1.0f / q
        val hp = input - low - qInv * band
        band += f * hp
        low += f * band
        return low
    }
}
