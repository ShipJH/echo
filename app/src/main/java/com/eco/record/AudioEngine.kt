package com.eco.record

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 녹음과 재생을 한 오디오 스레드(EchoAudio)에서만 다룬다. PcmRecorder 의 제어 호출은
 * 모두 이 스레드에 있어야 한다. 화면 상태는 메인 스레드에서만 바꾼다.
 */
class AudioEngine(context: Context) {
    private val cacheDir = context.applicationContext.cacheDir
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "EchoAudio") }
    private val main = Handler(Looper.getMainLooper())
    private val disposed = AtomicBoolean(false)

    init {
        // 엔진이 새로 뜰 때는 진행 중인 녹음이 없다. 이전 실행이 봉인 전에 끝나며 남긴 임시 파일을 지운다.
        cacheDir.listFiles { f -> f.name.startsWith("echo-recording-") }?.forEach { stale ->
            if (!stale.delete()) Log.w(TAG, "임시 녹음 파일을 지우지 못함: ${stale.name}")
        }
    }

    private var recorder: PcmRecorder? = null
    private var player: MediaPlayer? = null

    var busy by mutableStateOf(false); private set
    var recording by mutableStateOf(false); private set
    /** 재생 중인 파일. 없으면 null. */
    var playing by mutableStateOf<File?>(null); private set
    var level by mutableFloatStateOf(0f); private set
    var positionMs by mutableIntStateOf(0); private set
    var durationMs by mutableIntStateOf(0); private set
    private var startedAt = 0L
    val elapsedMs: Long get() = if (recording) SystemClock.elapsedRealtime() - startedAt else 0L

    private fun run(action: () -> Unit) {
        if (busy || disposed.get()) return
        busy = true
        worker.execute {
            try {
                if (!disposed.get()) action()
            } catch (error: Exception) {
                Log.e(TAG, "오디오 작업 실패", error)
            } finally {
                main.post { busy = false }
            }
        }
    }

    fun startRecording(onStarted: (File) -> Unit, onFailed: (String) -> Unit) = run {
        val output = File.createTempFile("echo-recording-", ".wav", cacheDir)
        val candidate = PcmRecorder(output)
        try {
            candidate.start()
            recorder = candidate
            main.post { startedAt = SystemClock.elapsedRealtime(); recording = true; onStarted(output) }
        } catch (error: Exception) {
            Log.e(TAG, "마이크 녹음을 시작하지 못함", error)
            runCatching { candidate.release() }
            output.delete()
            val message = when {
                error is SecurityException -> "마이크 권한을 허용한 뒤 다시 눌러 주세요."
                error.message?.contains("start failed", ignoreCase = true) == true -> "오디오 서비스가 녹음을 시작하지 못했어요. 잠시 후 다시 시도해 주세요."
                else -> "녹음을 시작하지 못했어요. 에뮬레이터의 마이크 설정을 확인해 주세요."
            }
            main.post { onFailed(message) }
        }
    }

    fun stopRecording(onStopped: () -> Unit, onFailed: (String) -> Unit) = run {
        val active = recorder ?: return@run
        try {
            active.stop()
            main.post { recording = false; level = 0f; onStopped() }
        } catch (error: RuntimeException) {
            main.post { recording = false; level = 0f; onFailed("녹음 입력이 중단되었거나 너무 짧아요. 다시 시도해 주세요.") }
        } finally {
            active.release()
            recorder = null
        }
    }

    /** 녹음 중 최근 피크. 화면이 주기적으로 읽는다. */
    fun sampleLevel() {
        val active = recorder ?: return
        worker.execute {
            val raw = runCatching { active.maxAmplitude }.getOrDefault(0)
            main.post { level = (raw / 32767f).coerceIn(0f, 1f) }
        }
    }

    fun samplePosition() {
        val active = player ?: return
        worker.execute {
            val position = runCatching { active.currentPosition }.getOrNull() ?: return@execute
            main.post { positionMs = position }
        }
    }

    /** 같은 파일이면 멈추고, 다른 파일이면 바꿔 튼다. */
    fun toggle(file: File, onStarted: () -> Unit = {}, onFailed: (String) -> Unit) = run {
        val wasPlaying = playing
        releasePlayer()
        if (wasPlaying == file) return@run
        val media = MediaPlayer()
        try {
            media.setDataSource(file.absolutePath)
            media.setOnCompletionListener { done ->
                worker.execute { if (player === done) releasePlayer() else done.release() }
            }
            media.prepare()
            media.start()
            player = media
            val length = media.duration
            main.post { playing = file; durationMs = length; positionMs = 0; onStarted() }
        } catch (error: Exception) {
            media.release()
            Log.e(TAG, "재생 실패 ${file.name}", error)
            main.post { onFailed("녹음 파일을 재생하지 못했어요.") }
        }
    }

    fun stopPlayback() = run { releasePlayer() }

    private fun releasePlayer() {
        val active = player ?: return
        player = null
        runCatching { active.stop() }
        active.release()
        main.post { playing = null; positionMs = 0 }
    }

    fun release() {
        if (!disposed.compareAndSet(false, true)) return
        worker.execute {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { player?.release() }
        }
        worker.shutdown()
    }

    private companion object { const val TAG = "EchoRecording" }
}
