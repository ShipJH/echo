package com.eco.speech

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Pleos SpeechToText 3.3.1 하나로 두 가지 일을 한다.
 * - 듣기: request() → 첫 최종 문장 → stop(). 화면의 '말로 하기' 버튼.
 * - 받아쓰기: sendAudio() → completeAudioSend() → 최종 문장들. 잠근 녹음의 자막. ON_DEVICE 에서만 된다(문서).
 * 한 번에 한 일만 한다. 콜백은 메인 스레드로 옮겨 전달한다.
 *
 * SDK 콜백에는 요청 번호가 없다. 같은 요청의 최종 문장이 두 번 오거나 종료 뒤에 늦게 오는 일이 있어(실측),
 * 한 일이 끝나면 [SETTLE_MS] 동안 들어오는 결과를 버린다. 없으면 방금 말한 명령이 바로 이어진 받아쓰기의 자막에
 * 붙었다(2026-11-12 리뷰).
 */
class SpeechService(context: Context) {
    enum class State { CONNECTING, IDLE, LISTENING, TRANSCRIBING, UNAVAILABLE }

    var state by mutableStateOf(State.CONNECTING); private set
    /** 듣는 중 갱신되는 중간 문장. */
    var partial by mutableStateOf(""); private set
    /** UNAVAILABLE 인 이유. 로그와 계측 테스트용. */
    var unavailableReason: String? = null; private set
    /**
     * 다시 만들면 될 수도 있는 UNAVAILABLE. 서비스 연결 실패(차가 켜질 때 CAAS 가 앱보다 늦게 뜨는 경우 등)는 그렇고,
     * 권한이 없는 것은 앱을 다시 깔기 전에는 바뀌지 않아 아니다.
     */
    var retryable = false; private set
    /** 이 차에서 음성 인식을 쓸 수 있다. 아니면 말로 하기를 보이지 않는다. 확인 중(CONNECTING)도 아직 아니다. */
    val supported: Boolean get() = state == State.IDLE || state == State.LISTENING || state == State.TRANSCRIBING

