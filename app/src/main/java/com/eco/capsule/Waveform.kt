package com.eco.capsule

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 녹음 WAV(PCM16 mono, 헤더 44바이트)를 [buckets] 칸으로 나눠 칸마다 최대 진폭(0..1)을 돌려준다.
 * 사람 귀처럼 작은 소리도 보이도록 제곱근으로 편다. 파일 전체를 메모리에 올리지 않고 칸 단위로 읽는다.
 */
fun wavePeaks(file: File, buckets: Int = 48): FloatArray {
    require(buckets > 0) { "칸 수는 1 이상" }
    if (!file.exists() || file.length() <= 44) return FloatArray(0)
    RandomAccessFile(file, "r").use { input ->
        val samples = ((file.length() - 44) / 2).toInt()
        val perBucket = (samples / buckets).coerceAtLeast(1)
        val count = minOf(buckets, samples)
        val buffer = ByteBuffer.allocate(perBucket * 2).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(count) { i ->
            input.seek(44L + i.toLong() * perBucket * 2)
            buffer.clear()
            val read = input.read(buffer.array(), 0, perBucket * 2).coerceAtLeast(0)
            var peak = 0
            for (j in 0 until read / 2) peak = maxOf(peak, abs(buffer.getShort(j * 2).toInt()))
            sqrt(peak / 32767f).coerceIn(0f, 1f)
        }
    }
}
