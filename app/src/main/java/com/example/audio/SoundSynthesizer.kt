package com.example.audio

import java.util.Random
import kotlin.math.sin
import kotlin.math.PI

class SoundSynthesizer {
    private val random = Random()
    private val sampleRate = 44100.0

    // Pink Noise filter state
    private var b0 = 0f
    private var b1 = 0f
    private var b2 = 0f
    private var b3 = 0f
    private var b4 = 0f
    private var b5 = 0f
    private var b6 = 0f

    // Brown Noise filter state
    private var lastBrown = 0f

    // Rain drop accumulator state
    private var rainDropAmp = 0f

    // Campfire log crackling state
    private var campfireCrackleAmp = 0f
    private var lastCampfireWhite = 0f

    // Ocean waves breathing phase
    private var wavePhase = 0.0

    // Wind sweeping phase and filter state
    private var windPhase = 0.0
    private var lastWind = 0f

    // Singing bowl fundamental, harmonic, and LFO tremolo phase
    private var bowlPhase1 = 0.0
    private var bowlPhase2 = 0.0
    private var bowlLfoPhase = 0.0

    // ----------------------------------------------------
    // Industry-Standard Procedural Crickets (Farnell Model)
    // ----------------------------------------------------
    // Layer 1: Near Solo Cricket (Stable rhythmic pulse strophe)
    private var soloCricketCarrierPhase = 0.0
    private var soloCricketSamplesLeft = 0L
    private var soloCricketPulseIndex = 0
    private var soloCricketIsChirping = false
    private val soloCarrierFreq = 3850.0 // Iconic clear cricket fundamental

    // Layer 2: Distant Chorus Shimmer (Bandpass filtered pink noise with fast Tremolo)
    private var chorusBPFX1 = 0f
    private var chorusBPFX2 = 0f
    private var chorusBPFY1 = 0f
    private var chorusBPFY2 = 0f
    private var chorusLfoPhase = 0.0      // Fast 18Hz Tremolo
    private var chorusSlowSwellPhase = 0.0 // Very slow 0.05Hz wind wave swell

    // Babbling stream bubbling filter and phase
    private var streamPhase = 0.0
    private var lastStreamFilter = 0f
    private var streamFrequency = 1.5

    // 3. 红泥煮雪 (Winter Snow & Charcoal Tea-Boiling)
    private val teaBubblePhase = DoubleArray(3)
    private val teaBubbleAmp = FloatArray(3)
    private val teaBubbleDecay = FloatArray(3)
    private val teaBubbleFreq = DoubleArray(3)
    private var snowWindSvfLow = 0f
    private var snowWindSvfBand = 0f
    private var snowWindPhase = 0.0

    private fun nextWhite(): Float {
        return random.nextFloat() * 2.0f - 1.0f
    }

