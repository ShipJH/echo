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
import com.eco.capsule.wavDurationMs
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
        // 엔진이 새로 뜰 때는 진행 중인 녹음이 없다. 이전 실행이 잠그기 전에 끝나며 남긴 임시 파일을 지운다.
        cacheDir.listFiles { f -> f.name.startsWith("echo-recording-") }?.forEach { stale ->
            if (!stale.delete()) Log.w(TAG, "임시 녹음 파일을 지우지 못함: ${stale.name}")
        }
    }

    private var recorder: PcmRecorder? = null
    private var output: File? = null
    private var player: MediaPlayer? = null

    var busy by mutableStateOf(false); private set
    var recording by mutableStateOf(false); private set
    /** 녹음 도중 입력이 끊겼다. 그때까지의 녹음은 파일에 남아 있다. */
    var interrupted by mutableStateOf(false); private set
    /** 재생 중인 파일. 없으면 null. */
    var playing by mutableStateOf<File?>(null); private set
    var level by mutableFloatStateOf(0f); private set
    var positionMs by mutableIntStateOf(0); private set
    var durationMs by mutableIntStateOf(0); private set
    private var startedAt = 0L
    val elapsedMs: Long get() = if (recording) SystemClock.elapsedRealtime() - startedAt else 0L

    /** release 뒤에는 실행기가 닫혀 execute 가 RejectedExecutionException 을 던진다. 그 전에 거른다. */
    private fun post(task: () -> Unit) {
        if (!disposed.get()) worker.execute(task)
    }

    /** 사용자 조작 하나. 앞 작업이 끝나기 전 연타는 버린다. */
    private fun run(action: () -> Unit) {
        if (busy || disposed.get()) return
        busy = true
        post {
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
        // 재생 중이던 녹음이 스피커로 나오면 새 녹음에 섞인다.
        releasePlayer()
        val file = File.createTempFile("echo-recording-", ".wav", cacheDir)
        val candidate = PcmRecorder(file)
        try {
            candidate.start()
            recorder = candidate
            output = file
            main.post { startedAt = SystemClock.elapsedRealtime(); interrupted = false; recording = true; onStarted(file) }
        } catch (error: Exception) {
            Log.e(TAG, "마이크 녹음을 시작하지 못함", error)
            runCatching { candidate.release() }
            file.delete()
            val message = if (error is SecurityException) "마이크 권한을 허용한 뒤 다시 눌러 주세요."
            else "녹음을 시작하지 못했어요. 잠시 후 다시 눌러 주세요."
            main.post { onFailed(message) }
        }
    }

    /** 입력이 끊겼어도 1초 이상 남아 있으면 [onStopped] 에 partial=true 로 넘긴다. */
    fun stopRecording(onStopped: (partial: Boolean) -> Unit, onFailed: (String) -> Unit) = run {
        val active = recorder ?: return@run
        val file = output
        try {
            active.stop()
            main.post { recording = false; level = 0f; onStopped(false) }
        } catch (error: RuntimeException) {
            // PcmRecorder 는 실패해도 finally 에서 WAV 헤더를 써 둔다. 쓸 만큼 남았으면 살린다.
            val usable = file != null && wavDurationMs(file) >= 1_000
            Log.e(TAG, "녹음 종료 중 실패, 남은 녹음 ${if (usable) "사용" else "폐기"}", error)
            main.post {
                recording = false; level = 0f
                if (usable) onStopped(true) else onFailed("녹음이 끊겼거나 너무 짧아요. 다시 녹음해 주세요.")
            }
        } finally {
            active.release()
            recorder = null
            output = null
        }
    }

    /** 녹음 중 최근 피크와 입력 끊김. 화면이 주기적으로 읽는다. */
    fun sampleLevel() {
        val active = recorder ?: return
        post {
            val raw = runCatching { active.maxAmplitude }.getOrDefault(0)
            val failed = active.failure != null
            main.post { level = (raw / 32767f).coerceIn(0f, 1f); if (failed) interrupted = true }
        }
    }

    fun samplePosition() {
        val active = player ?: return
        post {
            val position = runCatching { active.currentPosition }.getOrNull() ?: return@post
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
            media.setOnCompletionListener { done -> post { if (player === done) releasePlayer() else done.release() } }
            media.prepare()
            media.start()
            player = media
            val length = media.duration
            main.post { playing = file; durationMs = length; positionMs = 0; onStarted() }
        } catch (error: Exception) {
            media.release()
            Log.e(TAG, "재생 실패 ${file.name}", error)
            main.post { onFailed("재생하지 못했어요.") }
        }
    }

    /** 화면을 떠날 때 부른다. 다른 작업 중이어도 버려지지 않도록 busy 를 거치지 않고 줄 세운다. */
    fun stopPlayback() = post { releasePlayer() }

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
