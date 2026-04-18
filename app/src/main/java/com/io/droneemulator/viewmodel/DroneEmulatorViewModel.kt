package com.io.droneemulator.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.io.droneemulator.model.BleState
import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.repository.ble.BleRepository
import com.io.droneemulator.repository.ble.BleRepositoryContract
import com.io.droneemulator.repository.mavlink.MavlinkRepository
import com.io.droneemulator.repository.mavlink.MavlinkRepositoryContract
import com.io.droneemulator.repository.mavlink.MockMavlinkRepository
import com.io.droneemulator.ui.ble.BleUiState
import com.io.droneemulator.ui.connection.ConnectionUiState
import com.io.droneemulator.ui.main.MainUiState
import com.io.droneemulator.ui.theme.TelemetryFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalCoroutinesApi::class)
class DroneEmulatorViewModel(
    application: Application,
    private val mavlinkRepository: MavlinkRepositoryContract,
    private val bleRepository: BleRepositoryContract,
) : AndroidViewModel(application), DroneEmulatorViewModelContract {

    private val localPort = MutableStateFlow("14560")
    private val remoteHost = MutableStateFlow("192.168.3.11")
    private val remotePort = MutableStateFlow("14550")
    private val validationMessage = MutableStateFlow<String?>(null)
    private val commandSummaries = MutableStateFlow<List<String>>(emptyList())
    private val _isMockMode = MutableStateFlow(false)

    private val realRepo: MavlinkRepositoryContract = mavlinkRepository
    private val mockRepo: MockMavlinkRepository = MockMavlinkRepository()

    private val activeRepo: MavlinkRepositoryContract
        get() = if (_isMockMode.value) mockRepo else realRepo

    private val connectionInputs = combine(
        localPort,
        remoteHost,
        remotePort,
        validationMessage,
    ) { lp, rh, rp, vm -> ConnectionInputs(lp, rh, rp, vm) }

    override val uiState: StateFlow<DroneEmulatorUiState> = _isMockMode
        .flatMapLatest { isMock ->
            val repo = if (isMock) mockRepo else realRepo
            combine(
                connectionInputs,
                repo.connectionState,
                repo.telemetryState,
                commandSummaries,
                bleRepository.bleState,
            ) { inputs, connectionState, telemetryState, commandHistory, bleState ->
                val isConnected = connectionState is ConnectionState.Connected
                DroneEmulatorUiState(
                    connection = ConnectionUiState(
                        isVisible = !isConnected,
                        isMockMode = isMock,
                        showNetworkInputs = !isMock,
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
                        isArmed = telemetryState.isArmed,
                        armText = TelemetryFormatter.armLabel(telemetryState.isArmed),
                        lastCommandText = commandHistory.firstOrNull() ?: "COMMAND_LONG / MISSION_ITEM / RC_CHANNELS_OVERRIDE を待機中",
                        commandLogText = TelemetryFormatter.commandLog(commandHistory),
                    ),
                    ble = BleUiState(
                        stateText = TelemetryFormatter.bleStateText(bleState),
                        isAdvertising = bleState != BleState.Idle,
                        connectedDeviceName = (bleState as? BleState.Connected)?.deviceName,
                        toggleButtonLabel = TelemetryFormatter.bleToggleLabel(bleState),
                    ),
                )
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, DroneEmulatorUiState())

    init {
        viewModelScope.launch {
            _isMockMode.flatMapLatest { isMock ->
                if (isMock) mockRepo.receivedCommands else realRepo.receivedCommands
            }.collect { command ->
                commandSummaries.update { history ->
                    (listOf(command.summary) + history).take(8)
                }
            }
        }
        viewModelScope.launch {
            bleRepository.bleCommands.collect { command ->
                commandSummaries.update { history ->
                    (listOf(command.summary) + history).take(8)
                }
            }
        }
        viewModelScope.launch {
            _isMockMode.flatMapLatest { isMock ->
                if (isMock) mockRepo.telemetryState else realRepo.telemetryState
            }.collect { telemetry ->
                bleRepository.notifyStatus(
                    battery = telemetry.batteryPercent,
                    gpsAvailable = true,
                    armed = telemetry.isArmed,
                    errorCode = 0,
                )
            }
        }
    }

    override fun onMockModeChanged(isMock: Boolean) {
        _isMockMode.value = isMock
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
            if (_isMockMode.value) {
                commandSummaries.value = emptyList()
                mockRepo.connect(0, "[モック]", 0)
            } else {
                val localPortValue = localPort.value.toIntOrNull()
                val remotePortValue = remotePort.value.toIntOrNull()
                val remoteHostValue = remoteHost.value.trim()

                validationMessage.value = when {
                    localPortValue == null || localPortValue !in 1..65535 -> "ローカルポートは1〜65535で入力してください。"
                    remotePortValue == null || remotePortValue !in 1..65535 -> "送信先ポートは1〜65535で入力してください。"
                    remoteHostValue.isBlank() -> "送信先ホストを入力してください。"
                    else -> null
                }

                if (validationMessage.value != null) return@launch

                runCatching {
                    realRepo.connect(
                        localPort = checkNotNull(localPortValue),
                        remoteHost = remoteHostValue,
                        remotePort = checkNotNull(remotePortValue),
                    )
                }.onFailure { throwable ->
                    validationMessage.value = throwable.message ?: "接続に失敗しました。"
                }
            }
        }
    }

    override fun disconnect() {
        viewModelScope.launch {
            activeRepo.disconnect()
            validationMessage.value = null
        }
    }

    override fun setAltitude(value: Float) {
        activeRepo.updateAltitudeMeters(value)
    }

    override fun setBattery(value: Float) {
        activeRepo.updateBatteryPercent(value.roundToInt())
    }

    override fun toggleArm() {
        val current = activeRepo.telemetryState.value.isArmed
        activeRepo.updateArmed(!current)
    }

    override fun toggleBle() {
        val current = bleRepository.bleState.value
        if (current == BleState.Idle) {
            bleRepository.startAdvertising(getApplication())
        } else {
            bleRepository.stopAdvertising()
        }
    }

    override fun onCleared() {
        super.onCleared()
        bleRepository.stopAdvertising()
        viewModelScope.launch {
            realRepo.disconnect()
            mockRepo.disconnect()
        }
    }

    class Factory(
        private val application: Application,
        private val mavlinkRepository: MavlinkRepositoryContract = MavlinkRepository(),
        private val bleRepository: BleRepositoryContract = BleRepository(),
    ) : ViewModelProvider.AndroidViewModelFactory(application) {
        @Suppress("UNCHECKED_CAST")
        override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
            return DroneEmulatorViewModel(application, mavlinkRepository, bleRepository) as T
        }
    }

    private data class ConnectionInputs(
        val localPort: String,
        val remoteHost: String,
        val remotePort: String,
        val validationMessage: String?,
    )
}