    private fun nextPink(): Float {
        val white = nextWhite()
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

    private fun nextBrown(): Float {
        val white = nextWhite()
        lastBrown = (lastBrown + (0.02f * white)) / 1.02f
        return lastBrown * 3.5f
    }

    fun nextSample(type: SoundType): Float {
        return when (type) {
            SoundType.WHITE_NOISE -> {
                nextWhite() * 0.4f
            }
            SoundType.RAIN -> {
                val baseRain = nextPink() * 0.6f
                if (random.nextFloat() < 0.0008f) {
                    rainDropAmp = 1.0f
                }
                rainDropAmp *= 0.96f
                val drop = rainDropAmp * nextWhite() * 0.4f
                baseRain + drop
            }
            SoundType.OCEAN -> {
                wavePhase += (2.0 * PI) / (sampleRate * 8.0) // 8-second waves
                if (wavePhase > 2.0 * PI) wavePhase -= 2.0 * PI
                
                val waveMod = ((sin(wavePhase) + 1.0) / 2.0).toFloat()
                val deepOcean = nextBrown() * 0.7f
                val foam = nextPink() * 0.2f * (waveMod * waveMod)
                
                (deepOcean + foam) * (0.25f + 0.75f * waveMod)
            }
            SoundType.WIND -> {
                windPhase += (2.0 * PI) / (sampleRate * 14.0) // 14-second sweeps
                if (windPhase > 2.0 * PI) windPhase -= 2.0 * PI
                
                val sweep = ((sin(windPhase) + 1.0) / 2.0).toFloat()
                val cutoff = 150.0f + sweep * 350.0f
                
                val alpha = cutoff / (sampleRate.toFloat() + cutoff)
                val brown = nextBrown()
                lastWind = lastWind + alpha * (brown - lastWind)
                
                lastWind * 1.5f
            }
            SoundType.CAMPFIRE -> {
                val woodFlame = nextBrown() * 0.5f
                if (random.nextFloat() < 0.00018f) {
                    campfireCrackleAmp = 1.0f
                }
                campfireCrackleAmp *= 0.92f
                
                val crackle = if (campfireCrackleAmp > 0.01f) {
                    val w = nextWhite()
                    val highFreq = w - lastCampfireWhite
                    lastCampfireWhite = w
                    highFreq * campfireCrackleAmp * 0.7f
                } else {
                    0f
                }
                
                woodFlame + crackle
            }
            SoundType.CRICKETS -> {
                // 1. Near Solo Cricket: High precision rhythmic strophe
                if (soloCricketSamplesLeft <= 0) {
                    if (soloCricketIsChirping) {
                        // Switch to silent rest gap: stable but naturally breathing rhythm
                        soloCricketIsChirping = false
                        val gapSec = 1.3 + random.nextDouble() * 0.5 // 1.3 to 1.8 seconds gap
                        soloCricketSamplesLeft = (sampleRate * gapSec).toLong()
                    } else {
                        // Switch to active chirp: exactly 4 rapid pulses
                        soloCricketIsChirping = true
                        val pulseTotalSamples = (sampleRate * 0.040).toLong() // 40ms per pulse cycle
                        soloCricketSamplesLeft = pulseTotalSamples * 4
                    }
                }

                var soloSample = 0f
                if (soloCricketIsChirping && soloCricketSamplesLeft > 0) {
                    val totalChirpSamples = (sampleRate * 0.040).toLong() * 4
                    val elapsedInChirp = totalChirpSamples - soloCricketSamplesLeft
                    val pulseLength = (sampleRate * 0.040).toLong()
                    val posInPulse = elapsedInChirp % pulseLength
                    val activeLength = (sampleRate * 0.024).toLong() // 24ms active

                    if (posInPulse < activeLength) {
                        soloCricketCarrierPhase += (2.0 * PI * soloCarrierFreq) / sampleRate
                        if (soloCricketCarrierPhase > 2.0 * PI) soloCricketCarrierPhase -= 2.0 * PI

                        // Smooth half-sine rise/decay envelope to keep pulse crisp without clicks
                        val ampEnvelope = sin((posInPulse.toDouble() / activeLength) * PI).toFloat()
                        soloSample = sin(soloCricketCarrierPhase).toFloat() * ampEnvelope * 0.28f
                    }
                }
                soloCricketSamplesLeft--

                // 2. Distant Shimmering Chorus: bandpass filtered pink noise with rapid tremolo
                val inputPink = nextPink()
                // Standard 2-Pole IIR Bandpass Filter (fc = 4500Hz, Q = 6.0, Fs = 44100Hz)
                val filteredNoise = 0.2848f * (inputPink - chorusBPFX2) - (-1.5271f) * chorusBPFY1 - 0.9051f * chorusBPFY2
                
                // Update filter state variables
                chorusBPFX2 = chorusBPFX1
                chorusBPFX1 = inputPink
                chorusBPFY2 = chorusBPFY1
                chorusBPFY1 = filteredNoise

                // Fast LFO (19Hz) simulating a dense field of vibrating insect wings (Tremolo)
                chorusLfoPhase += (2.0 * PI * 19.0) / sampleRate
                if (chorusLfoPhase > 2.0 * PI) chorusLfoPhase -= 2.0 * PI
                val tremolo = 0.5f + 0.5f * sin(chorusLfoPhase).toFloat()

                // Slow wind swell LFO (0.05Hz) to simulate natural wind passing through trees/grass
                chorusSlowSwellPhase += (2.0 * PI * 0.05) / sampleRate
                if (chorusSlowSwellPhase > 2.0 * PI) chorusSlowSwellPhase -= 2.0 * PI
                val swell = 0.6f + 0.4f * sin(chorusSlowSwellPhase).toFloat()

                val chorusSample = filteredNoise * tremolo * swell * 0.16f

                // Base forest wind background rustle
                val forestRustle = nextPink() * 0.02f

                soloSample + chorusSample + forestRustle
            }
            SoundType.SINGING_BOWL -> {
                bowlPhase1 += (2.0 * PI * 136.1) / sampleRate
                if (bowlPhase1 > 2.0 * PI) bowlPhase1 -= 2.0 * PI
                
                bowlPhase2 += (2.0 * PI * 367.5) / sampleRate
                if (bowlPhase2 > 2.0 * PI) bowlPhase2 -= 2.0 * PI
                
                bowlLfoPhase += (2.0 * PI * 0.08) / sampleRate
                if (bowlLfoPhase > 2.0 * PI) bowlLfoPhase -= 2.0 * PI
                
                val tremolo = (0.55f + 0.45f * sin(bowlLfoPhase).toFloat())
                val tone1 = sin(bowlPhase1).toFloat() * 0.65f
                val tone2 = sin(bowlPhase2).toFloat() * 0.35f
                
                (tone1 + tone2) * tremolo * 0.5f
            }
            SoundType.STREAM -> {
                val waterFlow = nextPink() * 0.5f
                
                streamPhase += (2.0 * PI * streamFrequency) / sampleRate
                if (streamPhase > 2.0 * PI) {
                    streamPhase -= 2.0 * PI
                    streamFrequency = 1.0 + random.nextDouble() * 1.2
                }
                
                val bubbleMod = ((sin(streamPhase) + 1.0) / 2.0).toFloat()
                val cutoff = 600.0f + bubbleMod * 400.0f
                
                val alpha = cutoff / (sampleRate.toFloat() + cutoff)
                val noise = nextWhite()
                lastStreamFilter = lastStreamFilter + alpha * (noise - lastStreamFilter)
                
                waterFlow * 0.5f + lastStreamFilter * 0.25f
            }
            SoundType.SNOW_TEA -> {
                // 1. Boiling tea water bubbles
                if (random.nextFloat() < (28.0f / sampleRate.toFloat())) {
                    var minIdx = 0
                    var minAmp = teaBubbleAmp[0]
                    for (i in 1..2) {
                        if (teaBubbleAmp[i] < minAmp) {
                            minAmp = teaBubbleAmp[i]
                            minIdx = i
                        }
                    }
                    // Trigger tiny steam bubble pop
                    teaBubbleAmp[minIdx] = 0.25f + random.nextFloat() * 0.75f
                    teaBubbleDecay[minIdx] = 0.994f - random.nextFloat() * 0.001f // ultra fast decay
                    teaBubbleFreq[minIdx] = 480.0 + random.nextDouble() * 260.0
                    teaBubblePhase[minIdx] = 0.0
                }

                var bubbleSum = 0f
                for (i in 0..2) {
                    if (teaBubbleAmp[i] > 0.001f) {
                        teaBubblePhase[i] += (2.0 * PI * teaBubbleFreq[i]) / sampleRate
                        if (teaBubblePhase[i] > 2.0 * PI) teaBubblePhase[i] -= 2.0 * PI

                        bubbleSum += sin(teaBubblePhase[i]).toFloat() * teaBubbleAmp[i]
                        teaBubbleAmp[i] *= teaBubbleDecay[i]
                    }
                }

                // 2. Cold winter howling wind draft SVF filter sweep
                snowWindPhase += (2.0 * PI * 0.04) / sampleRate
                if (snowWindPhase > 2.0 * PI) snowWindPhase -= 2.0 * PI
                val centerFreq = 850.0 + (sin(snowWindPhase) + 1.0) * 400.0 // sweeps 850Hz to 1650Hz
                val q = 8.5f // narrow whistling wind

                val f = (PI * centerFreq / sampleRate).toFloat().coerceIn(0.01f, 0.99f)
                val qInv = 1.0f / q

                val inputNoise = nextPink() * 0.2f
                val hp = inputNoise - snowWindSvfLow - qInv * snowWindSvfBand
                snowWindSvfBand += f * hp
                snowWindSvfLow += f * snowWindSvfBand
                val whistlingWind = snowWindSvfBand * 0.75f

                // 3. Cozy Red Clay Charcoal tiny crackles
                var charcoalCrackle = 0f
                if (random.nextFloat() < (1.5f / sampleRate.toFloat())) {
                    charcoalCrackle = (random.nextFloat() * 2f - 1f) * 0.08f
                }

                whistlingWind + bubbleSum * 0.18f + charcoalCrackle
            }
        }
    }
}
