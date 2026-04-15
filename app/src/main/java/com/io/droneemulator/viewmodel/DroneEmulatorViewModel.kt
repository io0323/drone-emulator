package com.io.droneemulator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.repository.mavlink.MavlinkRepository
import com.io.droneemulator.repository.mavlink.MavlinkRepositoryContract
import com.io.droneemulator.ui.connection.ConnectionUiState
import com.io.droneemulator.ui.main.MainUiState
import com.io.droneemulator.ui.theme.TelemetryFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class DroneEmulatorViewModel(
    private val repository: MavlinkRepositoryContract,
) : ViewModel(), DroneEmulatorViewModelContract {
    private val localPort = MutableStateFlow("14560")
    private val remoteHost = MutableStateFlow("10.0.2.2")
    private val remotePort = MutableStateFlow("14550")
    private val validationMessage = MutableStateFlow<String?>(null)
    private val commandSummaries = MutableStateFlow<List<String>>(emptyList())
    private val connectionInputs = combine(
        localPort,
        remoteHost,
        remotePort,
        validationMessage,
    ) { localPortValue, remoteHostValue, remotePortValue, validationMessageValue ->
        ConnectionInputs(
            localPort = localPortValue,
            remoteHost = remoteHostValue,
            remotePort = remotePortValue,
            validationMessage = validationMessageValue,
        )
    }

    override val uiState: StateFlow<DroneEmulatorUiState> = combine(
        connectionInputs,
        repository.connectionState,
        repository.telemetryState,
        commandSummaries,
    ) { inputs, connectionState, telemetryState, commandHistory ->
        val isConnected = connectionState is ConnectionState.Connected
        DroneEmulatorUiState(
            connection = ConnectionUiState(
                isVisible = !isConnected,
                localPortText = inputs.localPort,
                remoteHostText = inputs.remoteHost,
                remotePortText = inputs.remotePort,
                statusText = TelemetryFormatter.connectionStatus(connectionState),
                helperText = TelemetryFormatter.helperText(connectionState, inputs.validationMessage),
                isConnectEnabled = connectionState != ConnectionState.Connecting,
            ),
            main = MainUiState(
                isVisible = isConnected,
                connectionText = TelemetryFormatter.connectionLabel(connectionState),
                altitudeMeters = TelemetryFormatter.altitudeSliderValue(telemetryState),
                altitudeText = TelemetryFormatter.altitudeLabel(telemetryState.altitudeMeters),
                batteryPercent = TelemetryFormatter.batteryPercent(telemetryState),
                batteryText = TelemetryFormatter.batteryLabel(telemetryState.batteryPercent),
                gpsText = TelemetryFormatter.gpsLabel(telemetryState),
                attitudeText = TelemetryFormatter.attitudeLabel(telemetryState),
                lastCommandText = commandHistory.firstOrNull() ?: "COMMAND_LONG / MISSION_ITEM / RC_CHANNELS_OVERRIDE を待機中",
                commandLogText = TelemetryFormatter.commandLog(commandHistory),
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DroneEmulatorUiState())

    init {
        viewModelScope.launch {
            repository.receivedCommands.collect { command ->
                commandSummaries.update { history ->
                    (listOf(command.summary) + history).take(8)
                }
            }
        }
    }

    override fun onLocalPortChanged(value: String) {
        localPort.value = value
        validationMessage.value = null
    }

    override fun onRemoteHostChanged(value: String) {
        remoteHost.value = value
        validationMessage.value = null
    }

    override fun onRemotePortChanged(value: String) {
        remotePort.value = value
        validationMessage.value = null
    }

    override fun connect() {
        viewModelScope.launch {
            val localPortValue = localPort.value.toIntOrNull()
            val remotePortValue = remotePort.value.toIntOrNull()
            val remoteHostValue = remoteHost.value.trim()

            validationMessage.value = when {
                localPortValue == null || localPortValue !in 1..65535 -> "ローカルポートは1〜65535で入力してください。"
                remotePortValue == null || remotePortValue !in 1..65535 -> "送信先ポートは1〜65535で入力してください。"
                remoteHostValue.isBlank() -> "送信先ホストを入力してください。"
                else -> null
            }

            if (validationMessage.value != null) {
                return@launch
            }

            runCatching {
                repository.connect(
                    localPort = checkNotNull(localPortValue),
                    remoteHost = remoteHostValue,
                    remotePort = checkNotNull(remotePortValue),
                )
            }.onFailure { throwable ->
                validationMessage.value = throwable.message ?: "接続に失敗しました。"
            }
        }
    }

    override fun disconnect() {
        viewModelScope.launch {
            repository.disconnect()
            validationMessage.value = null
        }
    }

    override fun setAltitude(value: Float) {
        repository.updateAltitudeMeters(value)
    }

    override fun setBattery(value: Float) {
        repository.updateBatteryPercent(value.roundToInt())
    }

    override fun onCleared() {
        super.onCleared()
        viewModelScope.launch {
            repository.disconnect()
        }
    }

    class Factory(
        private val repository: MavlinkRepositoryContract = MavlinkRepository(),
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DroneEmulatorViewModel(repository) as T
        }
    }

    private data class ConnectionInputs(
        val localPort: String,
        val remoteHost: String,
        val remotePort: String,
        val validationMessage: String?,
    )
}



