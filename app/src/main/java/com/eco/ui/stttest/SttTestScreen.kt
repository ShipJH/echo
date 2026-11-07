package com.eco.ui.stttest

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eco.speech.*
import com.eco.ui.components.EchoHeader
import com.eco.ui.components.TopTabs
import com.eco.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.foundation.text.selection.SelectionContainer
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private enum class TestMode { DIRECT, WAKE }
private class TestDisplay {
    var status by mutableStateOf("대기 중")
    var text by mutableStateOf("")
    var command by mutableStateOf("")
    var console by mutableStateOf<List<String>>(emptyList())

    fun log(message: String) {
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))
        console = (listOf("$time  $message") + console).take(40)
    }
}

@Composable
fun SttTestScreen(onCreate: () -> Unit, onMyCapsules: () -> Unit) {
    val context = LocalContext.current
    val direct = remember { TestDisplay() }
    val wake = remember { TestDisplay() }
    var mode by remember { mutableStateOf<TestMode?>(null) }
    var awaitingCommand by remember { mutableStateOf(false) }
    var requestId by remember { mutableIntStateOf(0) }
    var ended by remember { mutableStateOf(false) }
    var finalReceived by remember { mutableStateOf(false) }

    fun start(target: TestMode) {
        mode?.let { if (it == TestMode.DIRECT) direct.status = "중지됨" else wake.status = "중지됨" }
        awaitingCommand = false
        ended = false
        finalReceived = false
        requestId++
        mode = target
        (if (target == TestMode.DIRECT) direct else wake).log("테스트 시작")
    }

    // Keep the listener alive after Ended: some services deliver the final text afterwards.
    LaunchedEffect(mode, requestId, ended, finalReceived) {
        if (mode != null && ended) {
            delay(if (finalReceived) 350 else 2000)
            if (mode == TestMode.WAKE) {
                ended = false
                finalReceived = false
                requestId++
            } else mode = null
        }
    }

    DisposableEffect(mode, requestId) {
        val owner = mode
        var disposed = false
        var service: PleosSpeechRecognizer? = null
        val gate = VoiceCommandSession()
        if (owner != null) {
            val display = if (owner == TestMode.DIRECT) direct else wake
            val tag = "eco-stt-test"
            val testLabel = if (owner == TestMode.DIRECT) "[명령 테스트]" else "[에코야 테스트]"
            display.status = "음성 인식 연결 중…"
            display.text = ""
            display.command = ""
            gate.begin()
            try {
                val created = PleosSpeechRecognizer(context) { event ->
                    if (!disposed) {
                        when (event) {
                            PleosSpeechRecognizer.Event.Initialized -> {
                                try { service?.start() } catch (e: Exception) {
                                    display.status = "시작 실패: ${e.message}"; mode = null
                                    display.log(display.status)
                                }
                            }
                            PleosSpeechRecognizer.Event.Ready -> display.status = "마이크 준비 중…"
                            PleosSpeechRecognizer.Event.Started -> display.status = if (owner == TestMode.WAKE && !awaitingCommand) "‘에코야’를 기다립니다" else "명령을 말씀해 주세요"
                            is PleosSpeechRecognizer.Event.Result -> {
                                display.text = event.text
                                if (event.completed) {
                                    val command = gate.acceptFinal(event.text)
                                    if (command != null) {
                                        finalReceived = true
                                        Log.i(tag, "$testLabel 최종 인식: ${event.text}")
                                        display.log("인식된 말: ${event.text}")
                                        var accept = owner == TestMode.DIRECT || awaitingCommand
                                        // A repeated wake word renews command waiting, even if already awake.
                                        if (owner == TestMode.WAKE && event.text.filter { it.isLetterOrDigit() }.contains("에코야")) {
                                            Log.i(tag, "$testLabel 네?")
                                            display.log("응답: 네?")
                                            display.status = "네? 다음 명령을 말해 주세요"
                                            display.command = ""
                                            awaitingCommand = true
                                            accept = command != VoiceCommand.UNKNOWN
                                        }
                                        if (accept) {
                                            val message = commandTestMessage(command)
                                            display.command = message
                                            Log.i(tag, "$testLabel $message")
                                            display.log("테스트 결과: $message")
                                            if (owner == TestMode.WAKE && command != VoiceCommand.UNKNOWN) awaitingCommand = false
                                        } else if (!awaitingCommand) {
                                            display.status = "호출어 없음 · 다시 ‘에코야’를 기다립니다"
                                            display.log(display.status)
                                        }
                                    } else Log.d(tag, "$testLabel 동일 요청의 최종 결과 중복 수신")
                                }
                            }
                            PleosSpeechRecognizer.Event.Ended -> {
                                ended = true
                                Log.d(tag, "$testLabel 인식 종료 · 최종 결과 수신 대기")
                            }
                            is PleosSpeechRecognizer.Event.Error -> {
                                display.status = "오류: ${event.message}"
                                Log.e(tag, "$testLabel ${display.status}")
                                display.log(display.status)
                                mode = null
                            }
                        }
                    }
                }
                service = created
                created.initialize()
            } catch (e: Exception) { display.status = "연결 실패: ${e.message}"; display.log(display.status); mode = null }
        }
        onDispose {
            disposed = true
            gate.end()
            service?.close()
        }
    }

    Column(Modifier.fillMaxSize().background(EchoBackground)) {
        EchoHeader()
        TopTabs(2) { if (it == 0) onCreate() else if (it == 1) onMyCapsules() }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            TestPanel("음성 명령 테스트", "말한 명령을 바로 판별합니다. 로그: eco-stt-test", direct, mode == TestMode.DIRECT,
                { start(TestMode.DIRECT) }, { direct.status = "중지됨"; mode = null })
            TestPanel("에코야 인식 테스트", "에코야 → 네? → 다음 명령. 로그: eco-stt-test", wake, mode == TestMode.WAKE,
                { start(TestMode.WAKE) }, { wake.status = "중지됨"; mode = null; awaitingCommand = false })
            Text("실제 기능은 실행하지 않고 콘솔에만 처리 로그를 출력합니다.", color = EchoMuted, fontSize = 20.sp)
        }
    }
}

