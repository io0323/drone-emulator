package com.io.droneemulator.repository.ble

import android.content.Context
import com.io.droneemulator.model.BleCommand
import com.io.droneemulator.model.BleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface BleRepositoryContract {
    val bleState: StateFlow<BleState>
    val bleCommands: Flow<BleCommand>

    fun startAdvertising(context: Context)
    fun stopAdvertising()
    fun notifyStatus(battery: Int, gpsAvailable: Boolean, armed: Boolean, errorCode: Int)
}
