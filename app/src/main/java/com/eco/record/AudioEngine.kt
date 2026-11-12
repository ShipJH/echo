package com.eco.record

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
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
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 녹음과 재생을 한 오디오 스레드(EchoAudio)에서만 다룬다. PcmRecorder 의 제어 호출은
 * 모두 이 스레드에 있어야 한다. 화면 상태는 메인 스레드에서만 바꾼다.
 */
class AudioEngine(context: Context) {
    /**
     * 잠그기 전 녹음. 예전에는 cacheDir 에 두고 엔진이 뜰 때마다 지워, 잠그기 화면에서 앱이 죽거나 시스템이 프로세스를
     * 거두면 녹음이 조용히 사라졌다(2026-11-12 리뷰). 이제 filesDir 에 두고 다음 실행에서 되살린다.
     */
    private val pendingDir = File(context.applicationContext.filesDir, "pending").apply { mkdirs() }
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()
    private var focus: AudioFocusRequest? = null
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "EchoAudio") }
    private val main = Handler(Looper.getMainLooper())
    private val disposed = AtomicBoolean(false)

    /** 이전 실행이 잠그기 전에 남긴 녹음. 새것부터. 1초가 안 되는 것은 지운다. */
    val recovered: List<File> = recoverPending(context.applicationContext.cacheDir)

    private fun recoverPending(legacyDir: File): List<File> {
        // 2026-11-12 이전 빌드는 cacheDir 에 두었다. 남아 있으면 옮겨서 함께 살린다.
        legacyDir.listFiles { f -> f.name.startsWith("echo-recording-") }?.forEach { old ->
            if (!old.renameTo(File(pendingDir, old.name))) Log.w(TAG, "옛 임시 녹음을 옮기지 못함: ${old.name}")
        }
        return pendingDir.listFiles { f -> f.name.startsWith("echo-recording-") }.orEmpty()
            .filter { file ->
                // 녹음 중에 죽으면 헤더가 비어 있다(헤더는 끝낼 때 쓴다). 파일 길이로 다시 쓴다.
                val usable = try {
                    RandomAccessFile(file, "rw").use { writeWavHeader(it, (file.length() - 44).coerceAtLeast(0).toInt() and 1.inv(), PcmRecorder.RATE) }
                    wavDurationMs(file) >= 1_000
                } catch (error: IOException) {
                    Log.e(TAG, "남은 녹음을 읽지 못함: ${file.name}", error); false
                }
                if (!usable && !file.delete()) Log.w(TAG, "짧은 임시 녹음을 지우지 못함: ${file.name}")
                usable
            }
            .sortedByDescending { it.lastModified() }
            .also { if (it.isNotEmpty()) Log.i(TAG, "잠그기 전 녹음 ${it.size}개를 되살림") }
    }

    private fun takeFocus(gain: Int, onLoss: () -> Unit): Boolean {
        val request = AudioFocusRequest.Builder(gain).setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener({ change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) onLoss()
            }, main).build()
        val granted = audio.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) focus = request else Log.w(TAG, "오디오 포커스 거부됨 (gain=$gain)")
        return granted
    }

    private fun dropFocus() {
        focus?.let(audio::abandonAudioFocusRequest)
        focus = null
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
        // 늦게 온 음성 명령 등으로 녹음 중에 또 불리면 앞 녹음을 덮어써 잃었다. 하나만 한다.
        if (recorder != null) { Log.w(TAG, "이미 녹음 중이라 새 녹음을 시작하지 않음"); return@run }
        var file: File? = null
        var candidate: PcmRecorder? = null
        try {
            file = File.createTempFile("echo-recording-", ".wav", pendingDir)
            candidate = PcmRecorder(file)
            // 라디오·음악을 멈춰 녹음에 섞이지 않게 한다. 거부돼도 녹음은 한다(차량 포커스 정책은 실차 미확인).
            takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE) { Log.i(TAG, "녹음 중 오디오 포커스를 잃음") }
            candidate.start()
            recorder = candidate
            output = file
            main.post { startedAt = SystemClock.elapsedRealtime(); interrupted = false; recording = true; onStarted(file) }
        } catch (error: Exception) {
            Log.e(TAG, "마이크 녹음을 시작하지 못함", error)
            runCatching { candidate?.release() }
            file?.delete()
            dropFocus()
            val message = if (error is SecurityException) "마이크 권한을 허용한 뒤 다시 눌러 주세요"
            else "녹음을 시작하지 못했어요. 잠시 후 다시 눌러 주세요"
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
            val usable = file != null && try { wavDurationMs(file) >= 1_000 } catch (io: IOException) { Log.e(TAG, "남은 녹음 길이를 읽지 못함", io); false }
            Log.e(TAG, "녹음 종료 중 실패, 남은 녹음 ${if (usable) "사용" else "폐기"}", error)
            main.post {
                recording = false; level = 0f
                if (usable) onStopped(true) else onFailed("녹음이 끊겼거나 너무 짧아요. 다시 녹음해 주세요")
            }
        } finally {
            active.release()
            recorder = null
            output = null
            dropFocus()
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
        // 녹음 중에 틀면 스피커 소리가 녹음에 섞인다.
        if (recorder != null) { main.post { onFailed("녹음 중에는 들을 수 없어요") }; return@run }
        // 통화·안내 음성 중이면 포커스를 받지 못한다. 그 위에 겹쳐 틀지 않는다.
        if (!takeFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT) { stopPlayback() }) { main.post { onFailed("지금은 재생할 수 없어요. 잠시 후 다시 눌러 주세요") }; return@run }
        val media = MediaPlayer()
        try {
            media.setAudioAttributes(attributes)
            media.setDataSource(file.absolutePath)
            media.setOnCompletionListener { done -> post { if (player === done) releasePlayer() else done.release() } }
            media.prepare()
            media.start()
            player = media
            val length = media.duration
            main.post { playing = file; durationMs = length; positionMs = 0; onStarted() }
        } catch (error: Exception) {
            media.release()
            dropFocus()
            Log.e(TAG, "재생 실패 ${file.name}", error)
            main.post { onFailed("재생하지 못했어요") }
        }
    }

    /** 화면을 떠날 때 부른다. 다른 작업 중이어도 버려지지 않도록 busy 를 거치지 않고 줄 세운다. */
    fun stopPlayback() = post { releasePlayer() }

    private fun releasePlayer() {
        val active = player ?: return
        player = null
        runCatching { active.stop() }
        active.release()
        dropFocus()
        main.post { playing = null; positionMs = 0 }
    }

    fun release() {
        if (!disposed.compareAndSet(false, true)) return
        worker.execute {
            runCatching { recorder?.stop() }
            runCatching { recorder?.release() }
            runCatching { player?.release() }
            dropFocus()
        }
        worker.shutdown()
    }

    private companion object { const val TAG = "EchoRecording" }
}
