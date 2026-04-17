package com.io.droneemulator.model

sealed interface BleState {
    data object Idle : BleState
    data object Advertising : BleState
    data class Connected(val deviceName: String) : BleState
    data class Error(val message: String) : BleState
}
