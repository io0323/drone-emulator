package com.io.droneemulator

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.io.droneemulator.databinding.ActivityMainBinding
import com.io.droneemulator.viewmodel.DroneEmulatorUiState
import com.io.droneemulator.viewmodel.DroneEmulatorViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: DroneEmulatorViewModel by viewModels {
        DroneEmulatorViewModel.Factory(application)
    }

    private val blePermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            viewModel.toggleBle()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        binding.infoDeviceIpValue.text = getWifiIpAddress()
        bindInputs()
        observeUiState()
    }

    @Suppress("DEPRECATION")
    private fun getWifiIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        if (ip == 0) return "未取得"
        return String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
    }

    private fun bindInputs() = with(binding) {
        connectButton.setOnClickListener { viewModel.connect() }
        disconnectButton.setOnClickListener { viewModel.disconnect() }
        blePairingButton.setOnClickListener {
            bleCard.isVisible = !bleCard.isVisible
        }

        modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            viewModel.onMockModeChanged(checkedId == R.id.radioMock)
        }

        localPortInput.doAfterTextChanged { text ->
            if (localPortInput.hasFocus()) viewModel.onLocalPortChanged(text?.toString().orEmpty())
        }
        remoteHostInput.doAfterTextChanged { text ->
            if (remoteHostInput.hasFocus()) viewModel.onRemoteHostChanged(text?.toString().orEmpty())
        }
        remotePortInput.doAfterTextChanged { text ->
            if (remotePortInput.hasFocus()) viewModel.onRemotePortChanged(text?.toString().orEmpty())
        }

        altitudeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setAltitude(value)
        }
        batterySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) viewModel.setBattery(value)
        }

        armSwitch.setOnCheckedChangeListener { _, _ ->
            viewModel.toggleArm()
        }

        bleToggleButton.setOnClickListener {
            requestBlePermissionsOrToggle()
        }
    }

    private fun observeUiState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    render(state)
                }
            }
        }
    }

    private fun render(state: DroneEmulatorUiState) = with(binding) {
        infoRemoteHostValue.text = state.connection.remoteHostText
        infoRemotePortValue.text = state.connection.remotePortText
        infoLocalPortValue.text = state.connection.localPortText
        infoUdpPortValue.text = state.connection.localPortText

        connectionCard.isVisible = state.connection.isVisible
        mainCard.isVisible = state.main.isVisible

        networkInputsGroup.isVisible = state.connection.showNetworkInputs
        val targetRadioId = if (state.connection.isMockMode) R.id.radioMock else R.id.radioProd
        if (modeRadioGroup.checkedRadioButtonId != targetRadioId) {
            modeRadioGroup.check(targetRadioId)
        }

        syncText(localPortInput, state.connection.localPortText)
        syncText(remoteHostInput, state.connection.remoteHostText)
        syncText(remotePortInput, state.connection.remotePortText)

        connectionStatusText.text = state.connection.statusText
        connectionHelperText.text = state.connection.helperText
        connectButton.isEnabled = state.connection.isConnectEnabled

        activeConnectionText.text = state.main.connectionText
        altitudeValueText.text = state.main.altitudeText
        if (altitudeSlider.value != state.main.altitudeMeters) {
            altitudeSlider.value = state.main.altitudeMeters
        }
        batteryValueText.text = state.main.batteryText
        if (batterySlider.value != state.main.batteryPercent.toFloat()) {
            batterySlider.value = state.main.batteryPercent.toFloat()
        }
        gpsValueText.text = state.main.gpsText
        attitudeValueText.text = state.main.attitudeText

        armValueText.text = state.main.armText
        if (armSwitch.isChecked != state.main.isArmed) {
            armSwitch.setOnCheckedChangeListener(null)
            armSwitch.isChecked = state.main.isArmed
            armSwitch.setOnCheckedChangeListener { _, _ -> viewModel.toggleArm() }
        }

        latestCommandText.text = state.main.lastCommandText
        commandLogText.text = state.main.commandLogText

        bleStateText.text = state.ble.stateText
        bleDeviceText.text = state.ble.connectedDeviceName ?: "未接続"
        bleToggleButton.text = state.ble.toggleButtonLabel
    }

    private fun syncText(editText: com.google.android.material.textfield.TextInputEditText, value: String) {
        if (editText.text?.toString() != value) {
            editText.setText(value)
            editText.setSelection(editText.text?.length ?: 0)
        }
    }

    private fun requestBlePermissionsOrToggle() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
            )
            val allGranted = permissions.all {
                ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
            }
            if (allGranted) {
                viewModel.toggleBle()
            } else {
                blePermissionLauncher.launch(permissions)
            }
        } else {
            viewModel.toggleBle()
        }
    }
}
