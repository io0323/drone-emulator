package com.io.droneemulator.repository.mavlink

import com.io.droneemulator.model.ReceivedCommand
import java.nio.ByteBuffer
import java.nio.ByteOrder

object MavlinkDecoder {
    private const val HeaderLength = 6
    private const val ChecksumLength = 2

    private val crcExtras = mapOf(
        39 to 254,
        70 to 124,
        76 to 152,
    )

    fun decode(datagram: ByteArray): List<ReceivedCommand> {
        val commands = mutableListOf<ReceivedCommand>()
        var index = 0

        while (index < datagram.size) {
            if (datagram[index].toInt() and 0xFF != MavlinkEncoder.Stx) {
                index += 1
                continue
            }

            if (index + HeaderLength > datagram.size) {
                break
            }

            val payloadLength = datagram[index + 1].toInt() and 0xFF
            val packetLength = 1 + HeaderLength - 1 + payloadLength + ChecksumLength
            if (index + packetLength > datagram.size) {
                break
            }

            val sequence = datagram[index + 2].toInt() and 0xFF
            val systemId = datagram[index + 3].toInt() and 0xFF
            val componentId = datagram[index + 4].toInt() and 0xFF
            val messageId = datagram[index + 5].toInt() and 0xFF
            val payloadStart = index + HeaderLength
            val payloadEnd = payloadStart + payloadLength
            val payload = datagram.copyOfRange(payloadStart, payloadEnd)
            val checksum = (datagram[payloadEnd].toInt() and 0xFF) or
                ((datagram[payloadEnd + 1].toInt() and 0xFF) shl 8)

            val crcExtra = crcExtras[messageId]
            if (crcExtra != null && checksum == checksumFor(datagram, index, payloadLength, crcExtra)) {
                parseCommand(messageId, payload)?.let(commands::add)
            }

            index += packetLength
        }

        return commands
    }

    private fun checksumFor(datagram: ByteArray, packetStart: Int, payloadLength: Int, crcExtra: Int): Int {
        val headerAndPayload = datagram.copyOfRange(packetStart + 1, packetStart + HeaderLength + payloadLength)
        return MavlinkEncoder.crcX25(headerAndPayload + byteArrayOf(crcExtra.toByte()))
    }

    private fun parseCommand(messageId: Int, payload: ByteArray): ReceivedCommand? {
        val buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        return when (messageId) {
            76 -> if (payload.size >= 33) {
                ReceivedCommand.CommandLong(
                    commandId = buffer.getShort(28).toInt() and 0xFFFF,
                    confirmation = buffer.get(32).toInt() and 0xFF,
                    params = List(7) { index -> buffer.getFloat(index * 4) },
                )
            } else {
                null
            }

            39 -> if (payload.size >= 37) {
                ReceivedCommand.MissionItem(
                    sequence = buffer.getShort(28).toInt() and 0xFFFF,
                    commandId = buffer.getShort(30).toInt() and 0xFFFF,
                    frame = buffer.get(34).toInt() and 0xFF,
                    current = (buffer.get(35).toInt() and 0xFF) == 1,
                    autoContinue = (buffer.get(36).toInt() and 0xFF) == 1,
                    x = buffer.getFloat(16),
                    y = buffer.getFloat(20),
                    z = buffer.getFloat(24),
                )
            } else {
                null
            }

            70 -> if (payload.size >= 18) {
                ReceivedCommand.RcChannelsOverride(
                    channels = List(8) { index -> buffer.getShort(index * 2).toInt() and 0xFFFF },
                )
            } else {
                null
            }

            else -> null
        }
    }
}


