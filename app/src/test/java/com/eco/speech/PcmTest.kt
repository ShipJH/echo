package com.eco.speech

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class PcmTest {
    private fun pcm(vararg samples: Int): ByteArray =
        ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN).apply { samples.forEach { putShort(it.toShort()) } }.array()

    @Test fun averagesEveryThreeSamplesFrom48k() {
        val out = downsample(pcm(3, 6, 9, -30, 0, 30, 100), 48_000)
        assertArrayEquals(pcm(6, 0), out) // 남는 1샘플은 버린다
    }

    @Test fun keeps16kAsIs() {
        assertArrayEquals(pcm(1, -1, 7), downsample(pcm(1, -1, 7), 16_000))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNonIntegerRatio() { downsample(pcm(1, 2, 3), 44_100) }

    @Test fun readsWavHeader() {
        val data = pcm(10, 20, 30, 40, 50, 60)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + data.size).put("WAVEfmt ".toByteArray()).putInt(16)
        header.putShort(1).putShort(1).putInt(48_000).putInt(96_000).putShort(2).putShort(16)
        header.put("data".toByteArray()).putInt(data.size)
        val file = File.createTempFile("pcm-test", ".wav").apply { writeBytes(header.array() + data); deleteOnExit() }
        assertEquals(4, wavToStt(file).size) // 6샘플 → 2샘플 → 4바이트
    }
}
