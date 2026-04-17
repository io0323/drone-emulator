package com.io.droneemulator.repository.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.content.Context
import android.os.Build
import android.os.ParcelUuid
import com.io.droneemulator.model.BleCommand
import com.io.droneemulator.model.BleState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

@SuppressLint("MissingPermission")
class BleRepository : BleRepositoryContract {

    companion object {
        val SERVICE_UUID: UUID = UUID.fromString("0000FFF0-0000-1000-8000-00805F9B34FB")
        val CONTROL_CHAR_UUID: UUID = UUID.fromString("0000FFF1-0000-1000-8000-00805F9B34FB")
        val STATUS_CHAR_UUID: UUID = UUID.fromString("0000FFF2-0000-1000-8000-00805F9B34FB")
        val CCCD_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
    }

    override val bleState: StateFlow<BleState> get() = _bleState.asStateFlow()
    override val bleCommands: Flow<BleCommand> get() = _bleCommands.asSharedFlow()

    private val _bleState = MutableStateFlow<BleState>(BleState.Idle)
    private val _bleCommands = MutableSharedFlow<BleCommand>(extraBufferCapacity = 32)

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var gattServer: BluetoothGattServer? = null
    private var statusCharacteristic: BluetoothGattCharacteristic? = null
    private var connectedDevice: BluetoothDevice? = null
    private var advertiseCallback: AdvertiseCallback? = null

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice, status: Int, newState: Int) {
            if (newState == BluetoothGatt.STATE_CONNECTED) {
                connectedDevice = device
                _bleState.value = BleState.Connected(device.name ?: device.address)
            } else if (connectedDevice?.address == device.address) {
                connectedDevice = null
                _bleState.value = BleState.Advertising
            }
        }

        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (characteristic.uuid == CONTROL_CHAR_UUID && value != null && value.isNotEmpty()) {
                parseCommand(value)?.let { _bleCommands.tryEmit(it) }
            }
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }

        override fun onDescriptorWriteRequest(
            device: BluetoothDevice,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?,
        ) {
            if (responseNeeded) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, 0, null)
            }
        }
    }

    override fun startAdvertising(context: Context) {
        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: run {
                _bleState.value = BleState.Error("Bluetooth未対応")
                return
            }
        val adapter = bluetoothManager.adapter ?: run {
            _bleState.value = BleState.Error("Bluetooth未対応")
            return
        }
        bluetoothAdapter = adapter

        if (!adapter.isEnabled) {
            _bleState.value = BleState.Error("Bluetoothが無効です")
            return
        }

        setupGattServer(context, bluetoothManager)

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()

        val callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
                _bleState.value = BleState.Advertising
            }

            override fun onStartFailure(errorCode: Int) {
                _bleState.value = BleState.Error("アドバタイズ失敗 code=$errorCode")
            }
        }
        advertiseCallback = callback
        adapter.bluetoothLeAdvertiser?.startAdvertising(settings, data, callback)
            ?: run { _bleState.value = BleState.Error("BLEアドバタイザ未対応") }
    }

    override fun stopAdvertising() {
        advertiseCallback?.let { callback ->
            bluetoothAdapter?.bluetoothLeAdvertiser?.stopAdvertising(callback)
        }
        advertiseCallback = null
        gattServer?.close()
        gattServer = null
        connectedDevice = null
        statusCharacteristic = null
        _bleState.value = BleState.Idle
    }

    override fun notifyStatus(battery: Int, gpsAvailable: Boolean, armed: Boolean, errorCode: Int) {
        val device = connectedDevice ?: return
        val characteristic = statusCharacteristic ?: return
        val server = gattServer ?: return
        val value = byteArrayOf(
            battery.coerceIn(0, 100).toByte(),
            if (gpsAvailable) 1 else 0,
            if (armed) 1 else 0,
            errorCode.toByte(),
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            server.notifyCharacteristicChanged(device, characteristic, false, value)
        } else {
            @Suppress("DEPRECATION")
            characteristic.value = value
            @Suppress("DEPRECATION")
            server.notifyCharacteristicChanged(device, characteristic, false)
        }
    }

    private fun setupGattServer(context: Context, bluetoothManager: BluetoothManager) {
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)

        val controlChar = BluetoothGattCharacteristic(
            CONTROL_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE,
        )

        val statusChar = BluetoothGattCharacteristic(
            STATUS_CHAR_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ,
        )
        statusChar.addDescriptor(
            BluetoothGattDescriptor(
                CCCD_UUID,
                BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE,
            ),
        )

        service.addCharacteristic(controlChar)
        service.addCharacteristic(statusChar)

        statusCharacteristic = statusChar
        gattServer = bluetoothManager.openGattServer(context, gattServerCallback)?.also {
            it.addService(service)
        }
    }

    private fun parseCommand(value: ByteArray): BleCommand? = when (value[0].toInt() and 0xFF) {
        0x01 -> BleCommand.Takeoff
        0x02 -> BleCommand.Land
        0x03 -> BleCommand.Rtl
        0x04 -> if (value.size >= 2) BleCommand.FlightMode(value[1].toInt() and 0xFF) else null
        0x05 -> if (value.size >= 2) BleCommand.Calibrate(value[1].toInt() and 0xFF) else null
        else -> null
    }
}
