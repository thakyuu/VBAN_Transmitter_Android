// SPDX-License-Identifier: MIT
// Copyright (c) 2026 thakyuu
package net.akyuu.thakyuu.vbantransmitter

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import net.akyuu.thakyuu.vbantransmitter.databinding.ActivityMainBinding
import java.net.Inet4Address
import java.net.NetworkInterface
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var vbanService: VbanService? = null
    private var isBound = false

    private lateinit var prefs: SharedPreferences

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val recordAudioGranted = permissions[Manifest.permission.RECORD_AUDIO] ?: false
        val postNotificationGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else {
            true
        }

        if (recordAudioGranted && postNotificationGranted) {
            onPermissionsGranted()
        } else {
            Toast.makeText(this, "Permissions required to transmit audio.", Toast.LENGTH_LONG).show()
        }
    }

    // Media projection launcher
    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            startVbanService(result.resultCode, result.data!!)
        } else {
            Toast.makeText(this, "Media projection consent denied.", Toast.LENGTH_SHORT).show()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as VbanService.LocalBinder
            vbanService = binder.getService()
            isBound = true
            vbanService?.setServiceListener(serviceListener)
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            vbanService?.setServiceListener(null)
            vbanService = null
            isBound = false
        }
    }

    private val serviceListener = object : VbanService.ServiceListener {
        override fun onStateChanged(state: VbanService.State) {
            runOnUiThread {
                updateUiState(state)
            }
        }

        override fun onStatsUpdated(packets: Long, bytes: Long) {
            runOnUiThread {
                binding.statsPackets.text = String.format(Locale.getDefault(), "%,d", packets)
                val mbSent = bytes.toDouble() / (1024.0 * 1024.0)
                binding.statsData.text = String.format(Locale.getDefault(), "%.2f MB", mbSent)
            }
        }

        override fun onError(message: String) {
            runOnUiThread {
                Toast.makeText(this@MainActivity, "Streaming Error: $message", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences("vban_prefs", Context.MODE_PRIVATE)

        setupSpinners()
        loadPreferences()
        updateLocalIp()

        binding.btnToggle.setOnClickListener {
            toggleStreaming()
        }
    }

    override fun onStart() {
        super.onStart()
        val intent = Intent(this, VbanService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStop() {
        super.onStop()
        if (isBound) {
            vbanService?.setServiceListener(null)
            unbindService(serviceConnection)
            isBound = false
        }
        savePreferences()
    }

    private fun setupSpinners() {
        // Source Spinner
        val sources = arrayOf("System Audio (Android 10+)", "Microphone")
        val sourceAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sources).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSource.adapter = sourceAdapter

        // Sample Rate Spinner
        val sampleRates = arrayOf("48000 Hz", "44100 Hz", "24000 Hz", "16000 Hz")
        val sampleRateAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sampleRates).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerSampleRate.adapter = sampleRateAdapter

        // Channels Spinner
        val channels = arrayOf("Stereo (2 Channels)", "Mono (1 Channel)")
        val channelsAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, channels).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        binding.spinnerChannels.adapter = channelsAdapter
    }

    private fun loadPreferences() {
        binding.inputIp.setText(prefs.getString("ip", "192.168.1.100"))
        binding.inputPort.setText(prefs.getInt("port", 6980).toString())
        binding.inputStreamName.setText(prefs.getString("stream_name", "Stream1"))
        
        binding.spinnerSource.setSelection(prefs.getInt("source_pos", 0))
        binding.spinnerSampleRate.setSelection(prefs.getInt("sample_rate_pos", 0))
        binding.spinnerChannels.setSelection(prefs.getInt("channels_pos", 0))
    }

    private fun savePreferences() {
        val editor = prefs.edit()
        editor.putString("ip", binding.inputIp.text.toString().trim())
        
        val portStr = binding.inputPort.text.toString().trim()
        editor.putInt("port", portStr.toIntOrNull() ?: 6980)
        
        editor.putString("stream_name", binding.inputStreamName.text.toString().trim())
        
        editor.putInt("source_pos", binding.spinnerSource.selectedItemPosition)
        editor.putInt("sample_rate_pos", binding.spinnerSampleRate.selectedItemPosition)
        editor.putInt("channels_pos", binding.spinnerChannels.selectedItemPosition)
        editor.apply()
    }

    private fun updateLocalIp() {
        val ip = getLocalIpAddress()
        binding.textLocalIp.text = "Local Device IP: $ip"
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address is Inet4Address) {
                        return address.hostAddress ?: "Unknown"
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error retrieving local IP", e)
        }
        return "Not Connected"
    }

    private fun toggleStreaming() {
        if (vbanService?.state == VbanService.State.RUNNING) {
            // Stop streaming
            val stopIntent = Intent(this, VbanService::class.java).apply {
                action = VbanService.ACTION_STOP
            }
            startService(stopIntent)
        } else {
            // Check permissions
            val hasRecordAudio = ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val hasPostNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }

            if (hasRecordAudio && hasPostNotifications) {
                onPermissionsGranted()
            } else {
                val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                }
                requestPermissionLauncher.launch(permissions.toTypedArray())
            }
        }
    }

    private fun onPermissionsGranted() {
        val isSystemAudio = binding.spinnerSource.selectedItemPosition == 0

        if (isSystemAudio) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                Toast.makeText(this, "System Audio Capture requires Android 10+.", Toast.LENGTH_LONG).show()
                return
            }
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } else {
            startVbanService(0, null)
        }
    }

    private fun startVbanService(resultCode: Int, resultData: Intent?) {
        val ip = binding.inputIp.text.toString().trim()
        val portStr = binding.inputPort.text.toString().trim()
        val port = portStr.toIntOrNull() ?: 6980
        val streamName = binding.inputStreamName.text.toString().trim()
        val isSystemAudio = binding.spinnerSource.selectedItemPosition == 0

        val sampleRate = when (binding.spinnerSampleRate.selectedItemPosition) {
            0 -> 48000
            1 -> 44100
            2 -> 24000
            3 -> 16000
            else -> 48000
        }

        val channels = when (binding.spinnerChannels.selectedItemPosition) {
            0 -> 2
            1 -> 1
            else -> 2
        }

        if (ip.isEmpty()) {
            Toast.makeText(this, "Please enter a valid IP address.", Toast.LENGTH_SHORT).show()
            return
        }

        if (streamName.isEmpty()) {
            Toast.makeText(this, "Stream Name cannot be empty.", Toast.LENGTH_SHORT).show()
            return
        }

        val serviceIntent = Intent(this, VbanService::class.java).apply {
            action = VbanService.ACTION_START
            putExtra(VbanService.EXTRA_IP, ip)
            putExtra(VbanService.EXTRA_PORT, port)
            putExtra(VbanService.EXTRA_STREAM_NAME, streamName)
            putExtra(VbanService.EXTRA_IS_SYSTEM_AUDIO, isSystemAudio)
            putExtra(VbanService.EXTRA_SAMPLE_RATE, sampleRate)
            putExtra(VbanService.EXTRA_CHANNELS, channels)
            if (isSystemAudio && resultData != null) {
                putExtra(VbanService.EXTRA_RESULT_CODE, resultCode)
                putExtra(VbanService.EXTRA_RESULT_DATA, resultData)
            }
        }

        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun updateUiState(state: VbanService.State) {
        val indicatorColor = when (state) {
            VbanService.State.STOPPED -> ContextCompat.getColor(this, R.color.state_stopped)
            VbanService.State.RUNNING -> ContextCompat.getColor(this, R.color.state_running)
            VbanService.State.ERROR -> ContextCompat.getColor(this, R.color.state_error)
        }

        // Update dot indicator color
        val background = binding.statusIndicator.background as? GradientDrawable
        background?.setColor(indicatorColor)

        // Update status text
        binding.statusText.text = when (state) {
            VbanService.State.STOPPED -> "STATUS: STOPPED"
            VbanService.State.RUNNING -> "STATUS: TRANSMITTING..."
            VbanService.State.ERROR -> "STATUS: ERROR"
        }

        // Update action button text & color
        if (state == VbanService.State.RUNNING) {
            binding.btnToggle.text = "STOP TRANSMISSION"
            binding.btnToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.state_error)
            )
            // Disable inputs during active transmission to avoid packet corruption
            toggleInputs(false)
        } else {
            binding.btnToggle.text = "START TRANSMISSION"
            binding.btnToggle.backgroundTintList = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.secondary_neon)
            )
            toggleInputs(true)
        }

        // Periodically refresh local IP to catch network connection transitions
        updateLocalIp()
    }

    private fun toggleInputs(enabled: Boolean) {
        binding.inputIp.isEnabled = enabled
        binding.inputPort.isEnabled = enabled
        binding.inputStreamName.isEnabled = enabled
        binding.spinnerSource.isEnabled = enabled
        binding.spinnerChannels.isEnabled = enabled
        binding.spinnerSampleRate.isEnabled = enabled
    }
}
