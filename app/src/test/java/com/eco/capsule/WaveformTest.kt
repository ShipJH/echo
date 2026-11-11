package com.eco.capsule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WaveformTest {
    private fun wav(vararg samples: Int): File {
        val data = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putShort(it.toShort()) } }.array()
        return File.createTempFile("wave-test", ".wav").apply { writeBytes(ByteArray(44) + data); deleteOnExit() }
    }

    @Test fun peakPerBucketWithSquareRootScale() {
        val peaks = wavePeaks(wav(0, 100, -32767, 0, 8192, -10), buckets = 3)
        assertEquals(3, peaks.size)
        assertEquals(kotlin.math.sqrt(100 / 32767f), peaks[0], 1e-4f)
        assertEquals(1f, peaks[1], 1e-4f)
        assertEquals(kotlin.math.sqrt(8192 / 32767f), peaks[2], 1e-4f)
    }

    @Test fun fewerSamplesThanBuckets() {
        assertEquals(2, wavePeaks(wav(5, 6), buckets = 48).size)
    }

    @Test fun emptyOrMissingFile() {
        assertEquals(0, wavePeaks(wav(), buckets = 10).size)
        assertEquals(0, wavePeaks(File("/없는/파일.wav")).size)
    }
}
