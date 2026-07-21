package com.example.audio

enum class SoundType(
    val id: String,
    val displayName: String,
    val iconName: String,
    val defaultVolume: Float
) {
    WHITE_NOISE("white_noise", "纯白噪音", "Grain", 0.0f),
    RAIN("rain", "轻柔细雨", "WaterDrop", 0.4f),
    OCEAN("ocean", "深海潮汐", "Waves", 0.3f),
    WIND("wind", "森林松风", "Air", 0.0f),
    CAMPFIRE("campfire", "林中篝火", "LocalFireDepartment", 0.0f),
    CRICKETS("crickets", "夏夜虫鸣", "Park", 0.0f),
    SINGING_BOWL("singing_bowl", "静心颂钵", "GraphicEq", 0.0f),
    STREAM("stream", "清泉流水", "Opacity", 0.0f),
    SNOW_TEA("snow_tea", "红泥煮雪", "Snow", 0.0f);
}
