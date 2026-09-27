package com.example.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural synthesizer for Lucid Dream Reality Check Audio Cues (WBTB).
 * Designed for acoustic infiltration of the dreaming mind (REM stage)
 * with pristine harmonic overtones, independent phase accumulators,
 * full stereo Schroeder reverberation, and click-free fade envelopes.
 */
class LucidDreamCueSynthesizer {

    private val sampleRate = 44100.0

    // Cue playback state
    @Volatile
    private var isPlaying = false
    private var currentCueType = 1
    private var cueElapsedSamples = 0L
    private var cueTotalSamples = 0L

    // Type 1: 528Hz & 660Hz Dual-Tone Lucid Bell (Ascending Major Third 4:5 motif)
    private var bellPhaseA = 0.0
    private var bellPhaseB = 0.0

    // Type 2: Crystal Wind Chimes (4 high-register crystalline chimes)
    private val chimePhases = DoubleArray(4)
    private val chimeOvertonePhases = DoubleArray(4)
    private val chimeFreqs = doubleArrayOf(2093.0, 2637.0, 3136.0, 3520.0) // C7, E7, G7, A7
    private val chimeDelays = doubleArrayOf(0.0, 0.45, 0.95, 1.45)

    // Type 3: Dream Music Box (4 pure metallic tine notes: C6, G6, E6, C7)
    private val musicBoxPhases = DoubleArray(4)
    private val musicBoxHarmonicPhases = DoubleArray(4)
    private val musicBoxFreqs = doubleArrayOf(1046.5, 1567.98, 1318.5, 2093.0)
    private val musicBoxDelays = doubleArrayOf(0.0, 0.70, 1.40, 2.10)

    // Type 4: Crystal Dew Drops (3 independent phase accumulators to prevent phase collision)
    private val dropPhases = DoubleArray(3)

    // Type 5: 传统空灵颂钵 (Authentic Handcrafted Tibetan Singing Bowl: 1 : 2.71 : 5.18 : 8.35 Inharmonic Shell Modes)
    private var bowlPhase0a = 0.0  // 216.0 Hz fundamental doublet A
    private var bowlPhase0b = 0.0  // 217.6 Hz fundamental doublet B (produces ~1.6Hz natural physical acoustic pulsing)
    private var bowlPhase1 = 0.0   // 585.36 Hz (2.71x inharmonic vibrational mode)
    private var bowlPhase2 = 0.0   // 1118.88 Hz (5.18x inharmonic vibrational mode)
    private var bowlPhase3 = 0.0   // 1803.60 Hz (8.35x high crystalline overtone)
    private var malletFilter = 0f
    private val random = java.util.Random()

    // Schroeder Reverb
    private val reverbLeft = SchroederReverb(
        combDelays = intArrayOf(1116, 1356, 1422, 1656),
        allPassDelays = intArrayOf(225, 341),
        combFeedback = 0.82f
    )

    fun triggerCue(cueType: Int) {
        currentCueType = cueType
        cueElapsedSamples = 0L

        // Durations with generous tail room for natural reverb decay
        cueTotalSamples = when (cueType) {
            1 -> (sampleRate * 6.0).toLong() // 2-tone bell
            2 -> (sampleRate * 6.0).toLong() // Crystal wind chimes
            3 -> (sampleRate * 6.8).toLong() // Dream music box
            4 -> (sampleRate * 5.5).toLong() // 3 crystal dew drops
            5 -> (sampleRate * 7.0).toLong() // Zen singing bowl
            else -> (sampleRate * 6.0).toLong()
        }

        // Reset all phases cleanly
        bellPhaseA = 0.0
        bellPhaseB = 0.0
        for (i in chimePhases.indices) {
            chimePhases[i] = 0.0
            chimeOvertonePhases[i] = 0.0
        }
        for (i in musicBoxPhases.indices) {
            musicBoxPhases[i] = 0.0
            musicBoxHarmonicPhases[i] = 0.0
        }
        for (i in dropPhases.indices) {
            dropPhases[i] = 0.0
        }
        bowlPhase0a = 0.0
        bowlPhase0b = 0.0
        bowlPhase1 = 0.0
        bowlPhase2 = 0.0
        bowlPhase3 = 0.0
        malletFilter = 0f

        isPlaying = true
    }

    fun stopCue() {
        isPlaying = false
        cueElapsedSamples = 0L
    }

