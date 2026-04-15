package com.io.droneemulator.repository.mavlink

import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.model.ReceivedCommand
import com.io.droneemulator.model.TelemetryState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MavlinkRepositoryContract {
    val connectionState: StateFlow<ConnectionState>
    val telemetryState: StateFlow<TelemetryState>
    val receivedCommands: Flow<ReceivedCommand>

    suspend fun connect(localPort: Int, remoteHost: String, remotePort: Int)

    suspend fun disconnect()

    fun updateAltitudeMeters(value: Float)

    fun updateBatteryPercent(value: Int)
}

