package com.eco.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pleos SpeechToText 3.3.1 하나로 두 가지 일을 한다.
 * - 듣기: request() → 첫 최종 문장 → stop(). 화면의 '말로 하기' 버튼.
 * - 받아쓰기: sendAudio() → completeAudioSend() → 최종 문장들. 잠근 녹음의 자막. ON_DEVICE 에서만 된다(문서).
 * 한 번에 한 일만 한다. 콜백은 메인 스레드로 옮겨 전달한다.
 */
class SpeechService(context: Context) {
    enum class State { CONNECTING, IDLE, LISTENING, TRANSCRIBING, UNAVAILABLE }

    var state by mutableStateOf(State.CONNECTING); private set
    /** 듣는 중 갱신되는 중간 문장. */
    var partial by mutableStateOf(""); private set
    var unavailableReason: String? = null; private set

    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor { Thread(it, "EchoSttSend") }
    private val sdk = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private var released = false
    private var session = 0

    private var onHeard: ((String?) -> Unit)? = null
    private var onTranscribed: ((String) -> Unit)? = null
    private var onFailed: ((String) -> Unit)? = null
    private val finals = mutableListOf<String>()
    private var gotFinal = false

    private val listener = object : ResultListener {
        override fun onReady() = Unit
        override fun onStartedRecognition() = Unit
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onUpdated(stt: String, completed: Boolean) { main.post { updated(stt, completed) } }
        override fun onEndedRecognition() { main.post { ended() } }
        override fun onError(errorType: ErrorType) { main.post { failed(errorType) } }
    }

