package com.eco.speech

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pleos STT sendAudio 예시 형식: 16 kHz, 16-bit, mono PCM (SpeechToText '오디오 데이터 보내기' 문서). */
const val STT_RATE = 16_000

/**
 * 녹음 WAV(PCM16 mono, 헤더 44바이트)를 STT 용 16 kHz PCM16 LE 로 바꿔 [chunk] 바이트씩 [send] 에 넘긴다.
 * 원본 음질은 재생용으로 그대로 두고 받아쓰기에만 이 사본을 쓴다. 정수배 비율만 받는다(48k→16k 는 3 샘플 평균.
 * 평균이 간단한 저역 통과 역할을 한다). 파일을 통째로 읽으면 10분 녹음에 100MB 넘게 잡혀(2026-11-12 리뷰) 조금씩 읽는다.
 * [send] 가 false 를 돌려주면 멈춘다. 넘긴 16 kHz 바이트 수를 돌려준다.
 */
fun streamWavToStt(file: File, chunk: Int, send: (ByteArray) -> Boolean): Long {
    require(chunk > 0 && chunk % 2 == 0) { "chunk 는 샘플(2바이트) 단위여야 합니다: $chunk" }
    file.inputStream().buffered().use { input ->
        val head = input.readNBytes(44)
        require(head.size == 44) { "WAV 헤더가 없습니다" }
        val header = ByteBuffer.wrap(head).order(ByteOrder.LITTLE_ENDIAN)
        val channels = header.getShort(22).toInt()
        val rate = header.getInt(24)
        val bits = header.getShort(34).toInt()
        require(channels == 1 && bits == 16) { "지원하지 않는 WAV 형식: ${channels}ch ${bits}bit" }
        require(rate % STT_RATE == 0) { "16 kHz 의 정수배가 아닌 샘플레이트: $rate" }
        val source = ByteArray(chunk * (rate / STT_RATE))
        var sent = 0L
        while (true) {
            val read = input.readNBytes(source, 0, source.size)
            if (read <= 0) break
            val out = downsample(if (read == source.size) source else source.copyOf(read), rate)
            if (out.isEmpty()) break
            sent += out.size
            if (!send(out)) break
        }
        require(sent > 0) { "WAV 에 오디오가 없습니다" }
        return sent
    }
}

internal fun downsample(pcm: ByteArray, rate: Int): ByteArray {
    require(rate % STT_RATE == 0) { "16 kHz 의 정수배가 아닌 샘플레이트: $rate" }
    val factor = rate / STT_RATE
    val input = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
    val outCount = input.remaining() / factor
    val out = ByteBuffer.allocate(outCount * 2).order(ByteOrder.LITTLE_ENDIAN)
    repeat(outCount) {
        var sum = 0
        repeat(factor) { sum += input.get().toInt() }
        out.putShort((sum / factor).toShort())
    }
    return out.array()
}
