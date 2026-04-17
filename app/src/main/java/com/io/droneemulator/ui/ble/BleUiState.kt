package com.io.droneemulator.ui.ble

data class BleUiState(
    val stateText: String = "停止中",
    val isAdvertising: Boolean = false,
    val connectedDeviceName: String? = null,
    val toggleButtonLabel: String = "アドバタイズ開始",
)
