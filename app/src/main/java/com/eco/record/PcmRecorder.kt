package com.eco.record

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicInteger

/** All control calls belong to EchoAudio; capture owns the file until joined. */
internal class PcmRecorder(private val output: File) {
    private var input: AudioRecord? = null
    private var capture: Thread? = null
    @Volatile private var running = false
    @Volatile var failure: String? = null
        private set
    private val peak = AtomicInteger(0)
    val maxAmplitude: Int get() = peak.getAndSet(0)
    private val rate = RATE

    // 권한은 부르는 쪽(EchoApp.record)이 확인하고, 그 사이 권한이 꺼져 나는 SecurityException 은
    // AudioEngine.startRecording 이 잡아 "마이크 권한을 허용한 뒤…" 로 알린다. lint 는 클래스 너머를 보지 못한다.
    @SuppressLint("MissingPermission")
    fun start() {
        Log.i("EchoRecording", "PCM create: MIC, mono, 48000 Hz, PCM16")
        val minimum = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        check(minimum > 0) { "Unsupported PCM format: $minimum" }
        val device = AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.MIC)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_IN_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
            .setBufferSizeInBytes(maxOf(minimum * 4, rate / 5 * 2)).build()
        input = device
        check(device.state == AudioRecord.STATE_INITIALIZED) { "Audio input initialization failed" }
        device.startRecording()
        check(device.recordingState == AudioRecord.RECORDSTATE_RECORDING) { "Audio input did not start" }
        Log.i("EchoRecording", "PCM started; routed device=${device.routedDevice?.type}")
        running = true
        capture = Thread({
            try {
                RandomAccessFile(output, "rw").use { file ->
                    file.setLength(0)
                    file.write(ByteArray(44))
                    val samples = ShortArray(2400)
                    val bytes = ByteBuffer.allocate(samples.size * 2).order(ByteOrder.LITTLE_ENDIAN)
                    var countBytes = 0
                    try {
                        while (running) {
                            val count = device.read(samples, 0, samples.size, AudioRecord.READ_NON_BLOCKING)
                            check(count >= 0) { "AudioRecord.read failed: $count" }
                            if (count == 0) { Thread.sleep(10); continue }
                            bytes.clear()
                            var level = 0
                            for (i in 0 until count) {
                                bytes.putShort(samples[i])
                                level = maxOf(level, kotlin.math.abs(samples[i].toInt()))
                            }
                            peak.updateAndGet { maxOf(it, level) }
                            file.write(bytes.array(), 0, count * 2)
                            countBytes += count * 2
                        }
                    } finally {
                        writeWavHeader(file, countBytes, rate)
                        Log.i("EchoRecording", "PCM file finalized: $countBytes bytes")
                    }
                }
            } catch (error: Exception) {
                failure = error.message ?: "Audio capture failed"
                running = false
                Log.e("EchoRecording", "PCM capture failed", error)
            }
        }, "EchoPcmCapture").also { it.start() }
    }

    fun stop() {
        running = false
        capture?.join()
        capture = null
        input?.let { if (it.recordingState == AudioRecord.RECORDSTATE_RECORDING) it.stop() }
        check(failure == null) { failure ?: "Audio capture failed" }
        check(output.length() > 44) { "No audio samples received" }
    }

    companion object { const val RATE = 48_000 }

    fun release() {
        running = false
        capture?.join()
        capture = null
        input?.release()
        input = null
    }
}

/** PCM16 mono WAV 헤더(44바이트)를 파일 앞에 쓴다. 녹음을 끝낼 때와, 끝내지 못한 녹음을 되살릴 때. */
internal fun writeWavHeader(file: RandomAccessFile, dataBytes: Int, rate: Int) {
    val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
    header.put("RIFF".toByteArray()).putInt(36 + dataBytes)
    header.put("WAVEfmt ".toByteArray()).putInt(16)
    header.putShort(1).putShort(1).putInt(rate).putInt(rate * 2)
    header.putShort(2).putShort(16).put("data".toByteArray()).putInt(dataBytes)
    file.seek(0); file.write(header.array())
}
