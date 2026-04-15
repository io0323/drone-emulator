package com.io.droneemulator.ui.connection

data class ConnectionUiState(
    val isVisible: Boolean = true,
    val localPortText: String = "14560",
    val remoteHostText: String = "10.0.2.2",
    val remotePortText: String = "14550",
    val statusText: String = "未接続",
    val helperText: String = "送信先のGCSホストとポートを入力して接続してください。",
    val isConnectEnabled: Boolean = true,
)

