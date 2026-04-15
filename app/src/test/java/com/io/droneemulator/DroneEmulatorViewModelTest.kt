package com.io.droneemulator

import com.io.droneemulator.model.ConnectionState
import com.io.droneemulator.ui.theme.TelemetryFormatter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DroneEmulatorViewModelTest {
    @Test
    fun fake_repository_connect_updates_connection_state() = runBlocking {
        val repository = FakeMavlinkRepository()

        repository.connect(localPort = 14560, remoteHost = "10.0.2.2", remotePort = 14550)

        assertEquals(ConnectionState.Connected(14560, "10.0.2.2", 14550), repository.connectionState.value)
        assertEquals(FakeMavlinkRepository.ConnectionRequest(14560, "10.0.2.2", 14550), repository.connectCalls.single())
    }

    @Test
    fun fake_repository_updates_telemetry_values() {
        val repository = FakeMavlinkRepository()

        repository.updateAltitudeMeters(48.5f)
        repository.updateBatteryPercent(77)

        assertEquals(48.5f, repository.telemetryState.value.altitudeMeters)
        assertEquals(77, repository.telemetryState.value.batteryPercent)
    }

    @Test
    fun telemetry_formatter_formats_labels_and_empty_log() {
        val connected = ConnectionState.Connected(localPort = 14560, remoteHost = "10.0.2.2", remotePort = 14550)

        assertTrue(TelemetryFormatter.connectionLabel(connected).contains("10.0.2.2:14550"))
        assertEquals("12.5 m", TelemetryFormatter.altitudeLabel(12.5f))
        assertEquals("まだ受信コマンドはありません", TelemetryFormatter.commandLog(emptyList()))
        assertFalse(TelemetryFormatter.connectionStatus(ConnectionState.Disconnected).contains("接続中"))
    }
}


