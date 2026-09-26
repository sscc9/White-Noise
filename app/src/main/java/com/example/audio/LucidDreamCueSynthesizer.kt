package com.example.audio

import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * Procedural synthesizer for the lucid dream (WBTB) audio cues.
 *
 * Each cue is a short, soft sound meant to stand out from the background soundscape
 * (e.g. low ocean rumble) without being loud enough to wake the sleeper.
 *
 * Threading: [triggerCue] / [stopCue] may be called from any thread. They only post a request;
 * all synthesis state is owned by the audio thread that calls [nextSample].
 */
class LucidDreamCueSynthesizer {

    private val sampleRate = 44100.0

    // Requests posted from other threads, consumed by the audio thread in nextSample()
    private val pendingCueType = AtomicInteger(0) // 0 = no pending request
    private val stopRequested = AtomicBoolean(false)

    // Cue playback state (audio thread only, except isPlaying which is read by isActive)
    @Volatile
    private var isPlaying = false
    private var currentCueType = 1
    private var cueElapsedSamples = 0L
    private var cueDrySamples = 0L

    // After the dry signal ends, keep running the reverb so its tail decays naturally instead of being cut
    private val tailSamples = (sampleRate * 1.5).toLong()
    // Dry signal fades out over its last 0.5s so the cue never ends on an audible step
    private val dryFadeOutSamples = (sampleRate * 0.5).toLong()
    // Final safety fade at the very end of the reverb tail
    private val tailFadeOutSamples = (sampleRate * 0.2).toLong()

    // Type 1: 528Hz & 660Hz dual-tone bell
    private var bellPhaseA = 0.0
    private var bellPhaseB = 0.0

    // Type 2: Crystal Wind Chimes (4 high-register crystalline chimes)
    private val chimePhases = DoubleArray(4)
    private val chimeShimmerPhases = DoubleArray(4) // inharmonic partial at 2.76x, needs its own accumulator
    private val chimeFreqs = doubleArrayOf(2093.0, 2637.0, 3136.0, 3520.0) // C7, E7, G7, A7
    private val chimeDelays = doubleArrayOf(0.0, 0.45, 0.95, 1.45)
    private val chimeShimmerRatio = 2.76

    // Type 3: Dream Music Box (4 delicate metallic tines: C6, G6, E6, C7)
    private val musicBoxPhases = DoubleArray(4)
    private val musicBoxFreqs = doubleArrayOf(1046.5, 1567.98, 1318.5, 2093.0)
    private val musicBoxDelays = doubleArrayOf(0.0, 0.7, 1.4, 2.1)

    // Type 4: Crystal Dew Drops (3 rhythmic droplets; windows overlap, so each drop has its own phase)
    private val dropPhases = DoubleArray(3)
    private val dropTimes = doubleArrayOf(0.0, 0.85, 1.7)
    private val dropBaseFreqs = doubleArrayOf(1250.0, 1500.0, 1750.0)
    private val dropLength = 1.2
    private val dropGateFade = 0.1

    // Type 5: Singing Bowl
    private var bowlPhase1 = 0.0
    private var bowlPhase2 = 0.0
    private var bowlPhase3 = 0.0
    private var bowlLfoPhase = 0.0

    // Schroeder Reverb for ethereal acoustic aura (output is dual mono, so one channel is enough)
    private val reverb = SchroederReverb(
        combDelays = intArrayOf(1116, 1356, 1422, 1656),
        allPassDelays = intArrayOf(225, 341),
        combFeedback = 0.82f
    )

    /** Request a cue. Safe to call from any thread; takes effect on the next audio sample. */
    fun triggerCue(cueType: Int) {
        stopRequested.set(false)
        pendingCueType.set(if (cueType in 1..5) cueType else 1)
    }

    /** Request an immediate stop. Safe to call from any thread. */
    fun stopCue() {
        pendingCueType.set(0)
        stopRequested.set(true)
    }

    val isActive: Boolean
        get() = isPlaying || pendingCueType.get() != 0

