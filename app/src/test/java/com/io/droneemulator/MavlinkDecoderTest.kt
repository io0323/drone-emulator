package com.io.droneemulator

import com.io.droneemulator.model.ReceivedCommand
import com.io.droneemulator.repository.mavlink.MavlinkDecoder
import com.io.droneemulator.repository.mavlink.MavlinkEncoder
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MavlinkDecoderTest {
    @Test
    fun decode_parses_command_long() {
        val payload = ByteBuffer.allocate(33)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putFloat(1.0f)
                putFloat(2.0f)
                putFloat(3.0f)
                putFloat(4.0f)
                putFloat(5.0f)
                putFloat(6.0f)
                putFloat(7.0f)
                putShort(400.toShort())
                put(1)
                put(2)
                put(3)
            }
            .array()

        val result = MavlinkDecoder.decode(packetFor(messageId = 76, crcExtra = 152, payload = payload)).single()

        assertTrue(result is ReceivedCommand.CommandLong)
        result as ReceivedCommand.CommandLong
        assertEquals(400, result.commandId)
        assertEquals(3, result.confirmation)
        assertEquals(listOf(1f, 2f, 3f, 4f, 5f, 6f, 7f), result.params)
    }

    @Test
    fun decode_parses_mission_item() {
        val payload = ByteBuffer.allocate(37)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putFloat(0.1f)
                putFloat(0.2f)
                putFloat(0.3f)
                putFloat(0.4f)
                putFloat(35.12345f)
                putFloat(139.12345f)
                putFloat(80.0f)
                putShort(5)
                putShort(16)
                put(1)
                put(1)
                put(1)
            }
            .array()

        val result = MavlinkDecoder.decode(packetFor(messageId = 39, crcExtra = 254, payload = payload)).single()

        assertTrue(result is ReceivedCommand.MissionItem)
        result as ReceivedCommand.MissionItem
        assertEquals(5, result.sequence)
        assertEquals(16, result.commandId)
        assertEquals(1, result.frame)
        assertEquals(35.12345f, result.x)
        assertEquals(139.12345f, result.y)
        assertEquals(80.0f, result.z)
    }

    @Test
    fun decode_parses_rc_channels_override() {
        val payload = ByteBuffer.allocate(18)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                listOf(1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900).forEach {
                    putShort(it.toShort())
                }
                put(1)
                put(1)
            }
            .array()

        val result = MavlinkDecoder.decode(packetFor(messageId = 70, crcExtra = 124, payload = payload)).single()

        assertTrue(result is ReceivedCommand.RcChannelsOverride)
        result as ReceivedCommand.RcChannelsOverride
        assertEquals(listOf(1200, 1300, 1400, 1500, 1600, 1700, 1800, 1900), result.channels)
    }

    private fun packetFor(messageId: Int, crcExtra: Int, payload: ByteArray): ByteArray =
        MavlinkEncoder.encodePacket(
            messageId = messageId,
            crcExtra = crcExtra,
            payload = payload,
            sequence = 42,
        )
}