    init {
        sdk.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                main.post {
                    if (released) return@post
                    sdk.addListener(listener)
                    sdk.setLocale(Locale.KOREA)
                    state = State.IDLE
                }
            }
            override fun onFailure(errorType: ErrorType) {
                main.post { unavailableReason = errorType.name; state = State.UNAVAILABLE; Log.e(TAG, "STT 초기화 실패 $errorType") }
            }
        })
    }

    /** 듣기를 시작한다. 바쁘면 false. [onResult] 는 최종 문장, 말이 없었으면 null. */
    fun listen(onResult: (String?) -> Unit, onError: (String) -> Unit): Boolean {
        if (state != State.IDLE) return false
        begin(State.LISTENING); onHeard = onResult; onFailed = onError
        sdk.request()
        watchdog(LISTEN_LIMIT_MS) { sdk.stop(); finish(heard = null) }
        return true
    }

    /** 사용자가 그만 듣기를 눌렀다. 이미 받은 문장이 없으면 null 로 끝난다. */
    fun stopListening() {
        if (state == State.LISTENING) sdk.stop()
    }

    /**
     * 16 kHz PCM16 mono 를 받아쓴다. 바쁘면 false. 문서 예시는 100ms 분량(3,200바이트)마다 100ms 쉬지만,
     * 2026-11-11 에뮬레이터 실측에서 쉬지 않고 보내도 결과가 같고 3.6배 빨랐다(5초 음성: 6.5초 → 1.8초, SttProbeTest).
     */
    fun transcribe(pcm16k: ByteArray, onResult: (String) -> Unit, onError: (String) -> Unit, pauseMs: Long = DEFAULT_PAUSE_MS): Boolean {
        if (state != State.IDLE) return false
        begin(State.TRANSCRIBING); onTranscribed = onResult; onFailed = onError
        val mine = session
        sender.execute {
            try {
                var offset = 0
                while (offset < pcm16k.size && mine == session && !released) {
                    val end = minOf(offset + CHUNK, pcm16k.size)
                    sdk.sendAudio(pcm16k.copyOfRange(offset, end))
                    offset = end
                    if (pauseMs > 0) Thread.sleep(pauseMs)
                }
                if (mine == session && !released) {
                    sdk.completeAudioSend()
                    // 말이 없는 오디오는 결과도 onEndedRecognition 도 오지 않았다(2026-11-11 에뮬레이터, 무음 녹음).
                    // 오디오 길이에 여유를 더한 뒤에도 끝나지 않으면 받은 만큼으로 끝낸다.
                    main.post { if (mine == session) watchdog(pcm16k.size / 32L + TRANSCRIBE_SLACK_MS) { finish(transcript = finals.joinToString(" ")) } }
                }
            } catch (error: Exception) {
                Log.e(TAG, "sendAudio 실패", error)
                main.post { if (mine == session) finish(error = error.message ?: "받아쓰기 전송 실패") }
            }
        }
        return true
    }

    /** 같은 세션이 [ms] 뒤에도 끝나지 않았으면 [onTimeout]. 세션이 바뀌면 아무것도 하지 않는다. */
    private fun watchdog(ms: Long, onTimeout: () -> Unit) {
        val mine = session
        val waiting = state
        main.postDelayed({
            if (mine == session && state == waiting) {
                Log.w(TAG, "$waiting ${ms}ms 안에 끝나지 않아 받은 결과로 끝냄")
                onTimeout()
            }
        }, ms)
    }

    private fun begin(next: State) {
        session++
        partial = ""; finals.clear(); gotFinal = false
        state = next
    }

    private fun updated(text: String, completed: Boolean) {
        when (state) {
            State.LISTENING -> {
                partial = text
                // 한 번의 듣기에서는 첫 최종 문장 하나만 받는다. 같은 요청의 최종 결과가 두 번 오기도 했다.
                if (completed && !gotFinal) { gotFinal = true; sdk.stop(); finish(heard = text) }
            }
            State.TRANSCRIBING -> if (completed && text.isNotBlank()) finals += text.trim()
            else -> Log.d(TAG, "상태 $state 에서 받은 결과 무시: $text")
        }
    }

    /** 인식 종료 뒤에 최종 문장이 도착하는 경우가 있어(기존 STT 테스트 실측) 잠깐 더 기다린 뒤 끝낸다. */
    private fun ended() {
        val mine = session
        val waiting = state
        if (waiting != State.LISTENING && waiting != State.TRANSCRIBING) return
        main.postDelayed({
            if (mine != session || state != waiting) return@postDelayed
            if (waiting == State.LISTENING) finish(heard = null) else finish(transcript = finals.joinToString(" "))
        }, END_GRACE_MS)
    }

    private fun failed(type: ErrorType) {
        Log.e(TAG, "STT 오류 $type (상태 $state)")
        if (state == State.LISTENING || state == State.TRANSCRIBING) finish(error = describe(type))
    }

    private fun finish(heard: String? = null, transcript: String? = null, error: String? = null) {
        val wasListening = state == State.LISTENING
        val heardCb = onHeard; val doneCb = onTranscribed; val failCb = onFailed
        onHeard = null; onTranscribed = null; onFailed = null
        session++
        state = if (released) State.UNAVAILABLE else State.IDLE
        when {
            error != null -> failCb?.invoke(error)
            wasListening -> heardCb?.invoke(heard)
            else -> doneCb?.invoke(transcript.orEmpty())
        }
    }

    fun release() {
        if (released) return
        released = true
        session++
        runCatching { sdk.stop() }
        runCatching { sdk.removeListener(listener) }
        sdk.release()
        sender.shutdown()
    }

    private fun describe(type: ErrorType): String = when (type) {
        ErrorType.MIC_ERROR, ErrorType.AUDIO_ERROR -> "마이크를 쓸 수 없어요"
        ErrorType.PERMISSION_DENIED -> "음성 인식 권한이 없어요"
        ErrorType.TIMEOUT -> "말소리가 들리지 않았어요"
        ErrorType.UNAVAILABLE_LANGUAGE -> "한국어 음성 인식을 쓸 수 없어요"
        else -> "음성 인식에 실패했어요"
    }

    companion object {
        private const val TAG = "eco-stt"
        /** 100ms 분량(16k × 2바이트 × 0.1초). */
        const val CHUNK = 3_200
        const val DEFAULT_PAUSE_MS = 0L
        private const val END_GRACE_MS = 1_500L
        private const val LISTEN_LIMIT_MS = 20_000L
        private const val TRANSCRIBE_SLACK_MS = 10_000L
    }
}
