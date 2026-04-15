package com.io.droneemulator

import com.io.droneemulator.model.TelemetryState
import com.io.droneemulator.repository.mavlink.MavlinkEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkEncoderTest {
    @Test
    fun heartbeat_packet_has_expected_structure() {
        val packet = MavlinkEncoder.encodeHeartbeat(sequence = 7, telemetryState = TelemetryState())

        assertEquals(0xFE, packet[0].toInt() and 0xFF)
        assertEquals(9, packet[1].toInt() and 0xFF)
        assertEquals(7, packet[2].toInt() and 0xFF)
        assertEquals(0, packet[5].toInt() and 0xFF)
        assertEquals(17, packet.size)
    }

    @Test
    fun global_position_packet_encodes_altitude_and_coordinates() {
        val telemetry = TelemetryState(
            altitudeMeters = 12.5f,
            absoluteAltitudeMeters = 40.25f,
            latitudeE7 = 356_812_360,
            longitudeE7 = 1_397_671_250,
            yawRad = 1.0f,
        )

        val packet = MavlinkEncoder.encodeGlobalPositionInt(sequence = 1, telemetryState = telemetry, timeBootMillis = 321L)
        val payload = ByteBuffer.wrap(packet, 6, 28).order(ByteOrder.LITTLE_ENDIAN)

        assertEquals(321, payload.int)
        assertEquals(356_812_360, payload.int)
        assertEquals(1_397_671_250, payload.int)
        assertEquals(40_250, payload.int)
        assertEquals(12_500, payload.int)
        payload.short
        payload.short
        payload.short
        assertEquals(5_730, payload.short.toInt())
    }

    @Test
    fun battery_status_packet_clamps_remaining_percentage() {
        val packet = MavlinkEncoder.encodeBatteryStatus(
            sequence = 2,
            telemetryState = TelemetryState(batteryPercent = 150),
        )

        assertEquals(147, packet[5].toInt() and 0xFF)
        assertEquals(36, packet[1].toInt() and 0xFF)
        assertTrue(packet.size > 40)
        assertEquals(100, packet[41].toInt() and 0xFF)
    }
}