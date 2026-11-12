package com.eco.ui

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.eco.capsule.Capsule
import com.eco.capsule.CapsuleStore
import com.eco.capsule.GeoPoint
import com.eco.capsule.UnlockCondition
import com.eco.capsule.PlaceReading
import com.eco.capsule.leaveMeters
import com.eco.capsule.read
import com.eco.capsule.wavDurationMs
import com.eco.location.PleosLocation
import com.eco.location.describe
import com.eco.record.AudioEngine
import com.eco.record.PlaceState
import com.eco.record.RecordingScreen
import com.eco.record.SealScreen
import com.eco.record.SealState
import com.eco.record.ConditionKind
import com.eco.speech.SpeechService
import com.eco.speech.UnlockChoice
import com.eco.speech.VoiceIntent
import com.eco.speech.VoiceScreen
import com.eco.speech.voiceExamples
import com.eco.speech.voiceHint
import com.eco.ui.components.BOTTOM_BAR
import com.eco.speech.parseVoiceIntent
import com.eco.ui.components.VoiceButton
import com.eco.ui.components.VoicePanel
import com.eco.ui.components.HLine
import com.eco.ui.components.NavItem
import com.eco.ui.components.NavRail
import com.eco.ui.components.Segmented
import com.eco.ui.components.SystemPopup
import com.eco.ui.format.koreanDate
import com.eco.ui.home.HomeScreen
import com.eco.ui.story.StoryScreen
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.File
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

private enum class Destination { HOME, STORY }

/** 같은 문구가 다시 와도 새 알림이다. 문자열을 키로 쓰면 타이머가 다시 돌지 않았다. */
private class Notice(val text: String)

private sealed interface Flow {
    data object Idle : Flow
    data class Recording(val file: File) : Flow
    data class Sealing(val file: File, val durationMs: Long, val choice: SealState = SealState()) : Flow
}

/**
 * 실패는 값으로 돌려주되 코루틴 취소는 삼키지 않는다. runCatching 은 CancellationException 까지 잡아,
 * 취소된 효과가 낡은 목록으로 지오펜스를 거는 일이 있었다(2026-11-11 리뷰).
 */
private suspend fun <T> attempt(block: suspend () -> T): Result<T> = try {
    Result.success(block())
} catch (cancel: CancellationException) {
    throw cancel
} catch (error: Exception) {
    Result.failure(error)
}

