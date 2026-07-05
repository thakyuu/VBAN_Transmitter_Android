// SPDX-License-Identifier: MIT
// Copyright (c) 2026 thakyuu
package net.akyuu.thakyuu.vbantransmitter

import java.nio.ByteBuffer
import java.nio.ByteOrder

object VbanHeader {
    const val HEADER_SIZE = 28

    fun serialize(
        sampleRate: Int,
        samplesPerFrame: Int,
        channels: Int,
        streamName: String,
        frameCounter: Int
    ): ByteArray {
        val buffer = ByteBuffer.allocate(HEADER_SIZE)
        buffer.order(ByteOrder.LITTLE_ENDIAN)

        // 1. "VBAN" (4 bytes)
        buffer.put('V'.code.toByte())
        buffer.put('B'.code.toByte())
        buffer.put('A'.code.toByte())
        buffer.put('N'.code.toByte())

        // 2. format_SR (1 byte): sub-protocol (bits 7-5 = 000 for Audio) | sampleRateIndex (bits 4-0)
        val srIndex = getSampleRateIndex(sampleRate)
        buffer.put((srIndex and 0x1F).toByte())

        // 3. format_nbs (1 byte): samples per frame - 1 (0 to 255 represents 1 to 256)
        buffer.put((samplesPerFrame - 1).toByte())

        // 4. format_nbc (1 byte): channels - 1 (0 for mono, 1 for stereo)
        buffer.put((channels - 1).toByte())

        // 5. format_bit (1 byte): format / codec (PCM 16-bit = 1)
        buffer.put(0x01.toByte())

        // 6. streamName (16 bytes, ASCII null-padded)
        val nameBytes = streamName.toByteArray(Charsets.US_ASCII)
        val paddedName = ByteArray(16)
        System.arraycopy(nameBytes, 0, paddedName, 0, minOf(nameBytes.size, 16))
        buffer.put(paddedName)

        // 7. frameCounter (4 bytes, little-endian uint32)
        buffer.putInt(frameCounter)

        return buffer.array()
    }

    private fun getSampleRateIndex(sampleRate: Int): Int {
        return when (sampleRate) {
            6000 -> 0
            12000 -> 1
            24000 -> 2
            48000 -> 3
            96000 -> 4
            192000 -> 5
            384000 -> 6
            8000 -> 7
            16000 -> 8
            32000 -> 9
            64000 -> 10
            128000 -> 11
            256000 -> 12
            512000 -> 13
            11025 -> 14
            22050 -> 15
            44100 -> 16
            88200 -> 17
            176400 -> 18
            352800 -> 19
            705600 -> 20
            else -> 3 // Default to 48000 Hz if unsupported
        }
    }
}