    private fun startCue(cueType: Int) {
        currentCueType = cueType
        cueElapsedSamples = 0L
        isPlaying = true

        // Dry duration per cue type (~5 to 6.5 seconds for complete natural decay)
        cueDrySamples = when (cueType) {
            1 -> (sampleRate * 6.0).toLong() // 2-tone bell
            2 -> (sampleRate * 5.5).toLong() // Crystal wind chimes
            3 -> (sampleRate * 6.5).toLong() // Dream music box
            4 -> (sampleRate * 5.0).toLong() // 3 crystal dew drops
            5 -> (sampleRate * 6.5).toLong() // Singing bowl
            else -> (sampleRate * 6.0).toLong()
        }

        // Reset all phases and clear any leftover reverb from a previous cue
        bellPhaseA = 0.0
        bellPhaseB = 0.0
        chimePhases.fill(0.0)
        chimeShimmerPhases.fill(0.0)
        musicBoxPhases.fill(0.0)
        dropPhases.fill(0.0)
        bowlPhase1 = 0.0
        bowlPhase2 = 0.0
        bowlPhase3 = 0.0
        bowlLfoPhase = 0.0
        reverb.reset()
    }

    private fun finishCue() {
        isPlaying = false
        cueElapsedSamples = 0L
        reverb.reset()
    }

    fun nextSample(): Float {
        if (stopRequested.getAndSet(false)) {
            finishCue()
        }
        val pending = pendingCueType.getAndSet(0)
        if (pending != 0) {
            startCue(pending)
        }
        if (!isPlaying) return 0f

        val n = cueElapsedSamples
        val dry = if (n < cueDrySamples) {
            renderDry(n.toDouble() / sampleRate) * dryFadeGain(n)
        } else {
            0f
        }

        // Apply Schroeder Reverb for spatial immersion and dream-like acoustic aura
        val wet = reverb.process(dry)
        var combined = dry * 0.72f + wet * 0.38f

        val tailEnd = cueDrySamples + tailSamples
        val remaining = tailEnd - n
        if (remaining < tailFadeOutSamples) {
            combined *= (remaining.toFloat() / tailFadeOutSamples).coerceIn(0f, 1f)
        }

        cueElapsedSamples++
        if (cueElapsedSamples >= tailEnd) {
            finishCue()
        }
        return combined.coerceIn(-1.0f, 1.0f)
    }

    // Raised-cosine fade over the last part of the dry signal
    private fun dryFadeGain(n: Long): Float {
        val fadeStart = cueDrySamples - dryFadeOutSamples
        if (n < fadeStart) return 1f
        val x = (n - fadeStart).toDouble() / dryFadeOutSamples
        return (0.5 * (1.0 + cos(PI * x))).toFloat()
    }

    private fun advance(phase: Double, freq: Double): Double {
        var p = phase + (2.0 * PI * freq) / sampleRate
        if (p > 2.0 * PI) p -= 2.0 * PI
        return p
    }

