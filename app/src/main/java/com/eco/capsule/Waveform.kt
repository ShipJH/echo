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
        val peaks = FloatArray(count) { i ->
            input.seek(44L + i.toLong() * perBucket * 2)
            buffer.clear()
            val read = input.read(buffer.array(), 0, perBucket * 2).coerceAtLeast(0)
            var peak = 0
            for (j in 0 until read / 2) peak = maxOf(peak, abs(buffer.getShort(j * 2).toInt()))
            sqrt(peak / 32767f).coerceIn(0f, 1f)
        }
        // 가장 큰 칸이 꽉 차게 편다. 작게 말한 녹음이 최소 높이 막대만 남아 점선처럼 보였다(2026-11-12 디자인 리뷰).
        // 바닥값이 없으면 무음 녹음의 잡음이 목소리처럼 그려진다. 무음은 평평한 선으로 남긴다.
        val loudest = peaks.maxOrNull() ?: return peaks
        val scale = 1f / maxOf(loudest, NORMALIZE_FLOOR)
        return FloatArray(peaks.size) { (peaks[it] * scale).coerceIn(0f, 1f) }
    }
}

/**
 * 정규화 분모의 바닥(제곱근 척도). 원래 진폭 약 9%(-21 dBFS) 아래는 끝까지 키우지 않는다. 0.1(1%)로 두었더니 에뮬레이터
 * 무음 녹음의 잡음이 고른 막대로 그려졌다(2026-11-12 재캡처). 작게 말한 목소리(진폭 5% 안팎)는 이 값으로도 칸의 70% 넘게 찬다.
 */
private const val NORMALIZE_FLOOR = 0.3f
