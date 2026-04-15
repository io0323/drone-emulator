package com.io.droneemulator.model

sealed interface ReceivedCommand {
    val summary: String

    data class CommandLong(
        val commandId: Int,
        val confirmation: Int,
        val params: List<Float>,
    ) : ReceivedCommand {
        override val summary: String = buildString {
            append("COMMAND_LONG #")
            append(commandId)
            append(" conf=")
            append(confirmation)
            append(" params=")
            append(params.joinToString(prefix = "[", postfix = "]") { "%.2f".format(it) })
        }
    }

    data class MissionItem(
        val sequence: Int,
        val commandId: Int,
        val frame: Int,
        val current: Boolean,
        val autoContinue: Boolean,
        val x: Float,
        val y: Float,
        val z: Float,
    ) : ReceivedCommand {
        override val summary: String =
            "MISSION_ITEM seq=$sequence cmd=$commandId frame=$frame xyz=${"%.5f".format(x)}, ${"%.5f".format(y)}, ${"%.2f".format(z)}"
    }

    data class RcChannelsOverride(
        val channels: List<Int>,
    ) : ReceivedCommand {
        override val summary: String =
            "RC_OVERRIDE ${channels.joinToString(prefix = "[", postfix = "]")}"
    }
}

