package com.eco

import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eco.speech.SpeechService
import com.eco.speech.parseVoiceIntent
import com.eco.speech.VoiceScreen
import com.eco.capsule.wavDurationMs
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 앱 녹음과 같은 형식(48 kHz PCM16 mono WAV)의 합성 음성을 실제 SpeechService 로 받아쓴다.
 * 음성 파일은 scripts/stt-probe.sh 가 macOS `say` 로 만들어 androidTest assets 에 넣는다.
 */
@RunWith(AndroidJUnit4::class)
class SttProbeTest {
    private val tag = "eco-stt-probe"
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private lateinit var speech: SpeechService

    @Before
    fun setUp() {
        instrumentation.runOnMainSync { speech = SpeechService(instrumentation.targetContext) }
        val deadline = SystemClock.elapsedRealtime() + 20_000
        while (speech.state == SpeechService.State.CONNECTING && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
        Log.i(tag, "state=${speech.state} reason=${speech.unavailableReason}")
        assertTrue("STT 가 준비되지 않음: ${speech.state} ${speech.unavailableReason}", speech.state == SpeechService.State.IDLE)
    }

    @After
    fun tearDown() { instrumentation.runOnMainSync { speech.release() } }

    private fun transcribe(name: String, pauseMs: Long): String {
        val file = File(instrumentation.targetContext.cacheDir, name)
        instrumentation.context.assets.open("stt/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
        val done = CountDownLatch(1)
        var text: String? = null
        var error: String? = null
        val started = SystemClock.elapsedRealtime()
        instrumentation.runOnMainSync {
            val accepted = speech.transcribe(file, onResult = { text = it; done.countDown() }, onError = { error = it; done.countDown() }, pauseMs = pauseMs)
            check(accepted) { "transcribe 거부됨: ${speech.state}" }
        }
        assertTrue("60초 안에 끝나지 않음", done.await(60, TimeUnit.SECONDS))
        val elapsed = SystemClock.elapsedRealtime() - started
        val audioMs = wavDurationMs(file)
        Log.i(tag, "$name pause=${pauseMs}ms audio=${audioMs}ms took=${elapsed}ms text=[$text] error=$error intent=${text?.let { parseVoiceIntent(it, LocalDate.now(), VoiceScreen.SEAL) }}")
        assertTrue("오류: $error", error == null)
        return text.orEmpty()
    }

    @Test fun transcribesCommandsAtDocumentedPace() {
        listOf("cmd_year.wav", "cmd_place.wav", "cmd_keep.wav", "cmd_record.wav").forEach { transcribe(it, 100) }
    }

    @Test fun transcribesLongerSpeechAndComparesPace() {
        val paced = transcribe("memory.wav", 100)
        val fast = transcribe("memory.wav", 0)
        assertTrue("받아쓴 문장이 비어 있음", paced.isNotBlank())
        Log.i(tag, "paced/fast 같은 결과=${paced == fast}")
    }
}
