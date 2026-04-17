package com.io.droneemulator.model

sealed interface BleCommand {
    val summary: String

    data object Takeoff : BleCommand {
        override val summary = "BLE: 離陸"
    }

    data object Land : BleCommand {
        override val summary = "BLE: 着陸"
    }

    data object Rtl : BleCommand {
        override val summary = "BLE: RTL"
    }

    data class FlightMode(val mode: Int) : BleCommand {
        override val summary = "BLE: フライトモード $mode"
    }

    data class Calibrate(val type: Int) : BleCommand {
        override val summary = "BLE: キャリブレーション type=$type"
    }
}
