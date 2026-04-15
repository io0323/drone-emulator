package com.io.droneemulator.model

sealed interface ConnectionState {
    data object Disconnected : ConnectionState

    data object Connecting : ConnectionState

    data class Connected(
        val localPort: Int,
        val remoteHost: String,
        val remotePort: Int,
        val lastPacketAtMillis: Long? = null,
    ) : ConnectionState

    data class Error(
        val message: String,
    ) : ConnectionState
}

