package com.io.droneemulator.ui.connection

data class ConnectionUiState(
    val isVisible: Boolean = true,
    val isMockMode: Boolean = false,
    val showNetworkInputs: Boolean = true,
    val localPortText: String = "14560",
    val remoteHostText: String = "192.168.3.11",
    val remotePortText: String = "14540",
    val statusText: String = "未接続",
    val helperText: String = "送信先のGCSホストとポートを入力して接続してください。",
    val isConnectEnabled: Boolean = true,
)

