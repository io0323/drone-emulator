package com.io.droneemulator

import android.os.Bundle
import androidx.activity.viewModels
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
        DroneEmulatorViewModel.Factory()
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

        bindInputs()
        observeUiState()
    }

    private fun bindInputs() = with(binding) {
        connectButton.setOnClickListener { viewModel.connect() }
        disconnectButton.setOnClickListener { viewModel.disconnect() }

        localPortInput.doAfterTextChanged { text ->
            if (localPortInput.hasFocus()) {
                viewModel.onLocalPortChanged(text?.toString().orEmpty())
            }
        }
        remoteHostInput.doAfterTextChanged { text ->
            if (remoteHostInput.hasFocus()) {
                viewModel.onRemoteHostChanged(text?.toString().orEmpty())
            }
        }
        remotePortInput.doAfterTextChanged { text ->
            if (remotePortInput.hasFocus()) {
                viewModel.onRemotePortChanged(text?.toString().orEmpty())
            }
        }

        altitudeSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setAltitude(value)
            }
        }
        batterySlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                viewModel.setBattery(value)
            }
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
        connectionCard.isVisible = state.connection.isVisible
        mainCard.isVisible = state.main.isVisible

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
        latestCommandText.text = state.main.lastCommandText
        commandLogText.text = state.main.commandLogText
    }

    private fun syncText(editText: com.google.android.material.textfield.TextInputEditText, value: String) {
        if (editText.text?.toString() != value) {
            editText.setText(value)
            editText.setSelection(editText.text?.length ?: 0)
        }
    }
}
