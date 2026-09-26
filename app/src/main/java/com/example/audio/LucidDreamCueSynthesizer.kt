package com.example.audio

import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural synthesizer for Lucid Dream Reality Check Audio Cues (WBTB).
 * Designed scientifically to infiltrate the dreaming mind (REM stage)
 * with high-contrast, anomalous acoustic overtones that stand out from background soundscapes
 * (especially deep ocean waves and low-frequency rumble) without jarring the user awake.
 */
class LucidDreamCueSynthesizer {

    private val sampleRate = 44100.0

    // Cue playback state
    private var isPlaying = false
    private var currentCueType = 1
    private var cueElapsedSamples = 0L
    private var cueTotalSamples = 0L

    // Type 1: 528Hz & 660Hz Solfeggio Dual-Tone Lucid Bell (🌟 Deep Sea Odyssey Best Match)
    private var bellPhaseA = 0.0
    private var bellPhaseB = 0.0

    // Type 2: Crystal Wind Chimes (4 high-register crystalline chimes)
    private val chimePhases = DoubleArray(4)
    private val chimeFreqs = doubleArrayOf(2093.0, 2637.0, 3136.0, 3520.0) // C7, E7, G7, A7
    private val chimeDelays = doubleArrayOf(0.0, 0.45, 0.95, 1.45)

    // Type 3: Dream Music Box (4 delicate metallic tines: C6, G6, E6, C7)
    private val musicBoxPhases = DoubleArray(4)
    private val musicBoxFreqs = doubleArrayOf(1046.5, 1567.98, 1318.5, 2093.0)
    private val musicBoxDelays = doubleArrayOf(0.0, 0.7, 1.4, 2.1)

    // Type 4: Crystal Dew Drops (3 rhythmic serene droplets)
    private var dropPhase = 0.0

    // Type 5: Zen Singing Bowl & Tibetan Bell (for users listening to rain/fire without bowl)
    private var bowlPhase1 = 0.0
    private var bowlPhase2 = 0.0
    private var bowlPhase3 = 0.0
    private var bowlLfoPhase = 0.0

    // Schroeder Reverb for ethereal acoustic aura
    private val reverbLeft = SchroederReverb(
        combDelays = intArrayOf(1116, 1356, 1422, 1656),
        allPassDelays = intArrayOf(225, 341),
        combFeedback = 0.82f
    )
    private val reverbRight = SchroederReverb(
        combDelays = intArrayOf(1187, 1311, 1481, 1613),
        allPassDelays = intArrayOf(251, 317),
        combFeedback = 0.82f
    )

    fun triggerCue(cueType: Int) {
        currentCueType = cueType
        cueElapsedSamples = 0L
        isPlaying = true

        // Total duration per cue type (~5.5 to 6.5 seconds for complete natural decay)
        cueTotalSamples = when (cueType) {
            1 -> (sampleRate * 6.0).toLong() // 2-tone bell
            2 -> (sampleRate * 5.5).toLong() // Crystal wind chimes
            3 -> (sampleRate * 6.5).toLong() // Dream music box
            4 -> (sampleRate * 5.0).toLong() // 3 crystal dew drops
            5 -> (sampleRate * 6.5).toLong() // Zen singing bowl
            else -> (sampleRate * 6.0).toLong()
        }

        // Reset all phases
        bellPhaseA = 0.0
        bellPhaseB = 0.0
        for (i in chimePhases.indices) chimePhases[i] = 0.0
        for (i in musicBoxPhases.indices) musicBoxPhases[i] = 0.0
        dropPhase = 0.0
        bowlPhase1 = 0.0
        bowlPhase2 = 0.0
        bowlPhase3 = 0.0
        bowlLfoPhase = 0.0
    }

    fun stopCue() {
        isPlaying = false
        cueElapsedSamples = 0L
    }

    val isActive: Boolean
        get() = isPlaying

