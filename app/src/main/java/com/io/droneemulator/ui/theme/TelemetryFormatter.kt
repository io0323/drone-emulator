package com.io.droneemulator.ui.theme

import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.model.TelemetryState
import java.util.Locale

object TelemetryFormatter {
    fun connectionStatus(state: ConnectionState): String = when (state) {
        ConnectionState.Disconnected -> "未接続"
        ConnectionState.Connecting -> "接続中…"
        is ConnectionState.Connected -> "接続中 ${state.remoteHost}:${state.remotePort}"
        is ConnectionState.Error -> "接続エラー"
    }

    fun helperText(state: ConnectionState, validationMessage: String?): String = when {
        validationMessage != null -> validationMessage
        state is ConnectionState.Error -> state.message
        state is ConnectionState.Connected && state.lastPacketAtMillis != null -> "受信パケットあり: ${state.lastPacketAtMillis}"
        state is ConnectionState.Connected -> "HEARTBEAT/GLOBAL_POSITION_INT/BATTERY_STATUS は1秒周期、ATTITUDEは100ms周期で送信中です。"
        else -> "送信先のGCSホストとポートを入力して接続してください。"
    }

    fun connectionLabel(state: ConnectionState): String = when (state) {
        ConnectionState.Disconnected -> "UDP停止中"
        ConnectionState.Connecting -> "UDP接続中…"
        is ConnectionState.Connected -> {
            val lastPacketLabel = state.lastPacketAtMillis?.let { " / last rx=$it" } ?: ""
            "local:${state.localPort} -> ${state.remoteHost}:${state.remotePort}$lastPacketLabel"
        }
        is ConnectionState.Error -> "UDPエラー: ${state.message}"
    }

    fun altitudeLabel(value: Float): String = String.format(Locale.US, "%.1f m", value)

    fun batteryLabel(value: Int): String = "$value %"

    fun gpsLabel(telemetryState: TelemetryState): String = String.format(
        Locale.US,
        "%.6f, %.6f",
        telemetryState.latitudeE7 / 10_000_000.0,
        telemetryState.longitudeE7 / 10_000_000.0,
    )

    fun attitudeLabel(telemetryState: TelemetryState): String = String.format(
        Locale.US,
        "roll %.2f / pitch %.2f / yaw %.2f",
        telemetryState.rollRad,
        telemetryState.pitchRad,
        telemetryState.yawRad,
    )

    fun commandLog(commandSummaries: List<String>): String =
        if (commandSummaries.isEmpty()) {
            "まだ受信コマンドはありません"
        } else {
            commandSummaries.joinToString(separator = "\n")
        }

    fun altitudeSliderValue(telemetryState: TelemetryState): Float = telemetryState.altitudeMeters

    fun batterySliderValue(telemetryState: TelemetryState): Float = telemetryState.batteryPercent.toFloat()

    fun batteryPercent(telemetryState: TelemetryState): Int = telemetryState.batteryPercent
}


