package com.io.droneemulator.ui.main

data class MainUiState(
    val isVisible: Boolean = false,
    val connectionText: String = "",
    val altitudeMeters: Float = 24.5f,
    val altitudeText: String = "24.5 m",
    val batteryPercent: Int = 86,
    val batteryText: String = "86 %",
    val gpsText: String = "35.681236, 139.767125",
    val attitudeText: String = "roll 0.03 / pitch -0.02 / yaw 1.57",
    val lastCommandText: String = "COMMAND_LONG / MISSION_ITEM / RC_CHANNELS_OVERRIDE を待機中",
    val commandLogText: String = "まだ受信コマンドはありません",
)

