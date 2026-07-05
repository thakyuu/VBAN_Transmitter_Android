// SPDX-License-Identifier: MIT
// Copyright (c) 2026 thakyuu
package net.akyuu.thakyuu.vbantransmitter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertArrayEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class VbanHeaderTest {

    @Test
    fun testVbanHeaderSerialization() {
        val sampleRate = 48000
        val samplesPerFrame = 256
        val channels = 2
        val streamName = "Stream1"
        val frameCounter = 12345

        val headerBytes = VbanHeader.serialize(
            sampleRate = sampleRate,
            samplesPerFrame = samplesPerFrame,
            channels = channels,
            streamName = streamName,
            frameCounter = frameCounter
        )

        // 1. Verify header size
        assertEquals(28, headerBytes.size)

        val buffer = ByteBuffer.wrap(headerBytes).order(ByteOrder.LITTLE_ENDIAN)

        // 2. Verify "VBAN" signature
        val sig = ByteArray(4)
        buffer.get(sig)
        assertArrayEquals("VBAN".toByteArray(Charsets.US_ASCII), sig)

        // 3. Verify format_SR (Sample rate index 3 for 48000 Hz)
        val formatSR = buffer.get()
        assertEquals(3, formatSR.toInt())

        // 4. Verify format_nbs (samples - 1 = 255)
        val formatNbs = buffer.get().toInt() and 0xFF
        assertEquals(255, formatNbs)

        // 5. Verify format_nbc (channels - 1 = 1)
        val formatNbc = buffer.get()
        assertEquals(1, formatNbc.toInt())

        // 6. Verify format_bit (PCM16 = 1)
        val formatBit = buffer.get()
        assertEquals(1, formatBit.toInt())

        // 7. Verify Stream Name (16 bytes, ASCII null-padded)
        val nameBytes = ByteArray(16)
        buffer.get(nameBytes)
        val expectedName = ByteArray(16)
        val rawNameBytes = "Stream1".toByteArray(Charsets.US_ASCII)
        System.arraycopy(rawNameBytes, 0, expectedName, 0, rawNameBytes.size)
        assertArrayEquals(expectedName, nameBytes)

        // 8. Verify frame counter (little-endian Int)
        val counter = buffer.getInt()
        assertEquals(12345, counter)
    }

    @Test
    fun testVbanHeaderSampleRateMapping() {
        // Test a few common sample rates mapping
        val map = mapOf(
            16000 to 8,
            44100 to 16,
            48000 to 3,
            96000 to 4
        )

        map.forEach { (rate, expectedIndex) ->
            val bytes = VbanHeader.serialize(
                sampleRate = rate,
                samplesPerFrame = 128,
                channels = 1,
                streamName = "Test",
                frameCounter = 0
            )
            val index = bytes[4].toInt() and 0x1F
            assertEquals("Index mapping failed for sample rate $rate", expectedIndex, index)
        }
    }
}