@Composable
private fun TestPanel(title: String, description: String, display: TestDisplay, active: Boolean, onStart: () -> Unit, onStop: () -> Unit) {
    Column(Modifier.fillMaxWidth().background(EchoSurface).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, color = EchoText, fontSize = 30.sp)
        Text(description, color = EchoMuted, fontSize = 20.sp)
        Text(display.status, color = EchoAccent, fontSize = 24.sp)
        Text(display.text.ifEmpty { "인식된 음성이 여기에 표시됩니다." }, color = EchoText, fontSize = 28.sp)
        if (display.command.isNotEmpty()) Text(display.command, color = EchoMuted, fontSize = 24.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Button(enabled = !active, onClick = onStart) { Text("스타트", fontSize = 24.sp) }
            Button(enabled = active, onClick = onStop) { Text("중지", fontSize = 24.sp) }
        }
        Text("테스트 콘솔 · 최신순", color = EchoText, fontSize = 24.sp)
        Button(onClick = { display.console = emptyList() }) { Text("로그 지우기", fontSize = 20.sp) }
        SelectionContainer {
            Column(Modifier.fillMaxWidth().heightIn(max = 280.dp)
                .background(EchoBackground).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (display.console.isEmpty()) Text("인식된 말과 처리 결과가 여기에 표시됩니다.", color = EchoMuted, fontSize = 20.sp)
                display.console.forEach { Text(it, color = EchoText, fontSize = 20.sp) }
            }
        }
    }
}

private fun commandTestMessage(command: VoiceCommand): String = when (command) {
    VoiceCommand.START_RECORDING -> "녹음 기능이 활성화되었습니다."
    VoiceCommand.STOP_RECORDING -> "녹음이 종료되었습니다."
    VoiceCommand.SAVE_RECORDING -> "저장되었습니다."
    VoiceCommand.CANCEL_RECORDING -> "취소되었습니다."
    VoiceCommand.PLAY_RECORDING -> "녹음 재생이 시작되었습니다."
    VoiceCommand.STOP_PLAYBACK -> "녹음 재생이 중지되었습니다."
    VoiceCommand.UNKNOWN -> "일치하는 음성 명령이 없습니다."
}