    fun nextSample(): Float {
        if (!isPlaying) return 0f

        if (cueElapsedSamples >= cueTotalSamples) {
            isPlaying = false
            return 0f
        }

        val t = cueElapsedSamples.toDouble() / sampleRate
        var rawSample = 0f

        when (currentCueType) {
            1 -> {
                // Type 1: 现实检验双音阶梦钟 (528Hz & 660Hz) - 与深海低音反差极强
                // Tone A: 528Hz at t = 0s
                val attackA = 0.05
                val decayA = if (t >= 0.0) {
                    val dtA = t
                    val att = if (dtA < attackA) (dtA / attackA).toFloat() else 1f
                    att * exp(-dtA * 0.72).toFloat()
                } else 0f

                bellPhaseA += (2.0 * PI * 528.0) / sampleRate
                if (bellPhaseA > 2.0 * PI) bellPhaseA -= 2.0 * PI
                // Add gentle overtone at 1056Hz
                val sampleA = (sin(bellPhaseA) + 0.3 * sin(bellPhaseA * 2.0)).toFloat() * decayA * 0.60f

                // Tone B: 660Hz at t = 1.2s
                var sampleB = 0f
                if (t >= 1.2) {
                    val dtB = t - 1.2
                    val attackB = 0.05
                    val attB = if (dtB < attackB) (dtB / attackB).toFloat() else 1f
                    val decayB = attB * exp(-dtB * 0.72).toFloat()

                    bellPhaseB += (2.0 * PI * 660.0) / sampleRate
                    if (bellPhaseB > 2.0 * PI) bellPhaseB -= 2.0 * PI
                    sampleB = (sin(bellPhaseB) + 0.3 * sin(bellPhaseB * 2.0)).toFloat() * decayB * 0.55f
                }

                rawSample = sampleA + sampleB
            }

            2 -> {
                // Type 2: 梦境水晶风铃 (Crystal Wind Chimes)
                // 4 delicate high-frequency metallic crystal strikes (2093Hz ~ 3520Hz)
                var chimeMix = 0f
                for (i in chimeFreqs.indices) {
                    val startT = chimeDelays[i]
                    if (t >= startT) {
                        val dt = t - startT
                        chimePhases[i] += (2.0 * PI * chimeFreqs[i]) / sampleRate
                        if (chimePhases[i] > 2.0 * PI) chimePhases[i] -= 2.0 * PI

                        val attack = if (dt < 0.02) (dt / 0.02).toFloat() else 1f
                        val decay = attack * exp(-dt * 1.8).toFloat()
                        // Pure sine with high shimmer
                        val tone = (sin(chimePhases[i]) + 0.15 * sin(chimePhases[i] * 2.76)).toFloat()
                        chimeMix += tone * decay * 0.32f
                    }
                }
                rawSample = chimeMix
            }

            3 -> {
                // Type 3: 灵性八音盒 (Dream Music Box)
                // 4 pure mechanical tine notes (C6, G6, E6, C7)
                var musicBoxMix = 0f
                for (i in musicBoxFreqs.indices) {
                    val startT = musicBoxDelays[i]
                    if (t >= startT) {
                        val dt = t - startT
                        musicBoxPhases[i] += (2.0 * PI * musicBoxFreqs[i]) / sampleRate
                        if (musicBoxPhases[i] > 2.0 * PI) musicBoxPhases[i] -= 2.0 * PI

                        val attack = if (dt < 0.015) (dt / 0.015).toFloat() else 1f
                        val decay = attack * exp(-dt * 2.1).toFloat()
                        // Music box characteristics: dominant fundamental + subtle octave + decay
                        val tine = (sin(musicBoxPhases[i]) + 0.22 * sin(musicBoxPhases[i] * 2.0)).toFloat()
                        musicBoxMix += tine * decay * 0.40f
                    }
                }
                rawSample = musicBoxMix
            }

            4 -> {
                // Type 4: 幽潭灵露 (3 Rhythmic Crystal Drops at 0.0s, 0.85s, 1.7s)
                val dropTimes = doubleArrayOf(0.0, 0.85, 1.7)
                val baseFreqs = doubleArrayOf(1250.0, 1500.0, 1750.0)

                var dropSample = 0f
                for (i in dropTimes.indices) {
                    val dropStart = dropTimes[i]
                    if (t >= dropStart && t < dropStart + 1.2) {
                        val dt = t - dropStart
                        val curFreq = if (dt < 0.025) {
                            baseFreqs[i] + (dt / 0.025) * 450.0
                        } else {
                            baseFreqs[i] + 450.0 - ((dt - 0.025) * 150.0)
                        }

                        dropPhase += (2.0 * PI * curFreq) / sampleRate
                        if (dropPhase > 2.0 * PI) dropPhase -= 2.0 * PI

                        val attack = if (dt < 0.015) (dt / 0.015).toFloat() else 1f
                        val decay = attack * exp(-dt * 4.2).toFloat()
                        dropSample += sin(dropPhase).toFloat() * decay * 0.55f
                    }
                }
                rawSample = dropSample
            }

            5 -> {
                // Type 5: 传统空灵颂钵 (Zen Tibetan Singing Bowl) - 供无钵声背景使用
                bowlPhase1 += (2.0 * PI * 216.0) / sampleRate
                if (bowlPhase1 > 2.0 * PI) bowlPhase1 -= 2.0 * PI

                bowlPhase2 += (2.0 * PI * 432.0) / sampleRate
                if (bowlPhase2 > 2.0 * PI) bowlPhase2 -= 2.0 * PI

                bowlPhase3 += (2.0 * PI * 864.0) / sampleRate
                if (bowlPhase3 > 2.0 * PI) bowlPhase3 -= 2.0 * PI

                bowlLfoPhase += (2.0 * PI * 0.25) / sampleRate
                if (bowlLfoPhase > 2.0 * PI) bowlLfoPhase -= 2.0 * PI

                val attackDuration = 0.08
                val attackEnv = if (t < attackDuration) (t / attackDuration).toFloat() else 1f

                val decay1 = exp(-t * 0.45).toFloat()
                val decay2 = exp(-t * 0.65).toFloat()
                val decay3 = exp(-t * 0.95).toFloat()

                val tremolo = 0.85f + 0.15f * sin(bowlLfoPhase).toFloat()

                val s1 = sin(bowlPhase1).toFloat() * decay1 * 0.50f
                val s2 = sin(bowlPhase2).toFloat() * decay2 * 0.35f
                val s3 = sin(bowlPhase3).toFloat() * decay3 * 0.15f

                rawSample = (s1 + s2 + s3) * tremolo * attackEnv
            }
        }

        // Apply Schroeder Reverb for spatial immersion and dream-like acoustic aura
        val wet = reverbLeft.process(rawSample)
        val combined = rawSample * 0.72f + wet * 0.38f

        cueElapsedSamples++
        return combined.coerceIn(-1.0f, 1.0f)
    }
}
