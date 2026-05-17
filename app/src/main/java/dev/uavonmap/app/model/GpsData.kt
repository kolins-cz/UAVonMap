package dev.uavonmap.app.model

data class GpsData(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val hdop: Float = 1.0f
)

