package com.io.droneemulator.viewmodel

import com.io.droneemulator.ui.ble.BleUiState
import com.io.droneemulator.ui.connection.ConnectionUiState
import com.io.droneemulator.ui.main.MainUiState
import kotlinx.coroutines.flow.StateFlow

interface DroneEmulatorViewModelContract {
    val uiState: StateFlow<DroneEmulatorUiState>

    fun onMockModeChanged(isMock: Boolean)

    fun onLocalPortChanged(value: String)

    fun onRemoteHostChanged(value: String)

    fun onRemotePortChanged(value: String)

    fun connect()

    fun disconnect()

    fun setAltitude(value: Float)

    fun setBattery(value: Float)

    fun toggleArm()

    fun toggleBle()
}

data class DroneEmulatorUiState(
    val connection: ConnectionUiState = ConnectionUiState(),
    val main: MainUiState = MainUiState(),
    val ble: BleUiState = BleUiState(),
)
