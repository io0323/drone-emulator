package com.io.droneemulator.repository.mavlink

import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.model.ReceivedCommand
import com.io.droneemulator.model.TelemetryState
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetSocketAddress
import java.net.SocketException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class MavlinkRepository(
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : MavlinkRepositoryContract {
    override val connectionState: StateFlow<ConnectionState>
        get() = _connectionState.asStateFlow()

    override val telemetryState: StateFlow<TelemetryState>
        get() = _telemetryState.asStateFlow()

    override val receivedCommands: Flow<ReceivedCommand>
        get() = _receivedCommands.asSharedFlow()

    private val repositoryScope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    private val _telemetryState = MutableStateFlow(TelemetryState())
    private val _receivedCommands = MutableSharedFlow<ReceivedCommand>(extraBufferCapacity = 32)
    private val lifecycleMutex = Mutex()

    private var socket: DatagramSocket? = null
    private var remoteAddress: InetSocketAddress? = null
    private var telemetryJob: Job? = null
    private var receiveJob: Job? = null
    private var startEpochMillis: Long = System.currentTimeMillis()
    private var sequence: Int = 0

    override suspend fun connect(localPort: Int, remoteHost: String, remotePort: Int) {
        lifecycleMutex.withLock {
            disconnectInternal()
            _connectionState.value = ConnectionState.Connecting

            runCatching {
                startEpochMillis = System.currentTimeMillis()
                sequence = 0
                socket = DatagramSocket(localPort).apply {
                    reuseAddress = true
                    soTimeout = 0
                }
                remoteAddress = InetSocketAddress(remoteHost, remotePort)
                _connectionState.value = ConnectionState.Connected(localPort, remoteHost, remotePort)
                startReceiveLoop()
                startTelemetryLoop()
            }.onFailure { throwable ->
                disconnectInternal()
                _connectionState.value = ConnectionState.Error(throwable.message ?: "UDP接続に失敗しました")
                throw throwable
            }
        }
    }

    override suspend fun disconnect() {
        lifecycleMutex.withLock {
            disconnectInternal()
            _connectionState.value = ConnectionState.Disconnected
        }
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

    private fun startTelemetryLoop() {
        telemetryJob = repositoryScope.launch {
            launch {
                while (isActive) {
                    sendPacket(MavlinkEncoder.encodeHeartbeat(nextSequence(), telemetryState.value))
                    sendPacket(
                        MavlinkEncoder.encodeGlobalPositionInt(
                            sequence = nextSequence(),
                            telemetryState = telemetryState.value,
                            timeBootMillis = elapsedSinceBoot(),
                        ),
                    )
                    sendPacket(MavlinkEncoder.encodeBatteryStatus(nextSequence(), telemetryState.value))
                    delay(1_000)
                }
            }
            launch {
                while (isActive) {
                    sendPacket(
                        MavlinkEncoder.encodeAttitude(
                            sequence = nextSequence(),
                            telemetryState = telemetryState.value,
                            timeBootMillis = elapsedSinceBoot(),
                        ),
                    )
                    delay(100)
                }
            }
        }
    }

    private fun startReceiveLoop() {
        receiveJob = repositoryScope.launch {
            val buffer = ByteArray(2048)
            while (isActive) {
                val packet = DatagramPacket(buffer, buffer.size)
                val currentSocket = socket ?: break

                try {
                    currentSocket.receive(packet)
                } catch (_: SocketException) {
                    break
                }

                val sender = packet.socketAddress as? InetSocketAddress
                if (sender != null) {
                    remoteAddress = sender
                    val currentState = _connectionState.value
                    if (currentState is ConnectionState.Connected) {
                        _connectionState.value = currentState.copy(
                            remoteHost = sender.address.hostAddress ?: sender.hostString,
                            remotePort = sender.port,
                            lastPacketAtMillis = System.currentTimeMillis(),
                        )
                    }
                }

                val bytes = packet.data.copyOfRange(0, packet.length)
                MavlinkDecoder.decode(bytes).forEach { command ->
                    _receivedCommands.tryEmit(command)
                }
            }
        }
    }

    private suspend fun disconnectInternal() {
        socket?.close()
        telemetryJob?.cancelAndJoin()
        receiveJob?.cancelAndJoin()
        telemetryJob = null
        receiveJob = null
        socket = null
        remoteAddress = null
    }

    private fun sendPacket(payload: ByteArray) {
        val currentSocket = socket ?: return
        val destination = remoteAddress ?: return
        val packet = DatagramPacket(payload, payload.size, destination)
        currentSocket.send(packet)
    }

    private fun nextSequence(): Int {
        val current = sequence and 0xFF
        sequence = (sequence + 1) and 0xFF
        return current
    }

    private fun elapsedSinceBoot(): Long = System.currentTimeMillis() - startEpochMillis
}


