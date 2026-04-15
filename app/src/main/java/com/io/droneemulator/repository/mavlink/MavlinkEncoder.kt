package com.io.droneemulator.repository.mavlink

import com.io.droneemulator.model.TelemetryState
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

object MavlinkEncoder {
    const val Stx: Int = 0xFE
    const val DefaultSystemId: Int = 1
    const val DefaultComponentId: Int = 1

    private const val HeartbeatMessageId = 0
    private const val AttitudeMessageId = 30
    private const val GlobalPositionIntMessageId = 33
    private const val BatteryStatusMessageId = 147

    private const val HeartbeatCrcExtra = 50
    private const val AttitudeCrcExtra = 39
    private const val GlobalPositionIntCrcExtra = 104
    private const val BatteryStatusCrcExtra = 154

    fun encodeHeartbeat(
        sequence: Int,
        telemetryState: TelemetryState,
        systemId: Int = DefaultSystemId,
        componentId: Int = DefaultComponentId,
    ): ByteArray {
        val payload = ByteBuffer.allocate(9)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(0)
                put(2)
                put(3)
                put(0x51)
                put(4)
                put(3)
            }
            .array()

        return encodePacket(
            messageId = HeartbeatMessageId,
            crcExtra = HeartbeatCrcExtra,
            payload = payload,
            sequence = sequence,
            systemId = systemId,
            componentId = componentId,
        )
    }

    fun encodeAttitude(
        sequence: Int,
        telemetryState: TelemetryState,
        timeBootMillis: Long,
        systemId: Int = DefaultSystemId,
        componentId: Int = DefaultComponentId,
    ): ByteArray {
        val payload = ByteBuffer.allocate(28)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(timeBootMillis.toInt())
                putFloat(telemetryState.rollRad)
                putFloat(telemetryState.pitchRad)
                putFloat(telemetryState.yawRad)
                putFloat(0.0f)
                putFloat(0.0f)
                putFloat(0.0f)
            }
            .array()

        return encodePacket(
            messageId = AttitudeMessageId,
            crcExtra = AttitudeCrcExtra,
            payload = payload,
            sequence = sequence,
            systemId = systemId,
            componentId = componentId,
        )
    }

    fun encodeGlobalPositionInt(
        sequence: Int,
        telemetryState: TelemetryState,
        timeBootMillis: Long,
        systemId: Int = DefaultSystemId,
        componentId: Int = DefaultComponentId,
    ): ByteArray {
        val relativeAltitudeMillimeters = (telemetryState.altitudeMeters * 1_000f).roundToInt()
        val absoluteAltitudeMillimeters = (telemetryState.absoluteAltitudeMeters * 1_000f).roundToInt()
        val payload = ByteBuffer.allocate(28)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(timeBootMillis.toInt())
                putInt(telemetryState.latitudeE7)
                putInt(telemetryState.longitudeE7)
                putInt(absoluteAltitudeMillimeters)
                putInt(relativeAltitudeMillimeters)
                putShort((telemetryState.velocityXMetersPerSecond * 100f).roundToInt().toShort())
                putShort((telemetryState.velocityYMetersPerSecond * 100f).roundToInt().toShort())
                putShort((telemetryState.velocityZMetersPerSecond * 100f).roundToInt().toShort())
                putShort((telemetryState.yawRad * 57.29578f * 100f).roundToInt().toShort())
            }
            .array()

        return encodePacket(
            messageId = GlobalPositionIntMessageId,
            crcExtra = GlobalPositionIntCrcExtra,
            payload = payload,
            sequence = sequence,
            systemId = systemId,
            componentId = componentId,
        )
    }

    fun encodeBatteryStatus(
        sequence: Int,
        telemetryState: TelemetryState,
        systemId: Int = DefaultSystemId,
        componentId: Int = DefaultComponentId,
    ): ByteArray {
        val perCellMillivolts = (3_400 + (telemetryState.batteryPercent * 8)).coerceAtMost(4_200)
        val payload = ByteBuffer.allocate(36)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putInt(-1)
                putInt(-1)
                putShort(Short.MAX_VALUE)
                repeat(3) {
                    putShort(perCellMillivolts.toShort())
                }
                repeat(7) {
                    putShort(0xFFFF.toShort())
                }
                putShort((-1).toShort())
                put(0)
                put(0)
                put(0)
                put(telemetryState.batteryPercent.coerceIn(0, 100).toByte())
            }
            .array()

        return encodePacket(
            messageId = BatteryStatusMessageId,
            crcExtra = BatteryStatusCrcExtra,
            payload = payload,
            sequence = sequence,
            systemId = systemId,
            componentId = componentId,
        )
    }

    fun encodePacket(
        messageId: Int,
        crcExtra: Int,
        payload: ByteArray,
        sequence: Int,
        systemId: Int = DefaultSystemId,
        componentId: Int = DefaultComponentId,
    ): ByteArray {
        val header = byteArrayOf(
            payload.size.toByte(),
            sequence.toByte(),
            systemId.toByte(),
            componentId.toByte(),
            messageId.toByte(),
        )
        val checksumInput = header + payload + byteArrayOf(crcExtra.toByte())
        val checksum = crcX25(checksumInput)

        return byteArrayOf(Stx.toByte()) +
            header +
            payload +
            byteArrayOf(
                (checksum and 0xFF).toByte(),
                ((checksum shr 8) and 0xFF).toByte(),
            )
    }

    fun crcX25(bytes: ByteArray): Int {
        var crc = 0xFFFF
        bytes.forEach { byte ->
            var tmp = (byte.toInt() xor crc) and 0xFF
            tmp = (tmp xor (tmp shl 4)) and 0xFF
            crc = (crc shr 8) xor (tmp shl 8) xor (tmp shl 3) xor (tmp shr 4)
        }
        return crc and 0xFFFF
    }
}

