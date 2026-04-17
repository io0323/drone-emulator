package com.io.droneemulator.repository.mavlink

import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.model.ReceivedCommand
import com.io.droneemulator.model.TelemetryState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class MockMavlinkRepository : MavlinkRepositoryContract {
    override val connectionState: StateFlow<ConnectionState>
        get() = _connectionState.asStateFlow()

    override val telemetryState: StateFlow<TelemetryState>
        get() = _telemetryState.asStateFlow()

    override val receivedCommands: Flow<ReceivedCommand>
        get() = _receivedCommands.asSharedFlow()

    private val scope = CoroutineScope(SupervisorJob())
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _telemetryState = MutableStateFlow(TelemetryState())
    private val _receivedCommands = MutableSharedFlow<ReceivedCommand>(extraBufferCapacity = 32)
    private var dummyJob: Job? = null

    private val dummyCommands = listOf(
        ReceivedCommand.CommandLong(400, 0, listOf(1f, 0f, 0f, 0f, 0f, 0f, 0f)),
        ReceivedCommand.CommandLong(179, 1, listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        ReceivedCommand.RcChannelsOverride(listOf(1500, 1500, 1000, 1500, 0, 0, 0, 0)),
    )

    override suspend fun connect(localPort: Int, remoteHost: String, remotePort: Int) {
        dummyJob?.cancelAndJoin()
        _connectionState.value = ConnectionState.Connected(localPort, remoteHost, remotePort)
        dummyJob = scope.launch {
            var i = 0
            while (isActive) {
                delay(3_000)
                _receivedCommands.tryEmit(dummyCommands[i % dummyCommands.size])
                i++
            }
        }
    }

    override suspend fun disconnect() {
        dummyJob?.cancelAndJoin()
        dummyJob = null
        _connectionState.value = ConnectionState.Disconnected
    }

    override fun updateAltitudeMeters(value: Float) {
        _telemetryState.update { it.copy(altitudeMeters = value.coerceIn(0f, 120f)) }
    }

    override fun updateBatteryPercent(value: Int) {
        _telemetryState.update { it.copy(batteryPercent = value.coerceIn(0, 100)) }
    }

    override fun updateArmed(value: Boolean) {
        _telemetryState.update { it.copy(isArmed = value) }
    }
}
