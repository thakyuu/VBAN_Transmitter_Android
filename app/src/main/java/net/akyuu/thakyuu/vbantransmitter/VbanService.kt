// SPDX-License-Identifier: MIT
// Copyright (c) 2026 thakyuu
package net.akyuu.thakyuu.vbantransmitter

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

class VbanService : Service() {

    enum class State {
        STOPPED, RUNNING, ERROR
    }

    interface ServiceListener {
        fun onStateChanged(state: State)
        fun onStatsUpdated(packets: Long, bytes: Long)
        fun onError(message: String)
    }

    private val binder = LocalBinder()
    private var listener: ServiceListener? = null

    private var mediaProjection: MediaProjection? = null
    private var captureThread: AudioCaptureThread? = null

    // Service state
    var state = State.STOPPED
        private set(value) {
            field = value
            listener?.onStateChanged(value)
        }
    
    var packetsSent = 0L
        private set
    var bytesSent = 0L
        private set

    inner class LocalBinder : Binder() {
        fun getService(): VbanService = this@VbanService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setServiceListener(listener: ServiceListener?) {
        this.listener = listener
        // Immediately report current state when bound
        listener?.onStateChanged(state)
        listener?.onStatsUpdated(packetsSent, bytesSent)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START

        if (action == ACTION_STOP) {
            stopStreaming()
            stopSelf()
            return START_NOT_STICKY
        }

        if (action == ACTION_START && state == State.STOPPED) {
            val ip = intent?.getStringExtra(EXTRA_IP) ?: "192.168.1.100"
            val port = intent?.getIntExtra(EXTRA_PORT, 6980) ?: 6980
            val streamName = intent?.getStringExtra(EXTRA_STREAM_NAME) ?: "Stream1"
            val isSystemAudio = intent?.getBooleanExtra(EXTRA_IS_SYSTEM_AUDIO, true) ?: true
            val sampleRate = intent?.getIntExtra(EXTRA_SAMPLE_RATE, 48000) ?: 48000
            val channels = intent?.getIntExtra(EXTRA_CHANNELS, 2) ?: 2

            val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
            val resultData = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent?.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent?.getParcelableExtra(EXTRA_RESULT_DATA)
            }

            startStreaming(ip, port, streamName, isSystemAudio, sampleRate, channels, resultCode, resultData)
        }

        return START_NOT_STICKY
    }

    private fun startStreaming(
        ip: String,
        port: Int,
        streamName: String,
        isSystemAudio: Boolean,
        sampleRate: Int,
        channels: Int,
        resultCode: Int,
        resultData: Intent?
    ) {
        packetsSent = 0L
        bytesSent = 0L
        state = State.RUNNING

        // 1. Show notification and promote to Foreground Service
        val notification = createNotification("Initializing stream...")
        startForeground(NOTIFICATION_ID, notification)

        try {
            // 2. Initialize MediaProjection if System Audio is selected
            if (isSystemAudio) {
                if (resultData == null) {
                    throw Exception("Media projection permission data missing.")
                }
                val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjection = mpManager.getMediaProjection(resultCode, resultData).apply {
                    registerCallback(object : MediaProjection.Callback() {
                        override fun onStop() {
                            Log.d("VbanService", "MediaProjection stopped by system")
                            stopStreaming()
                            stopSelf()
                        }
                    }, null)
                }
            }

            // 3. Launch Audio Capture Thread
            captureThread = AudioCaptureThread(
                ipAddressStr = ip,
                port = port,
                streamName = streamName,
                isSystemAudio = isSystemAudio,
                sampleRate = sampleRate,
                channels = channels,
                mediaProjection = mediaProjection,
                callback = object : AudioCaptureThread.Callback {
                    override fun onStarted() {
                        updateNotification("Streaming audio to $ip:$port...")
                    }

                    override fun onPacketSent(bytes: Int) {
                        packetsSent++
                        bytesSent += bytes
                        listener?.onStatsUpdated(packetsSent, bytesSent)
                    }

                    override fun onError(message: String) {
                        state = State.ERROR
                        listener?.onError(message)
                        updateNotification("Error: $message")
                        stopSelf()
                    }

                    override fun onStopped() {
                        // Managed in cleanup
                    }
                }
            )
            captureThread?.start()

        } catch (e: Exception) {
            state = State.ERROR
            listener?.onError(e.message ?: "Failed to start streaming.")
            stopSelf()
        }
    }

    private fun stopStreaming() {
        captureThread?.stopCapturing()
        captureThread = null

        mediaProjection?.stop()
        mediaProjection = null

        state = State.STOPPED
        Log.d("VbanService", "Streaming service stopped.")
    }

    override fun onDestroy() {
        stopStreaming()
        super.onDestroy()
    }

    // --- Notification Helper Methods ---

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "VBAN Streamer"
            val descriptionText = "Shows active VBAN audio transmitter connection"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(content: String): Notification {
        val stopIntent = Intent(this, VbanService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(
            this,
            0,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val mainIntent = Intent(this, MainActivity::class.java)
        val pendingMainIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("VBAN Transmitter")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.presence_audio_online)
            .setContentIntent(pendingMainIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", pendingStopIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(content: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification(content))
    }

    companion object {
        const val CHANNEL_ID = "vban_service_channel"
        const val NOTIFICATION_ID = 4321

        const val ACTION_START = "net.akyuu.thakyuu.vbantransmitter.START"
        const val ACTION_STOP = "net.akyuu.thakyuu.vbantransmitter.STOP"

        const val EXTRA_IP = "EXTRA_IP"
        const val EXTRA_PORT = "EXTRA_PORT"
        const val EXTRA_STREAM_NAME = "EXTRA_STREAM_NAME"
        const val EXTRA_IS_SYSTEM_AUDIO = "EXTRA_IS_SYSTEM_AUDIO"
        const val EXTRA_SAMPLE_RATE = "EXTRA_SAMPLE_RATE"
        const val EXTRA_CHANNELS = "EXTRA_CHANNELS"
        const val EXTRA_RESULT_CODE = "EXTRA_RESULT_CODE"
        const val EXTRA_RESULT_DATA = "EXTRA_RESULT_DATA"
    }
}
