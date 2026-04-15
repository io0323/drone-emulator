package com.io.droneemulator

import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.model.ReceivedCommand
import com.io.droneemulator.model.TelemetryState
import com.io.droneemulator.repository.mavlink.MavlinkRepositoryContract
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow

class FakeMavlinkRepository(
    initialConnectionState: ConnectionState = ConnectionState.Disconnected,
    initialTelemetryState: TelemetryState = TelemetryState(),
) : MavlinkRepositoryContract {
    override val connectionState: StateFlow<ConnectionState> = MutableStateFlow(initialConnectionState)
    override val telemetryState: StateFlow<TelemetryState> = MutableStateFlow(initialTelemetryState)
    override val receivedCommands: Flow<ReceivedCommand>
        get() = commandFlow.asSharedFlow()

    var connectCalls: List<ConnectionRequest> = emptyList()
        private set
    var disconnectCalls: Int = 0
        private set

    private val commandFlow = MutableSharedFlow<ReceivedCommand>(extraBufferCapacity = 16)

    override suspend fun connect(localPort: Int, remoteHost: String, remotePort: Int) {
        connectCalls = connectCalls + ConnectionRequest(localPort, remoteHost, remotePort)
        (connectionState as MutableStateFlow).value = ConnectionState.Connected(localPort, remoteHost, remotePort)
    }

    override suspend fun disconnect() {
        disconnectCalls += 1
        (connectionState as MutableStateFlow).value = ConnectionState.Disconnected
    }

    override fun updateAltitudeMeters(value: Float) {
        (telemetryState as MutableStateFlow).value = telemetryState.value.copy(altitudeMeters = value)
    }

    override fun updateBatteryPercent(value: Int) {
        (telemetryState as MutableStateFlow).value = telemetryState.value.copy(batteryPercent = value)
    }

    fun emitCommand(command: ReceivedCommand) {
        commandFlow.tryEmit(command)
    }

    data class ConnectionRequest(
        val localPort: Int,
        val remoteHost: String,
        val remotePort: Int,
    )
}

