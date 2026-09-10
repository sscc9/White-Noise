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

    // Rain synthesizer state: Transparent & natural outdoor/tent/eaves acoustic model
    private var rainBedFilter = 0f
    private var rainRes1_y1 = 0f
    private var rainRes1_y2 = 0f
    private var rainRes2_y1 = 0f
    private var rainRes2_y2 = 0f
    private var rainRes3_y1 = 0f
    private var rainRes3_y2 = 0f

    // Campfire wood burning state (Pops, crackles, sparks, and clean warm embers)
    private var campfireWoodSnapAmp = 0f
    private var campfireWoodSnapPhase = 0.0
    private var campfireWoodSnapFreq = 480.0
    private var campfireMicroCrackleAmp = 0f
    private var campfireEmberFilter = 0f

    // Ocean waves breathing phase and soft sand foam filter
    private var wavePhase = 0.0
    private var oceanFoamFilter = 0f
    private var oceanDeepFilter = 0f

    // Forest Pine Wind: Dual-LFO organic gusts, deep canopy bed, and pine needle rustle
    private var windGustPhase1 = 0.0
    private var windGustPhase2 = 0.0
    private var windDeepCanopyFilter = 0f
    private var pineLastPink = 0f
    private var pineHPOut = 0f
    private var pineLPOut = 0f

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

    // Babbling stream: dual-stage smooth water bed filter, swirl phase, and 3-voice pebble droplet polyphony
    private var streamBedFilter1 = 0f
    private var streamBedFilter2 = 0f
    private var streamSwirlPhase = 0.0
    private val streamBubblePhase = DoubleArray(3)
    private val streamBubbleAmp = FloatArray(3)
    private val streamBubbleDecay = FloatArray(3)
    private val streamBubbleFreq = DoubleArray(3)

    // 3. 红泥煮雪 (Winter Snow & Charcoal Tea-Boiling)
    private val teaBubblePhase = DoubleArray(3)
    private val teaBubbleAmp = FloatArray(3)
    private val teaBubbleDecay = FloatArray(3)
    private val teaBubbleFreq = DoubleArray(3)
    private var teaSimmerBedFilter = 0f
    private var snowWindSvfLow = 0f
    private var snowWindSvfBand = 0f
    private var snowWindPhase = 0.0
    private var snowWindSmooth = 0f

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
                // 柔和舒缓的温暖气流声（仿风扇/柔和空气感），彻底消除干涩刺耳的电视雪花屏杂音
                nextPink() * 0.30f + nextBrown() * 0.12f
            }
            SoundType.RAIN -> {
                // 1. 多频段物理敲击谐振器（模拟帐篷布、屋檐瓦片、窗玻璃与枝叶上的雨珠微小撞击质感）
                // 平均每秒约 120 滴细密水珠，自然泊松分布撞击，节奏密集均匀，无宏观忽大忽小
                if (random.nextFloat() < (120.0f / sampleRate.toFloat())) {
                    val impactAmp = 0.08f + random.nextFloat() * 0.12f
                    when (random.nextInt(3)) {
                        0 -> rainRes1_y1 += impactAmp * 0.7f // ~950Hz 帐篷布与屋檐的温润弹击
                        1 -> rainRes2_y1 += impactAmp * 0.5f // ~1650Hz 窗户与细瓦的水珠清脆淅沥
                        2 -> rainRes3_y1 += impactAmp * 0.3f // ~2600Hz 树叶水花与雨雾微粒飞溅
                    }
                }

                // 谐振器计算与微小声学阻尼物理衰减 (15~25ms 自然消散)
                val y1 = (1.9421f * rainRes1_y1 - 0.9604f * rainRes1_y2).coerceIn(-1.5f, 1.5f)
                rainRes1_y2 = rainRes1_y1
                rainRes1_y1 = y1

                val y2 = (1.8963f * rainRes2_y1 - 0.9506f * rainRes2_y2).coerceIn(-1.5f, 1.5f)
                rainRes2_y2 = rainRes2_y1
                rainRes2_y1 = y2

                val y3 = (1.8086f * rainRes3_y1 - 0.9409f * rainRes3_y2).coerceIn(-1.5f, 1.5f)
                rainRes3_y2 = rainRes3_y1
                rainRes3_y1 = y3

                val patter = (y1 * 0.45f + y2 * 0.35f + y3 * 0.20f) * 0.35f

                // 2. 连续通透自然雨幕（粉红噪音为主体，融合温润布朗底音）
                val pink = nextPink()
                val brown = nextBrown()
                val bedInput = pink * 0.42f + brown * 0.18f

                // 3. 通透性低通滤波（截止频率约 3800Hz）：
                // 彻底滤除 6kHz 以上的电子白噪尖刺，但保留 1k-4kHz 清爽通透的空气感与湿润感，绝不发闷
                val alphaBed = 0.38f
                rainBedFilter += alphaBed * (bedInput - rainBedFilter)

                // 4. 混合通透雨幕与细腻敲击淅沥感，音量绝对平稳恒定，无周期波动
                val rainOut = rainBedFilter * 1.05f + patter
                rainOut.coerceIn(-1.0f, 1.0f)
            }
            SoundType.OCEAN -> {
                // 1. 极慢深度睡眠潮汐呼吸周期（14.5 秒超长舒缓周期，模拟夜间沙滩远海涌浪）
                wavePhase += (2.0 * PI) / (sampleRate * 14.5)
                if (wavePhase > 2.0 * PI) wavePhase -= 2.0 * PI

                // 2. 温和渐进的平滑波形曲线（起伏落差极其温和，从 0.58 渐变到 0.95，彻底杜绝暴冲惊吓）
                val rawSine = sin(wavePhase).toFloat()
                val waveMod = ((rawSine + 1.0f) * 0.5f).coerceIn(0f, 1f)
                val gentleSwell = 0.58f + 0.38f * waveMod

                // 3. 深远海洋温润低频声床（低通滤波，消除生硬杂音，保留温厚深沉的海浪底蕴）
                val brown = nextBrown()
                oceanDeepFilter += 0.05f * (brown - oceanDeepFilter)

                // 4. 细软沙滩泡沫漫涌（带平滑滤波，消除生硬尖锐白花，呈现轻柔抚岸与细沙吸水声）
                val pink = nextPink()
                val targetFoam = pink * (0.04f + 0.16f * (waveMod * waveMod))
                oceanFoamFilter += 0.09f * (targetFoam - oceanFoamFilter)

                // 5. 混合深海温润底床与细沙潮涌，整体音量始终温厚克制、宁静安详
                val oceanOut = (oceanDeepFilter * 0.75f + oceanFoamFilter * 1.1f) * gentleSwell * 1.5f
                oceanOut.coerceIn(-1.0f, 1.0f)
            }
            SoundType.WIND -> {
                // 1. 自然有机阵风模型（双频复合 LFO：13.7s 与 19.3s，交织出非机械重复的自然风势起伏）
                windGustPhase1 += (2.0 * PI) / (sampleRate * 13.7)
                if (windGustPhase1 > 2.0 * PI) windGustPhase1 -= 2.0 * PI
                windGustPhase2 += (2.0 * PI) / (sampleRate * 19.3)
                if (windGustPhase2 > 2.0 * PI) windGustPhase2 -= 2.0 * PI

                val gust1 = (sin(windGustPhase1) + 1.0) * 0.5
                val gust2 = (sin(windGustPhase2) + 1.0) * 0.5
                val gustVelocity = (gust1 * 0.65 + gust2 * 0.35).toFloat() // 0.0f ~ 1.0f 阵风风速

                // 2. 山谷林间深层风息声床（~350Hz 低通平滑温润底音，告别封闭机舱/隧道轰鸣）
                val rawCanopy = nextBrown() * 0.40f + nextPink() * 0.15f
                windDeepCanopyFilter += 0.048f * (rawCanopy - windDeepCanopyFilter)
                val canopySwell = 0.60f + 0.38f * gustVelocity

                // 3. 松针与树梢萧萧掠拂（核心松涛音色：700Hz ~ 1800Hz 级联带通滤波，随阵风动态呼吸）
                val pink = nextPink()
                // 高通部分 (fc ~ 700Hz)
                val alphaHP = 0.909f
                pineHPOut = alphaHP * (pineHPOut + pink - pineLastPink)
                pineLastPink = pink
                // 低通部分 (fc ~ 1800Hz)
                val alphaLP = 0.204f
                pineLPOut += alphaLP * (pineHPOut - pineLPOut)

                // 松针沙沙声随阵风起伏，掠过树梢时呈现细腻如轻纱般的松涛质感
                val needleMod = 0.10f + 0.25f * gustVelocity
                val pineNeedles = pineLPOut * needleMod * 1.6f

                // 4. 混合林冠深风与松针细语，整体音色通透开阔、温和助眠
                val windOut = (windDeepCanopyFilter * canopySwell * 1.8f + pineNeedles)
                windOut.coerceIn(-1.0f, 1.0f)
            }
            SoundType.CAMPFIRE -> {
                // 1. 木柴受热开裂突发爆裂 (Wood Log Snaps & Pops)
                // 平均每 0.5 - 1.2 秒发生一次较为清脆明显的木材断裂/树脂爆裂声
                if (random.nextFloat() < (1.4f / sampleRate.toFloat())) {
                    campfireWoodSnapAmp = 0.45f + random.nextFloat() * 0.45f
                    campfireWoodSnapFreq = 380.0 + random.nextDouble() * 320.0
                    campfireWoodSnapPhase = 0.0
                }

                var woodSnapSignal = 0f
                if (campfireWoodSnapAmp > 0.002f) {
                    campfireWoodSnapPhase += (2.0 * PI * campfireWoodSnapFreq) / sampleRate
                    if (campfireWoodSnapPhase > 2.0 * PI) campfireWoodSnapPhase -= 2.0 * PI
                    // 木质共鸣腔体阻尼衰减（约 20-30ms 自然消散）
                    woodSnapSignal = sin(campfireWoodSnapPhase).toFloat() * campfireWoodSnapAmp * 0.55f
                    // 初始瞬态爆破感 (Transient Click)
                    if (campfireWoodSnapAmp > 0.35f) {
                        woodSnapSignal += (nextWhite() - nextWhite()) * campfireWoodSnapAmp * 0.40f
                    }
                    campfireWoodSnapAmp *= 0.982f
                }

                // 2. 细碎炭花与细小火星迸发 (Micro Crackles & Sparks)
                // 随机高频细微噼啪，每秒 25-40 次轻微噼啪声，灵动真实
                if (random.nextFloat() < (32.0f / sampleRate.toFloat())) {
                    campfireMicroCrackleAmp = 0.15f + random.nextFloat() * 0.25f
                }
                campfireMicroCrackleAmp *= 0.93f // 极短促（2-4ms）清脆炭花
                val microCrackle = (nextWhite() - nextWhite()) * campfireMicroCrackleAmp * 0.22f

                // 3. 极微弱温暖余烬底音（彻底移除原先像下雨瀑布一样的 0.5f 粗暴布朗噪音）
                // 仅保留 0.02f 经由极深低通滤波的温和低频炭火暗涌，声底极其干净纯粹
                val emberNoise = nextBrown() * 0.025f
                campfireEmberFilter += 0.06f * (emberNoise - campfireEmberFilter)

                val fireOut = woodSnapSignal + microCrackle + campfireEmberFilter
                fireOut.coerceIn(-1.0f, 1.0f)
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
                // 1. 山涧石缝流水底床（温润平稳的双阶低通滤波水流，彻底消除粗暴喷水与自来水冲水感）
                val rawBed = nextPink() * 0.32f + nextBrown() * 0.18f
                val alpha = 0.085f // 截止频率约 600Hz，温润圆融
                streamBedFilter1 += alpha * (rawBed - streamBedFilter1)
                streamBedFilter2 += alpha * (streamBedFilter1 - streamBedFilter2)

                // 微弱水流涡旋轻柔呼吸（0.2Hz 微幅波动，自然而不突兀）
                streamSwirlPhase += (2.0 * PI * 0.2) / sampleRate
                if (streamSwirlPhase > 2.0 * PI) streamSwirlPhase -= 2.0 * PI
                val swirl = 0.88f + 0.12f * sin(streamSwirlPhase).toFloat()

                // 2. 卵石激流轻柔水泡与清脆叮咚声（3 复音物理水珠共振）
                // 平均每秒约 16-24 次轻柔水泡与卵石拍打，自然错落
                if (random.nextFloat() < (20.0f / sampleRate.toFloat())) {
                    var minIdx = 0
                    var minAmp = streamBubbleAmp[0]
                    for (i in 1..2) {
                        if (streamBubbleAmp[i] < minAmp) {
                            minAmp = streamBubbleAmp[i]
                            minIdx = i
                        }
                    }
                    streamBubbleAmp[minIdx] = 0.15f + random.nextFloat() * 0.25f
                    streamBubbleFreq[minIdx] = 420.0 + random.nextDouble() * 520.0 // 420Hz ~ 940Hz 灵动水珠声
                    streamBubbleDecay[minIdx] = 0.9935f - random.nextFloat() * 0.001f // ~18-28ms 快速自然衰减
                    streamBubblePhase[minIdx] = 0.0
                }

                var bubbleOutput = 0f
                for (i in 0..2) {
                    if (streamBubbleAmp[i] > 0.002f) {
                        // 微妙向上扫频（水滴脱离与气泡破裂的物理声学特征）
                        val freqRatio = 1.0 + (1.0f - streamBubbleAmp[i] / 0.40f).coerceIn(0f, 1f) * 0.15
                        val currentFreq = streamBubbleFreq[i] * freqRatio
                        streamBubblePhase[i] += (2.0 * PI * currentFreq) / sampleRate
                        if (streamBubblePhase[i] > 2.0 * PI) streamBubblePhase[i] -= 2.0 * PI

                        bubbleOutput += sin(streamBubblePhase[i]).toFloat() * streamBubbleAmp[i]
                        streamBubbleAmp[i] *= streamBubbleDecay[i]
                    }
                }

                // 3. 混合温润流水底床与叮咚水花，音量均匀平稳
                val streamOut = streamBedFilter2 * swirl * 1.9f + bubbleOutput * 0.40f
                streamOut.coerceIn(-1.0f, 1.0f)
            }
            SoundType.SNOW_TEA -> {
                // 1. 红泥陶壶慢火温煨小水泡（从每秒28次急沸大幅降至8-10次慢火咕嘟，音调下潜至温润厚实的240~460Hz）
                if (random.nextFloat() < (9.0f / sampleRate.toFloat())) {
                    var minIdx = 0
                    var minAmp = teaBubbleAmp[0]
                    for (i in 1..2) {
                        if (teaBubbleAmp[i] < minAmp) {
                            minAmp = teaBubbleAmp[i]
                            minIdx = i
                        }
                    }
                    teaBubbleAmp[minIdx] = 0.20f + random.nextFloat() * 0.30f
                    teaBubbleDecay[minIdx] = 0.9968f - random.nextFloat() * 0.0008f // ~35-50ms 温润自然消散
                    teaBubbleFreq[minIdx] = 240.0 + random.nextDouble() * 220.0 // 陶壶温厚低频水泡
                    teaBubblePhase[minIdx] = 0.0
                }

                var bubbleSum = 0f
                for (i in 0..2) {
                    if (teaBubbleAmp[i] > 0.001f) {
                        val freqRatio = 1.0 + (1.0f - teaBubbleAmp[i] / 0.50f).coerceIn(0f, 1f) * 0.12
                        val currentFreq = teaBubbleFreq[i] * freqRatio
                        teaBubblePhase[i] += (2.0 * PI * currentFreq) / sampleRate
                        if (teaBubblePhase[i] > 2.0 * PI) teaBubblePhase[i] -= 2.0 * PI

                        bubbleSum += sin(teaBubblePhase[i]).toFloat() * teaBubbleAmp[i]
                        teaBubbleAmp[i] *= teaBubbleDecay[i]
                    }
                }

                // 2. 陶壶温热水体微沸声床（柔和低频，沉静舒适）
                val rawTeaBed = nextBrown() * 0.20f + nextPink() * 0.08f
                teaSimmerBedFilter += 0.035f * (rawTeaBed - teaSimmerBedFilter)

                // 3. 窗外积雪冬日温和冷风（彻底去除原先Q=8.5尖厉口哨啸音！改用Q=1.3宽带低沉雪风拂过）
                snowWindPhase += (2.0 * PI * 0.032) / sampleRate
                if (snowWindPhase > 2.0 * PI) snowWindPhase -= 2.0 * PI
                val centerFreq = 320.0 + (sin(snowWindPhase) + 1.0) * 160.0 // 320Hz ~ 640Hz 低回温和，绝无刺耳哨音
                val q = 1.3f // 宽带柔和，平滑宁静

                val f = (PI * centerFreq / sampleRate).toFloat().coerceIn(0.01f, 0.99f)
                val qInv = 1.0f / q

                val inputNoise = nextPink() * 0.15f
                val hp = inputNoise - snowWindSvfLow - qInv * snowWindSvfBand
                snowWindSvfBand += f * hp
                snowWindSvfLow += f * snowWindSvfBand
                val gentleSnowWind = snowWindSvfBand * 0.45f
                snowWindSmooth += 0.05f * (gentleSnowWind - snowWindSmooth)

                // 4. 红泥炉炭火微弱温软木炭脆响（清幽雅致）
                var charcoalCrackle = 0f
                if (random.nextFloat() < (1.2f / sampleRate.toFloat())) {
                    charcoalCrackle = (random.nextFloat() * 2f - 1f) * 0.06f
                }

                // 5. 混合陶壶慢煨咕嘟声、温润水床与窗外积雪微风，温暖惬意极度助眠
                val snowTeaOut = bubbleSum * 0.45f + teaSimmerBedFilter * 1.6f + snowWindSmooth + charcoalCrackle
                snowTeaOut.coerceIn(-1.0f, 1.0f)
            }
        }
    }
}