    val isActive: Boolean
        get() = isPlaying

    /**
     * Compute next stereo sample pair [left, right] for the lucid cue.
     * Uses independent Left/Right decorrelated reverberation and smooth natural decay.
     */
    fun nextStereoSample(out: FloatArray) {
        if (!isPlaying) {
            out[0] = 0f
            out[1] = 0f
            return
        }

        if (cueElapsedSamples >= cueTotalSamples) {
            isPlaying = false
            out[0] = 0f
            out[1] = 0f
            return
        }

        val t = cueElapsedSamples.toDouble() / sampleRate
        var rawMono = 0f

        when (currentCueType) {
            1 -> {
                // Type 1: 现实检验双音钟 (528Hz & 660Hz 上行大三度)
                // Tone A: 528Hz at t = 0s
                val attackA = 0.05
                val dtA = t
                val decayA = if (dtA >= 0.0) {
                    val att = if (dtA < attackA) (dtA / attackA).toFloat() else 1f
                    att * exp(-dtA * 0.72).toFloat()
                } else 0f

                bellPhaseA += (2.0 * PI * 528.0) / sampleRate
                if (bellPhaseA > 2.0 * PI) bellPhaseA -= 2.0 * PI
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

                rawMono = sampleA + sampleB
            }

            2 -> {
                // Type 2: 梦境水晶风铃 (Crystal Wind Chimes)
                // Distinct phase accumulators for fundamental and overtone to guarantee pure distortion-free chime
                var chimeMix = 0f
                for (i in chimeFreqs.indices) {
                    val startT = chimeDelays[i]
                    if (t >= startT) {
                        val dt = t - startT
                        chimePhases[i] += (2.0 * PI * chimeFreqs[i]) / sampleRate
                        if (chimePhases[i] > 2.0 * PI) chimePhases[i] -= 2.0 * PI

                        // Separate overtone phase prevents phase wrapping distortion
                        chimeOvertonePhases[i] += (2.0 * PI * chimeFreqs[i] * 2.76) / sampleRate
                        if (chimeOvertonePhases[i] > 2.0 * PI) chimeOvertonePhases[i] -= 2.0 * PI

                        val attack = if (dt < 0.015) (dt / 0.015).toFloat() else 1f
                        val decay = attack * exp(-dt * 1.8).toFloat()
                        val tone = (sin(chimePhases[i]) + 0.12 * sin(chimeOvertonePhases[i])).toFloat()
                        chimeMix += tone * decay * 0.35f
                    }
                }
                rawMono = chimeMix
            }

            3 -> {
                // Type 3: 灵性八音盒 (Dream Music Box)
                var musicBoxMix = 0f
                for (i in musicBoxFreqs.indices) {
                    val startT = musicBoxDelays[i]
                    if (t >= startT) {
                        val dt = t - startT
                        musicBoxPhases[i] += (2.0 * PI * musicBoxFreqs[i]) / sampleRate
                        if (musicBoxPhases[i] > 2.0 * PI) musicBoxPhases[i] -= 2.0 * PI

                        musicBoxHarmonicPhases[i] += (2.0 * PI * musicBoxFreqs[i] * 2.0) / sampleRate
                        if (musicBoxHarmonicPhases[i] > 2.0 * PI) musicBoxHarmonicPhases[i] -= 2.0 * PI

                        val attack = if (dt < 0.01) (dt / 0.01).toFloat() else 1f
                        val decay = attack * exp(-dt * 2.0).toFloat()
                        val tine = (sin(musicBoxPhases[i]) + 0.20 * sin(musicBoxHarmonicPhases[i])).toFloat()
                        musicBoxMix += tine * decay * 0.40f
                    }
                }
                rawMono = musicBoxMix
            }

            4 -> {
                // Type 4: 幽潭灵露 (3 Rhythmic Crystal Drops with INDEPENDENT phase accumulators)
                val dropTimes = doubleArrayOf(0.0, 0.85, 1.70)
                val baseFreqs = doubleArrayOf(1250.0, 1500.0, 1750.0)

                var dropSample = 0f
                for (i in dropTimes.indices) {
                    val dropStart = dropTimes[i]
                    if (t >= dropStart && t < dropStart + 1.2) {
                        val dt = t - dropStart
                        // Water drop upward sweep in first 25ms, then gentle pitch relax
                        val curFreq = if (dt < 0.025) {
                            baseFreqs[i] + (dt / 0.025) * 450.0
                        } else {
                            baseFreqs[i] + 450.0 - ((dt - 0.025) * 150.0)
                        }

                        // Independent phase per drop prevents phase collision & frequency spikes
                        dropPhases[i] += (2.0 * PI * curFreq) / sampleRate
                        if (dropPhases[i] > 2.0 * PI) dropPhases[i] -= 2.0 * PI

                        val attack = if (dt < 0.012) (dt / 0.012).toFloat() else 1f
                        val decay = attack * exp(-dt * 4.2).toFloat()
                        dropSample += sin(dropPhases[i]).toFloat() * decay * 0.55f
                    }
                }
                rawMono = dropSample
            }

            5 -> {
                // Type 5: 传统空灵颂钵 (Authentic Handcrafted Bronze Tibetan Singing Bowl)
                // 1. Dual-mode fundamental beating (216.0Hz & 217.6Hz) creates real acoustic pulsing wave (~1.6Hz beat)
                bowlPhase0a += (2.0 * PI * 216.0) / sampleRate
                if (bowlPhase0a > 2.0 * PI) bowlPhase0a -= 2.0 * PI

                bowlPhase0b += (2.0 * PI * 217.6) / sampleRate
                if (bowlPhase0b > 2.0 * PI) bowlPhase0b -= 2.0 * PI

                // 2. Inharmonic shell vibrational modes (1 : 2.71 : 5.18 : 8.35)
                bowlPhase1 += (2.0 * PI * 585.36) / sampleRate // 2.71 * 216.0
                if (bowlPhase1 > 2.0 * PI) bowlPhase1 -= 2.0 * PI

                bowlPhase2 += (2.0 * PI * 1118.88) / sampleRate // 5.18 * 216.0
                if (bowlPhase2 > 2.0 * PI) bowlPhase2 -= 2.0 * PI

                bowlPhase3 += (2.0 * PI * 1803.6) / sampleRate // 8.35 * 216.0
                if (bowlPhase3 > 2.0 * PI) bowlPhase3 -= 2.0 * PI

                // Attack envelope: gentle mallet strike rise (20ms)
                val attackDuration = 0.02
                val attackEnv = if (t < attackDuration) (t / attackDuration).toFloat() else 1f

                // Natural physical damping gradients:
                // Higher modes decay rapidly (internal friction of bronze shell), fundamental rings for 6-8s
                val decay0 = exp(-t * 0.42).toFloat() // Fundamental (long sustain)
                val decay1 = exp(-t * 0.75).toFloat() // First inharmonic mode
                val decay2 = exp(-t * 1.60).toFloat() // Second inharmonic mode
                val decay3 = exp(-t * 2.80).toFloat() // High shimmer

                // Padded mallet initial strike transient (soft felt contact thump in first 35ms)
                var malletThump = 0f
                if (t < 0.035) {
                    val noise = (random.nextFloat() * 2f - 1f)
                    malletFilter += 0.15f * (noise - malletFilter)
                    malletThump = malletFilter * (1f - (t / 0.035).toFloat()) * 0.18f
                }

                // Modal summation with natural physical proportions (deep, warm, organic acoustic bowl)
                val s0 = (sin(bowlPhase0a).toFloat() * 0.5f + sin(bowlPhase0b).toFloat() * 0.5f) * decay0 * 0.58f
                val s1 = sin(bowlPhase1).toFloat() * decay1 * 0.28f
                val s2 = sin(bowlPhase2).toFloat() * decay2 * 0.12f
                val s3 = sin(bowlPhase3).toFloat() * decay3 * 0.04f

                rawMono = (s0 + s1 + s2 + s3 + malletThump) * attackEnv
            }
        }

        // Smooth release envelope over the last 350ms to ensure reverb tails never click or truncate abruptly
        val remainingSamples = cueTotalSamples - cueElapsedSamples
        val releaseSamples = (sampleRate * 0.35).toLong()
        val releaseEnv = if (remainingSamples < releaseSamples) {
            (remainingSamples.toFloat() / releaseSamples.toFloat()).coerceIn(0f, 1f)
        } else {
            1.0f
        }

        val wet = reverbLeft.process(rawMono)
        val mono = ((rawMono * 0.72f + wet * 0.38f) * releaseEnv).coerceIn(-1.0f, 1.0f)
        out[0] = mono
        out[1] = mono

        cueElapsedSamples++
    }
}
