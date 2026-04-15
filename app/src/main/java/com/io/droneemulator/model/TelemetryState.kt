package com.io.droneemulator.model

data class TelemetryState(
    val altitudeMeters: Float = 24.5f,
    val batteryPercent: Int = 86,
    val latitudeE7: Int = 356_812_360,
    val longitudeE7: Int = 1_397_671_250,
    val absoluteAltitudeMeters: Float = 42.0f,
    val rollRad: Float = 0.03f,
    val pitchRad: Float = -0.02f,
    val yawRad: Float = 1.57f,
    val velocityXMetersPerSecond: Float = 0.0f,
    val velocityYMetersPerSecond: Float = 0.0f,
    val velocityZMetersPerSecond: Float = 0.0f,
)