    private fun renderDry(t: Double): Float {
        return when (currentCueType) {
            1 -> {
                // Type 1: 现实检验双音阶梦钟 (528Hz & 660Hz) - 与深海低音反差极强
                // Tone A: 528Hz at t = 0s
                val attackA = 0.05
                val attA = if (t < attackA) (t / attackA).toFloat() else 1f
                val decayA = attA * exp(-t * 0.72).toFloat()

                bellPhaseA = advance(bellPhaseA, 528.0)
                // Add gentle overtone at 1056Hz (integer multiple, so it wraps cleanly with the fundamental)
                val sampleA = (sin(bellPhaseA) + 0.3 * sin(bellPhaseA * 2.0)).toFloat() * decayA * 0.60f

                // Tone B: 660Hz at t = 1.2s
                var sampleB = 0f
                if (t >= 1.2) {
                    val dtB = t - 1.2
                    val attackB = 0.05
                    val attB = if (dtB < attackB) (dtB / attackB).toFloat() else 1f
                    val decayB = attB * exp(-dtB * 0.72).toFloat()

                    bellPhaseB = advance(bellPhaseB, 660.0)
                    sampleB = (sin(bellPhaseB) + 0.3 * sin(bellPhaseB * 2.0)).toFloat() * decayB * 0.55f
                }

                sampleA + sampleB
            }

            2 -> {
                // Type 2: 梦境水晶风铃 (Crystal Wind Chimes)
                // 4 delicate high-frequency metallic crystal strikes (2093Hz ~ 3520Hz)
                var chimeMix = 0f
                for (i in chimeFreqs.indices) {
                    val startT = chimeDelays[i]
                    if (t >= startT) {
                        val dt = t - startT
                        chimePhases[i] = advance(chimePhases[i], chimeFreqs[i])
                        chimeShimmerPhases[i] = advance(chimeShimmerPhases[i], chimeFreqs[i] * chimeShimmerRatio)

                        val attack = if (dt < 0.02) (dt / 0.02).toFloat() else 1f
                        val decay = attack * exp(-dt * 1.8).toFloat()
                        // Pure sine with an inharmonic shimmer partial
                        val tone = (sin(chimePhases[i]) + 0.15 * sin(chimeShimmerPhases[i])).toFloat()
                        chimeMix += tone * decay * 0.32f
                    }
                }
                chimeMix
            }

            3 -> {
                // Type 3: 灵性八音盒 (Dream Music Box)
                // 4 pure mechanical tine notes (C6, G6, E6, C7)
                var musicBoxMix = 0f
                for (i in musicBoxFreqs.indices) {
                    val startT = musicBoxDelays[i]
                    if (t >= startT) {
                        val dt = t - startT
                        musicBoxPhases[i] = advance(musicBoxPhases[i], musicBoxFreqs[i])

                        val attack = if (dt < 0.015) (dt / 0.015).toFloat() else 1f
                        val decay = attack * exp(-dt * 2.1).toFloat()
                        // Music box characteristics: dominant fundamental + subtle octave + decay
                        val tine = (sin(musicBoxPhases[i]) + 0.22 * sin(musicBoxPhases[i] * 2.0)).toFloat()
                        musicBoxMix += tine * decay * 0.40f
                    }
                }
                musicBoxMix
            }

            4 -> {
                // Type 4: 幽潭灵露 (3 Rhythmic Crystal Drops at 0.0s, 0.85s, 1.7s)
                var dropSample = 0f
                for (i in dropTimes.indices) {
                    val dropStart = dropTimes[i]
                    if (t >= dropStart && t < dropStart + dropLength) {
                        val dt = t - dropStart
                        val curFreq = if (dt < 0.025) {
                            dropBaseFreqs[i] + (dt / 0.025) * 450.0
                        } else {
                            dropBaseFreqs[i] + 450.0 - ((dt - 0.025) * 150.0)
                        }

                        dropPhases[i] = advance(dropPhases[i], curFreq)

                        val attack = if (dt < 0.015) (dt / 0.015).toFloat() else 1f
                        // Short fade before the drop's window closes, so the gate doesn't click
                        val gate = ((dropLength - dt) / dropGateFade).toFloat().coerceIn(0f, 1f)
                        val decay = attack * gate * exp(-dt * 4.2).toFloat()
                        dropSample += sin(dropPhases[i]).toFloat() * decay * 0.55f
                    }
                }
                dropSample
            }

            5 -> {
                // Type 5: 空灵颂钵 (Singing Bowl) - 供无钵声背景使用
                bowlPhase1 = advance(bowlPhase1, 216.0)
                bowlPhase2 = advance(bowlPhase2, 432.0)
                bowlPhase3 = advance(bowlPhase3, 864.0)
                bowlLfoPhase = advance(bowlLfoPhase, 0.25)

                val attackDuration = 0.08
                val attackEnv = if (t < attackDuration) (t / attackDuration).toFloat() else 1f

                val decay1 = exp(-t * 0.45).toFloat()
                val decay2 = exp(-t * 0.65).toFloat()
                val decay3 = exp(-t * 0.95).toFloat()

                val tremolo = 0.85f + 0.15f * sin(bowlLfoPhase).toFloat()

                val s1 = sin(bowlPhase1).toFloat() * decay1 * 0.50f
                val s2 = sin(bowlPhase2).toFloat() * decay2 * 0.35f
                val s3 = sin(bowlPhase3).toFloat() * decay3 * 0.15f

                (s1 + s2 + s3) * tremolo * attackEnv
            }

            else -> 0f
        }
    }
}
