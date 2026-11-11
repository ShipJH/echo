package com.eco.speech

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Pleos STT sendAudio 예시 형식: 16 kHz, 16-bit, mono PCM (SpeechToText '오디오 데이터 보내기' 문서). */
const val STT_RATE = 16_000

/**
 * 녹음 WAV(PCM16 mono, 헤더 44바이트)를 STT 용 16 kHz PCM16 LE 로 바꾼다. 원본 음질은 재생용으로 그대로 두고
 * 받아쓰기에만 이 사본을 쓴다. 정수배 비율만 받는다(48k→16k 는 3 샘플 평균. 평균이 간단한 저역 통과 역할을 한다).
 */
fun wavToStt(file: File): ByteArray {
    val bytes = file.readBytes()
    require(bytes.size > 44) { "WAV 에 오디오가 없습니다" }
    val header = ByteBuffer.wrap(bytes, 0, 44).order(ByteOrder.LITTLE_ENDIAN)
    val channels = header.getShort(22).toInt()
    val rate = header.getInt(24)
    val bits = header.getShort(34).toInt()
    require(channels == 1 && bits == 16) { "지원하지 않는 WAV 형식: ${channels}ch ${bits}bit" }
    return downsample(bytes.copyOfRange(44, bytes.size), rate)
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
