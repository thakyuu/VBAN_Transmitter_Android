// SPDX-License-Identifier: MIT
// Copyright (c) 2026 thakyuu
package net.akyuu.thakyuu.vbantransmitter

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.os.Process
import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class AudioCaptureThread(
    private val ipAddressStr: String,
    private val port: Int,
    private val streamName: String,
    private val isSystemAudio: Boolean,
    private val sampleRate: Int,
    private val channels: Int,
    private val mediaProjection: MediaProjection?,
    private val callback: Callback
) : Thread("VbanCaptureThread") {

    interface Callback {
        fun onStarted()
        fun onPacketSent(bytes: Int)
        fun onError(message: String)
        fun onStopped()
    }

    @Volatile
    private var isRunning = false
    private var socket: DatagramSocket? = null
    private var audioRecord: AudioRecord? = null

    fun stopCapturing() {
        isRunning = false
        interrupt()
    }

    @SuppressLint("MissingPermission")
    override fun run() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        
        val samplesPerFrame = 256
        val bytesPerSample = 2 // 16-bit PCM
        val frameSize = channels * bytesPerSample
        val payloadSize = samplesPerFrame * frameSize

        try {
            // 1. Resolve Target IP Address
            val targetAddress = InetAddress.getByName(ipAddressStr)

            // 2. Configure AudioRecord
            val channelMask = if (channels == 2) {
                AudioFormat.CHANNEL_IN_STEREO
            } else {
                AudioFormat.CHANNEL_IN_MONO
            }

            val minBufferSize = AudioRecord.getMinBufferSize(
                sampleRate,
                channelMask,
                AudioFormat.ENCODING_PCM_16BIT
            )

            if (minBufferSize == AudioRecord.ERROR_BAD_VALUE || minBufferSize == AudioRecord.ERROR) {
                throw Exception("Invalid audio capture configuration parameters.")
            }

            // Allocate a buffer larger than minBufferSize to prevent overflow/stutter
            val recordBufferSize = maxOf(minBufferSize, payloadSize * 4)

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(sampleRate)
                .setChannelMask(channelMask)
                .build()

            audioRecord = if (isSystemAudio) {
                if (mediaProjection == null) {
                    throw Exception("System audio selected but MediaProjection is not available.")
                }
                if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
                    throw Exception("System audio capture requires Android 10+.")
                }
                
                val captureConfig = AudioPlaybackCaptureConfiguration.Builder(mediaProjection)
                    .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                    .addMatchingUsage(AudioAttributes.USAGE_GAME)
                    .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                    .build()

                AudioRecord.Builder()
                    .setAudioFormat(format)
                    .setBufferSizeInBytes(recordBufferSize)
                    .setAudioPlaybackCaptureConfig(captureConfig)
                    .build()
            } else {
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    channelMask,
                    AudioFormat.ENCODING_PCM_16BIT,
                    recordBufferSize
                )
            }

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                throw Exception("Failed to initialize AudioRecord device.")
            }

            // 3. Setup UDP Socket
            socket = DatagramSocket()
            socket?.reuseAddress = true

            // 4. Start recording
            audioRecord?.startRecording()
            if (audioRecord?.recordingState != AudioRecord.RECORDSTATE_RECORDING) {
                throw Exception("Failed to start audio recording stream.")
            }

            isRunning = true
            callback.onStarted()

            // 5. Transmission loop (Zero allocation inside loop)
            val packetBytes = ByteArray(VbanHeader.HEADER_SIZE + payloadSize)
            var frameCounter = 0

            Log.d("VbanCaptureThread", "Streaming started to $ipAddressStr:$port StreamName=$streamName")

            while (isRunning) {
                // Read audio samples directly into packet buffer offset 28
                val bytesRead = audioRecord?.read(packetBytes, VbanHeader.HEADER_SIZE, payloadSize) ?: -1
                
                if (!isRunning) break

                if (bytesRead < 0) {
                    throw Exception("Error reading audio data from recorder: $bytesRead")
                }

                if (bytesRead > 0) {
                    // Update header fields inside packet buffer
                    val header = VbanHeader.serialize(
                        sampleRate = sampleRate,
                        samplesPerFrame = samplesPerFrame,
                        channels = channels,
                        streamName = streamName,
                        frameCounter = frameCounter
                    )
                    System.arraycopy(header, 0, packetBytes, 0, VbanHeader.HEADER_SIZE)

                    // Stream UDP packet
                    val packet = DatagramPacket(packetBytes, VbanHeader.HEADER_SIZE + bytesRead, targetAddress, port)
                    socket?.send(packet)

                    frameCounter++
                    callback.onPacketSent(bytesRead)
                }
            }

        } catch (e: Exception) {
            Log.e("VbanCaptureThread", "Error in VBAN streaming thread", e)
            callback.onError(e.message ?: "Unknown audio capturing error occurred.")
        } finally {
            cleanup()
            callback.onStopped()
        }
    }

    private fun cleanup() {
        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            // Ignore
        }
        try {
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null

        try {
            socket?.close()
        } catch (e: Exception) {
            // Ignore
        }
        socket = null
    }
}