    private val main = Handler(Looper.getMainLooper())
    private val sender = Executors.newSingleThreadExecutor { Thread(it, "EchoSttSend") }
    private val sdk: SpeechToText?
    private var released = false
    private var session = 0
    /** 이 시각(uptimeMillis) 전에 들어온 결과는 앞 일의 꼬리라 버린다. 받아쓰기는 이때까지 기다렸다 보낸다. */
    @Volatile private var settledAt = 0L

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
        // SDK 는 모든 호출에서 이 권한을 확인하고 없으면 SecurityException 을 던진다(3.3.1 바이트코드). 권한은 normal 이라
        // 설치 때 주어지지만, 권한을 정의하는 CAAS 서비스가 없는 차나 앱보다 나중에 깔린 경우엔 없다. 그때 앱이 시작하자마자
        // 죽지 않도록 여기서 거른다.
        val granted = context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED
        sdk = if (!granted) null else try {
            SpeechToText(context.applicationContext, Mode.ON_DEVICE)
        } catch (error: RuntimeException) {
            Log.e(TAG, "STT 생성 실패", error); null
        }
        if (sdk == null) {
            unavailable(if (granted) "STT 생성 실패" else "STT_SERVICE 권한 없음", retry = granted)
        } else try {
            sdk.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    main.post {
                        if (released) return@post
                        sdk.addListener(listener)
                        sdk.setLocale(Locale.KOREA)
                        state = State.IDLE
                    }
                }
                // CAAS 가 꺼져 있으면 bindService 가 실패하고 곧바로 UNKNOWN 으로 온다(2026-11-12 에뮬레이터 실측).
                override fun onFailure(errorType: ErrorType) {
                    main.post { Log.e(TAG, "STT 초기화 실패 $errorType"); unavailable("초기화 실패 $errorType", retry = true) }
                }
            })
        } catch (error: RuntimeException) {
            Log.e(TAG, "STT 초기화 호출 실패", error)
            unavailable("초기화 호출 실패 ${error.javaClass.simpleName}", retry = true)
        }
    }

    private fun unavailable(reason: String, retry: Boolean) {
        unavailableReason = reason
        retryable = retry
        state = State.UNAVAILABLE
    }

    /** 듣기를 시작한다. 바쁘면 false. [onResult] 는 최종 문장, 말이 없었으면 null. */
    fun listen(onResult: (String?) -> Unit, onError: (String) -> Unit): Boolean {
        val stt = sdk ?: return false
        if (state != State.IDLE) return false
        begin(State.LISTENING); onHeard = onResult; onFailed = onError
        try {
            stt.request()
        } catch (error: RuntimeException) {
            Log.e(TAG, "request 실패", error)
            finish(error = "음성 인식을 시작하지 못했어요")
            return true
        }
        watchdog(LISTEN_LIMIT_MS) { stopSdk(); finish(heard = null) }
        return true
    }

    /**
     * 사용자가 그만 듣기를 눌렀다. 이미 받은 문장이 없으면 null 로 끝난다.
     * stop() 뒤에는 onEndedRecognition 이 오지 않았다(2026-11-12 에뮬레이터). 기다리기만 하면 20초 감시가 끝낼 때까지
     * 듣는 띠가 남아 '그만' 이 먹지 않는 것처럼 보였다. 늦게 오는 최종 문장만 잠깐 기다리고 직접 끝낸다.
     */
    fun stopListening() {
        if (state != State.LISTENING) return
        stopSdk()
        ended()
    }

    /** 듣기를 결과 없이 끝낸다. 녹음을 시작할 때: 늦게 온 "녹음해 줘" 가 녹음 중에 실행되면 안 된다. */
    fun cancelListening() {
        if (state != State.LISTENING) return
        stopSdk()
        onHeard = null; onFailed = null
        finish()
    }

    /**
     * 녹음 WAV 를 받아쓴다. 바쁘면 false. 문서 예시는 100ms 분량(3,200바이트)마다 100ms 쉬지만,
     * 2026-11-11 에뮬레이터 실측에서 쉬지 않고 보내도 결과가 같고 3.6배 빨랐다(5초 음성: 6.5초 → 1.8초, SttProbeTest).
     */
    fun transcribe(wav: File, onResult: (String) -> Unit, onError: (String) -> Unit, pauseMs: Long = DEFAULT_PAUSE_MS): Boolean {
        val stt = sdk ?: return false
        if (state != State.IDLE) return false
        begin(State.TRANSCRIBING); onTranscribed = onResult; onFailed = onError
        val mine = session
        sender.execute {
            try {
                val wait = settledAt - SystemClock.uptimeMillis()
                if (wait > 0) Thread.sleep(wait)
                val sent = streamWavToStt(wav, CHUNK) { chunk ->
                    if (mine != session || released) return@streamWavToStt false
                    stt.sendAudio(chunk)
                    if (pauseMs > 0) Thread.sleep(pauseMs)
                    true
                }
                if (mine == session && !released) {
                    stt.completeAudioSend()
                    // 말이 없는 오디오는 결과도 onEndedRecognition 도 오지 않았다(2026-11-11 에뮬레이터, 무음 녹음).
                    // 오디오 길이에 여유를 더한 뒤에도 끝나지 않으면 받은 만큼으로 끝낸다.
                    main.post { if (mine == session) watchdog(sent / BYTES_PER_MS + TRANSCRIBE_SLACK_MS) { finish(transcript = finals.joinToString(" ")) } }
                }
            } catch (error: Exception) {
                Log.e(TAG, "받아쓰기 전송 실패 ${wav.name}", error)
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
        if (released) return
        if (SystemClock.uptimeMillis() < settledAt) { Log.d(TAG, "앞 일의 늦은 결과 버림: $text"); return }
        when (state) {
            State.LISTENING -> {
                partial = text
                // 한 번의 듣기에서는 첫 최종 문장 하나만 받는다. 같은 요청의 최종 결과가 두 번 오기도 했다.
                if (completed && !gotFinal) { gotFinal = true; stopSdk(); finish(heard = text) }
            }
            State.TRANSCRIBING -> if (completed && text.isNotBlank()) finals += text.trim()
            else -> Log.d(TAG, "상태 $state 에서 받은 결과 무시: $text")
        }
    }

    /** 인식 종료 뒤에 최종 문장이 도착하는 경우가 있어(기존 STT 테스트 실측) 잠깐 더 기다린 뒤 끝낸다. */
    private fun ended() {
        if (released) return
        val mine = session
        val waiting = state
        if (waiting != State.LISTENING && waiting != State.TRANSCRIBING) return
        main.postDelayed({
            if (mine != session || state != waiting) return@postDelayed
            if (waiting == State.LISTENING) finish(heard = null) else finish(transcript = finals.joinToString(" "))
        }, END_GRACE_MS)
    }

    private fun failed(type: ErrorType) {
        if (released) return
        Log.e(TAG, "STT 오류 $type (상태 $state)")
        if (state == State.LISTENING || state == State.TRANSCRIBING) finish(error = describe(type))
        // 한국어 엔진이 없거나 이 API 를 지원하지 않는 차. 매번 눌러 실패하게 두지 않고 말로 하기를 거둔다.
        if (unsupported(type)) unavailable("지원하지 않음 $type", retry = true)
    }

    private fun finish(heard: String? = null, transcript: String? = null, error: String? = null) {
        val wasListening = state == State.LISTENING
        val heardCb = onHeard; val doneCb = onTranscribed; val failCb = onFailed
        onHeard = null; onTranscribed = null; onFailed = null
        session++
        settledAt = SystemClock.uptimeMillis() + SETTLE_MS
        state = if (released) State.UNAVAILABLE else State.IDLE
        when {
            error != null -> failCb?.invoke(error)
            wasListening -> heardCb?.invoke(heard)
            else -> doneCb?.invoke(transcript.orEmpty())
        }
    }

    /** stop 이 실패해도 이 일은 끝낸다. 실패는 로그로 남긴다. */
    private fun stopSdk() {
        try { sdk?.stop() } catch (error: RuntimeException) { Log.w(TAG, "stop 실패", error) }
    }

    fun release() {
        if (released) return
        released = true
        session++
        onHeard = null; onTranscribed = null; onFailed = null
        state = State.UNAVAILABLE
        sdk?.let { stt ->
            stopSdk()
            try { stt.removeListener(listener); stt.release() } catch (error: RuntimeException) { Log.w(TAG, "STT 해제 실패", error) }
        }
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
        /** 다시 눌러도 같은 결과가 나올 오류. 마이크·시간 초과 같은 일시적인 것은 아니다. */
        fun unsupported(type: ErrorType): Boolean = type == ErrorType.UNAVAILABLE_LANGUAGE || type == ErrorType.NOT_SUPPORTED_API

        private const val TAG = "eco-stt"
        const val PERMISSION = "pleos.car.permission.STT_SERVICE"
        /** 100ms 분량(16k × 2바이트 × 0.1초). */
        const val CHUNK = 3_200
        private const val BYTES_PER_MS = 32L
        const val DEFAULT_PAUSE_MS = 0L
        private const val END_GRACE_MS = 1_500L
        /** 종료 뒤 늦은 결과가 END_GRACE 안에 왔다(실측). 그보다 조금 길게. */
        private const val SETTLE_MS = 2_000L
        private const val LISTEN_LIMIT_MS = 20_000L
        private const val TRANSCRIBE_SLACK_MS = 10_000L
    }
}