@Composable
fun EchoApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { CapsuleStore(context) }
    val engine = remember { AudioEngine(context) }
    var capsules by remember { mutableStateOf(store.load()) }
    var destination by remember { mutableStateOf(Destination.HOME) }
    // 잠그기 전에 앱이 끝난 녹음이 있으면 그 녹음의 잠그기 화면에서 다시 시작한다.
    val recovered = remember { engine.recovered.firstOrNull() }
    var flow by remember { mutableStateOf<Flow>(recovered?.let { Flow.Sealing(it, wavDurationMs(it)) } ?: Flow.Idle) }
    var toast by remember {
        mutableStateOf(when {
            recovered != null -> "끝내지 못한 녹음이 있어요"
            store.unreadable.isNotEmpty() -> "녹음 ${store.unreadable.size}개를 불러오지 못했어요"
            else -> null
        }?.let(::Notice))
    }
    var askOpen by remember { mutableStateOf<Capsule?>(null) }
    // 화면을 켜 둔 채 자정을 넘기면 오늘 열릴 녹음이 잠긴 것으로 보였다. 자정과 화면 복귀 때 다시 읽는다.
    var today by remember { mutableStateOf(LocalDate.now()) }
    LaunchedEffect(today) {
        val midnight = today.plusDays(1).atStartOfDay(ZoneId.systemDefault())
        delay(Duration.between(ZonedDateTime.now(), midnight).toMillis().coerceAtLeast(1_000))
        today = LocalDate.now()
    }

    // uiMode 변경을 액티비티가 직접 받으므로 XML 의 창 바탕·상태바 색은 처음 값에 머문다. 테마가 바뀔 때 맞춘다.
    val background = Echo.colors.background
    SideEffect {
        (context as? Activity)?.window?.let { window ->
            window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
            @Suppress("DEPRECATION")
            window.statusBarColor = background.toArgb()
        }
    }

    // FusedLocation 생성 자체가 실패할 수 있다(서비스 없음). 그 이유를 화면에 보여 준다.
    val location = remember { runCatching { PleosLocation(context) } }
    var locationGranted by remember { mutableStateOf(location.getOrNull()?.hasPermission() == true) }
    var place by remember { mutableStateOf<PlaceState>(PlaceState.NeedsPermission) }
    // STT 는 차마다 있을 수도 없을 수도 있다. 없으면 말로 하기를 보이지 않고, 연결 실패였으면 화면에 돌아올 때 다시 만든다.
    var speech by remember { mutableStateOf(SpeechService(context)) }
    DisposableEffect(Unit) { onDispose { engine.release(); speech.release(); location.getOrNull()?.release() } }

    fun refresh() { capsules = store.load() }
    fun report(message: String) { toast = Notice(message) }
    /** 녹음 정보 저장. 실패하면 알리고 false. 콜백 안에서 예외가 나면 앱이 죽었다(2026-11-12 리뷰). */
    fun save(what: String, block: () -> Unit): Boolean = try {
        block(); true
    } catch (error: Exception) {
        Log.e("EchoStore", "$what 저장 실패", error); report("녹음 상태를 바꾸지 못했어요"); false
    }

    fun locate() {
        val source = location.getOrElse { Log.w("EchoLocation", "위치 서비스 연결 실패", it); place = PlaceState.Unavailable("위치를 찾지 못했어요"); return }
        if (!source.supported) { place = PlaceState.Unavailable("이 차에서는 위치를 쓸 수 없어요", retryable = false); return }
        if (!locationGranted) { place = PlaceState.NeedsPermission; return }
        place = PlaceState.Locating
        scope.launch {
            place = attempt { source.current() }.fold(
                { PlaceState.Ready(it) },
                { Log.w("EchoLocation", "현재 위치 실패: ${it.describe()}", it); PlaceState.Unavailable("위치를 찾지 못했어요") },
            )
        }
    }

    fun finishRecording(current: Flow.Recording) {
        engine.stopRecording(onStopped = { partial ->
            if (flow !== current) return@stopRecording
            flow = Flow.Sealing(current.file, wavDurationMs(current.file))
            if (partial) report("녹음이 중간에 끊겨 그 전까지만 녹음됐어요")
            if (locationGranted) locate()
        }, onFailed = { current.file.delete(); if (flow === current) flow = Flow.Idle; report(it) })
    }

    // 되살린 녹음은 녹음 끝내기를 거치지 않아 위치를 읽지 않았다. 권한이 있어도 "위치 권한을 요청해요" 가 보였다.
    LaunchedEffect(Unit) { if (recovered != null && locationGranted) locate() }

    // 설정에서 권한을 바꾸고 돌아오는 경우가 있어 화면이 다시 보일 때마다 확인한다.
    // 장소 재판정도 이때 다시 한다: '떠났음' 은 앱이 떠 있을 때만 알 수 있어, 확인할 기회를 늘린다.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            // 녹음 중에 다른 화면으로 가면 Android 14 는 백그라운드 마이크를 막아 무음만 남는다. 거기서 끝내고 잠그기로 넘긴다.
            if (event == Lifecycle.Event.ON_STOP) { (flow as? Flow.Recording)?.let(::finishRecording); return@LifecycleEventObserver }
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            resumes++
            today = LocalDate.now()
            if (speech.state == SpeechService.State.UNAVAILABLE && speech.retryable) {
                Log.i("eco-stt", "음성 인식 다시 확인 (이전: ${speech.unavailableReason})")
                speech.release()
                speech = SpeechService(context)
            }
            val granted = location.getOrNull()?.hasPermission() == true
            if (granted != locationGranted) {
                locationGranted = granted
                if (granted && flow is Flow.Sealing) locate()
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationGranted = granted
        if (granted) { locate(); return@rememberLauncherForActivityResult }
        // '다시 묻지 않음' 뒤에는 시스템이 묻지 않고 바로 거부를 돌려준다. 그때는 다시 누르라고 하지 않는다.
        val askable = (context as? Activity)?.shouldShowRequestPermissionRationale(PleosLocation.PERMISSION) == true
        place = if (askable) PlaceState.Unavailable("위치 권한이 없어요")
        else PlaceState.Unavailable("설정에서 이 앱의 위치 권한을 켜 주세요", retryable = false)
    }

    fun startRecording() = engine.startRecording(onStarted = { flow = Flow.Recording(it) }, onFailed = ::report)
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) { startRecording(); return@rememberLauncherForActivityResult }
        val askable = (context as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) == true
        report(if (askable) "녹음하려면 마이크 권한이 필요해요" else "설정에서 이 앱의 마이크 권한을 켜 주세요")
    }
    fun record() {
        // 듣던 중이면 그 결과는 버린다. 늦게 온 "녹음해 줘" 가 녹음 중에 한 번 더 녹음을 시작하면 첫 녹음이 사라졌다.
        speech.cancelListening()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun play(capsule: Capsule) {
        engine.toggle(store.file(capsule), onStarted = {
            if (capsule.openedAt == null && save("들은 표시") { store.markOpened(capsule.fileName) }) refresh()
        }, onFailed = ::report)
    }

    /** 홈과 목록이 모두 여기를 거친다. 잠긴 녹음은 묻고, 열린 녹음은 재생한다. */
    fun open(capsule: Capsule) {
        if (capsule.isUnlocked(today) || engine.playing?.name == capsule.fileName) play(capsule) else askOpen = capsule
    }

    fun seal(sealing: Flow.Sealing, condition: UnlockCondition) {
        if (engine.playing == sealing.file) engine.stopPlayback()
        runCatching { store.seal(sealing.file, condition, (place as? PlaceState.Ready)?.point) }
            .onSuccess { sealed ->
                refresh(); flow = Flow.Idle; destination = Destination.HOME
                // 잠갔다는 건 누른 사람이 안다. 결과만 알린다.
                report(when (val c = sealed.condition) {
                    is UnlockCondition.OnDate -> "${koreanDate(c.date)}에 열려요"
                    is UnlockCondition.AtPlace -> "여기를 떠났다가 다시 오면 열려요"
                    UnlockCondition.Anytime -> "이 차의 이야기에 보관했어요"
                })
            }
            .onFailure {
                Log.e("EchoStore", "잠그기 실패", it)
                report(if (condition == UnlockCondition.Anytime) "보관하지 못했어요. 다시 눌러 주세요" else "잠그지 못했어요. 다시 눌러 주세요")
            }
    }

    fun voiceScreen(): VoiceScreen = when {
        flow is Flow.Sealing -> VoiceScreen.SEAL
        destination == Destination.STORY -> VoiceScreen.STORY
        else -> VoiceScreen.HOME
    }
    fun examples(screen: VoiceScreen) = voiceExamples(screen).take(3).joinToString(" · ") { "“$it”" }

    /** 말로 한 것을 지금 화면에 맞게 실행한다. 해석은 parseVoiceIntent(순수 함수, 단위 테스트). */
    fun act(heard: String) {
        val screen = voiceScreen()
        val intent = parseVoiceIntent(heard, today, screen)
        Log.i("eco-stt", "들은 말 [$heard] ($screen) → $intent")
        when (intent) {
            VoiceIntent.Unknown -> { report("알아듣지 못했어요. “${voiceHint(screen)}”처럼 말해 보세요"); return }
            VoiceIntent.Negated -> { report("그대로 둘게요"); return }
            VoiceIntent.OutOfScope -> { report("이 앱에서는 할 수 없어요"); return }
            else -> Unit
        }
        val sealing = flow as? Flow.Sealing
        if (sealing != null) {
            val s = sealing.choice
            when (intent) {
                is VoiceIntent.Unlock -> {
                    when (val choice = intent.choice) {
                        is UnlockChoice.OnDate -> s.chooseDate(choice.date)
                        UnlockChoice.Place -> {
                            s.kind = ConditionKind.PLACE
                            if (place == PlaceState.NeedsPermission || (place as? PlaceState.Unavailable)?.retryable == true) locationPermission.launch(PleosLocation.PERMISSION)
                        }
                        UnlockChoice.Keep -> s.kind = ConditionKind.ANYTIME
                    }
                    s.picked = true
                    val condition = s.condition(place)
                    when {
                        intent.confirm && condition != null -> seal(sealing, condition)
                        intent.confirm -> report("위치가 확인되면 다시 말해 주세요")
                        // 무엇을 골랐는지 말한다. "내년 5월" 처럼 날 없이 말해 1일로 잡은 것도 여기서 드러난다.
                        else -> report(when (val choice = intent.choice) {
                            is UnlockChoice.OnDate -> "${koreanDate(choice.date)}로 골랐어요. 잠그려면 “잠가 줘”라고 하세요"
                            UnlockChoice.Place -> "‘여기 다시 오면’을 골랐어요. 잠그려면 “잠가 줘”라고 하세요"
                            UnlockChoice.Keep -> "‘언제든’을 골랐어요. 보관하려면 “보관해 줘”라고 하세요"
                        })
                    }
                }
                VoiceIntent.Confirm -> s.condition(place)?.let { seal(sealing, it) } ?: report("위치가 확인되면 다시 말해 주세요")
                VoiceIntent.Yes -> when {
                    !s.picked -> report("언제 열지 먼저 골라 주세요")
                    else -> s.condition(place)?.let { seal(sealing, it) } ?: report("위치가 확인되면 다시 말해 주세요")
                }
                VoiceIntent.Play -> engine.toggle(sealing.file, onFailed = ::report)
                VoiceIntent.Discard -> s.confirmDiscard = true
                else -> Log.w("eco-stt", "잠그기 화면에서 받을 수 없는 명령 $intent")
            }
            return
        }
        when (intent) {
            VoiceIntent.Record -> record()
            // 새로 열린 것이 먼저, 없으면 가장 최근에 열린 것. 잠긴 녹음은 말로 열지 않는다(되돌릴 수 없다).
            VoiceIntent.Play -> (capsules.firstOrNull { it.isWaiting(today) } ?: capsules.firstOrNull { it.isUnlocked(today) })?.let(::open)
                ?: report("들을 수 있는 녹음이 없어요")
            VoiceIntent.ShowStory -> { engine.stopPlayback(); destination = Destination.STORY }
            VoiceIntent.ShowHome -> { engine.stopPlayback(); destination = Destination.HOME }
            else -> Log.w("eco-stt", "목록 화면에서 받을 수 없는 명령 $intent")
        }
    }

    fun listen() {
        // 결과는 누른 화면의 것으로 해석한다. 그사이 터치로 화면이 바뀌었으면(잠그기를 눌렀다 등) 실행하지 않는다.
        // 바뀐 화면에서 해석하면 "들려줘" 가 다른 녹음을 틀어 '새로 열림' 을 지웠다(2026-11-12 리뷰).
        // 스피커에서 나오는 녹음 속 목소리가 명령으로 들리지 않게 재생을 멈춘다. 재생을 멈추는 수단도 된다.
        engine.stopPlayback()
        val askedOn = voiceScreen()
        val askedFlow = flow
        val accepted = speech.listen(
            onResult = { heard ->
                when {
                    heard.isNullOrBlank() -> report("말소리가 들리지 않았어요")
                    flow !== askedFlow || voiceScreen() != askedOn -> report("화면이 바뀌어 실행하지 않았어요")
                    else -> act(heard)
                }
            },
            onError = ::report,
        )
        // 버튼은 쓸 수 있는 차에서만 보이니(supported) 남는 거절 이유는 둘이다.
        if (!accepted) report(if (speech.state == SpeechService.State.TRANSCRIBING) "받아쓰기가 끝나면 다시 눌러 주세요" else "이미 듣고 있어요")
    }

    // 받아쓰기: 아직 받아쓰지 않은 녹음을 하나씩. 이번 실행에서 실패한 것은 다시 시도하지 않고 다음 실행에서 다시 한다.
    val transcribeFailed = remember { mutableSetOf<String>() }
    LaunchedEffect(capsules, speech.state, flow is Flow.Recording) {
        if (speech.state != SpeechService.State.IDLE || flow is Flow.Recording) return@LaunchedEffect
        val target = capsules.firstOrNull { it.transcript == null && it.fileName !in transcribeFailed } ?: return@LaunchedEffect
        speech.transcribe(store.file(target),
            onResult = { text ->
                Log.i("eco-stt", "받아쓰기 ${target.fileName}: [$text]")
                if (save("받아쓰기") { store.setTranscript(target.fileName, text) }) refresh() else transcribeFailed += target.fileName
            },
            onError = { transcribeFailed += target.fileName; Log.e("eco-stt", "받아쓰기 실패 ${target.fileName}: $it") })
    }

    // 장소 녹음: 앱을 켤 때 현재 위치로 한 번 판정하고, 떠 있는 동안은 지오펜스로 받는다.
    val watched = remember { mutableSetOf<String>() }
    LaunchedEffect(capsules, locationGranted, resumes) {
        val source = location.getOrNull()?.takeIf { it.supported && locationGranted } ?: return@LaunchedEffect
        // '지금 열기' 로 이미 연 녹음은 더 볼 필요가 없다. 보면 나중에 "열렸어요" 가 또 떴다.
        val lockedPlaces = capsules.filter { it.condition is UnlockCondition.AtPlace && it.arrivedAt == null && it.openedEarlyAt == null }
        if (lockedPlaces.isEmpty()) return@LaunchedEffect
        val here = attempt { source.current() }.onFailure { Log.w("EchoLocation", "장소 재판정 실패: ${it.describe()}", it) }.getOrNull()
        ensureActive()
        if (here != null) {
            var opened = 0
            lockedPlaces.forEach {
                save("장소 판정") {
                    when ((it.condition as UnlockCondition.AtPlace).read(here.latitude, here.longitude)) {
                        PlaceReading.AWAY -> store.markLeft(it.fileName)
                        PlaceReading.INSIDE -> if (store.markArrived(it.fileName)) opened++
                        PlaceReading.NEAR -> Unit
                    }
                }
            }
            if (opened > 0) { refresh(); report("여기서 잠근 녹음이 열렸어요"); return@LaunchedEffect }
        }
        lockedPlaces.filter { watched.add(it.fileName) }.forEach { capsule ->
            val target = capsule.condition as UnlockCondition.AtPlace
            runCatching {
                source.watch(capsule.fileName, GeoPoint(target.latitude, target.longitude), target.radiusMeters, target.leaveMeters,
                    onEnter = { name ->
                        var arrived = false
                        if (save("장소 도착") { arrived = store.markArrived(name) } && arrived) { refresh(); report("여기서 잠근 녹음이 열렸어요") }
                    },
                    onExit = { name -> save("장소 떠남") { store.markLeft(name) } },
                    onError = { Log.w("EchoLocation", "지오펜스 오류: ${it.describe()}", it); report("장소 확인을 시작하지 못했어요") })
            }.onFailure { watched.remove(capsule.fileName); Log.w("EchoLocation", "지오펜스 등록 실패", it); report("장소 확인을 시작하지 못했어요") }
        }
    }

    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(engine.playing) {
        while (engine.playing != null) {
            engine.samplePosition()
            progress = if (engine.durationMs > 0) engine.positionMs / engine.durationMs.toFloat() else 0f
            delay(200)
        }
        progress = 0f
    }
    // 운전 중 흘끗 읽는다. 4초에 글자당 0.1초를 더하고 7초에서 자른다.
    LaunchedEffect(toast) { toast?.let { delay((4_000L + it.text.length * 100L).coerceAtMost(7_000L)); toast = null } }

    val playingFile = engine.playing?.name
    // 음성 인식을 쓸 수 있는 차에서만 보인다. 듣는 중이 아니면 누를 수 있게 둔다: 받아쓰기 중에 막아 두면 회색 버튼만
    // 보이고 왜 안 되는지 알 수 없었다. 누르면 listen() 이 그 이유를 알린다.
    val voiceButton: (@Composable RowScope.() -> Unit)? =
        if (!speech.supported) null else { { VoiceButton(::listen, enabled = speech.state != SpeechService.State.LISTENING) } }
    Box(Modifier.fillMaxSize().background(Echo.colors.background)) {
        when (val current = flow) {
            is Flow.Recording -> RecordingScreen(engine, onFinish = { finishRecording(current) })
            is Flow.Sealing -> SealScreen(
                durationMs = current.durationMs, state = current.choice, engine = engine, previewFile = current.file, place = place,
                onRequestPlace = { locationPermission.launch(PleosLocation.PERMISSION) },
                onDiscard = { engine.stopPlayback(); current.file.delete(); flow = Flow.Idle; report("녹음을 버렸어요") },
                onSeal = { condition -> seal(current, condition) },
                onError = ::report,
                voice = voiceButton ?: {},
            )
            Flow.Idle -> Shell(destination, onSelect = { engine.stopPlayback(); destination = it }) {
                when (destination) {
                    Destination.HOME -> HomeScreen(capsules, today, playingFile, progress, ::record, ::open, { destination = Destination.STORY }, fileOf = store::file,
                        voice = voiceButton ?: {})
                    Destination.STORY -> StoryScreen(capsules, today, playingFile, progress, ::open, voice = voiceButton,
                        onDelete = { names ->
                            if (playingFile in names) engine.stopPlayback()
                            val failed = store.delete(names); refresh()
                            report(if (failed.isEmpty()) "녹음 ${names.size}개를 삭제했어요" else "녹음 ${failed.size}개를 삭제하지 못했어요")
                        })
                }
            }
        }
        // 잠그기 화면에서는 아래 버튼 줄(버리기·잠그기)을 가리지 않게 그 위에 띄운다.
        val aboveBar = if (flow is Flow.Sealing) Modifier.padding(bottom = BOTTOM_BAR) else Modifier
        if (speech.state == SpeechService.State.LISTENING && flow !is Flow.Recording) {
            VoicePanel(speech.partial, examples(voiceScreen()), onCancel = speech::cancelListening,
                modifier = Modifier.align(Alignment.BottomCenter).then(aboveBar))
        }
        toast?.let { Toast(it.text, Modifier.align(Alignment.BottomCenter).then(aboveBar)) }
    }

    askOpen?.let { capsule ->
        val until = when (val c = capsule.condition) {
            is UnlockCondition.OnDate -> "${koreanDate(c.date)}에 열릴 녹음이에요"
            is UnlockCondition.AtPlace -> "여기 다시 오면 열릴 녹음이에요"
            UnlockCondition.Anytime -> "보관한 녹음이에요"
        }
        SystemPopup("아직 잠긴 녹음이에요", "$until\n지금 열면 다시 잠글 수 없어요", "지금 열기",
            onConfirm = {
                askOpen = null
                store.openEarly(capsule.fileName); refresh()
                capsules.firstOrNull { it.fileName == capsule.fileName }?.let(::play)
            },
            onDismiss = { askOpen = null })
    }
}

private val navItems = listOf(
    NavItem("홈", Icons.Outlined.Home),
    NavItem("이 차의 이야기", Icons.Outlined.ViewAgenda),
)

/** 넓은 창은 차량 설정 앱처럼 왼쪽 목록, 좁은 창(2분할)은 위쪽 세그먼트. */
@Composable
private fun Shell(destination: Destination, onSelect: (Destination) -> Unit, content: @Composable () -> Unit) {
    val items = navItems
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 1000.dp) {
            Row(Modifier.fillMaxSize()) {
                NavRail(items, destination.ordinal, { onSelect(Destination.entries[it]) })
                Box(Modifier.weight(1f)) { content() }
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                Segmented(items.map { it.label }, destination.ordinal, { onSelect(Destination.entries[it]) }, Modifier.fillMaxWidth())
                HLine()
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}

/** Pleos Toast: 앱 영역 좌우·아래 여백 24, 최대 폭 792. */
@Composable
private fun Toast(message: String, modifier: Modifier) {
    Box(modifier.padding(24.dp).widthIn(max = 792.dp).background(Echo.colors.inverse).padding(horizontal = 40.dp, vertical = 28.dp)) {
        Text(message, style = EchoType.body, color = Echo.colors.onInverse)
    }
}
