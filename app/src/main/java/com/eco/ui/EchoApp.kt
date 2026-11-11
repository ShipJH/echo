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
import com.eco.capsule.contains
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
import com.eco.speech.parseVoiceIntent
import com.eco.speech.wavToStt
import com.eco.ui.components.VoiceButton
import com.eco.ui.components.VoicePanel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
import java.time.LocalDate

private enum class Destination { HOME, STORY }

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
    var flow by remember { mutableStateOf<Flow>(Flow.Idle) }
    var toast by remember { mutableStateOf<String?>(null) }
    var askOpen by remember { mutableStateOf<Capsule?>(null) }
    val today = LocalDate.now()

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
    val speech = remember { SpeechService(context) }
    DisposableEffect(Unit) { onDispose { engine.release(); speech.release(); location.getOrNull()?.release() } }

    fun refresh() { capsules = store.load() }
    fun report(message: String) { toast = message }

    fun locate() {
        val source = location.getOrElse { Log.w("EchoLocation", "위치 서비스 연결 실패", it); place = PlaceState.Unavailable("위치를 가져오지 못했어요"); return }
        if (!source.supported) { place = PlaceState.Unavailable("이 차에서는 위치를 쓸 수 없어요", retryable = false); return }
        if (!locationGranted) { place = PlaceState.NeedsPermission; return }
        place = PlaceState.Locating
        scope.launch {
            place = attempt { source.current() }.fold(
                { PlaceState.Ready(it) },
                { Log.w("EchoLocation", "현재 위치 실패: ${it.describe()}", it); PlaceState.Unavailable("위치를 가져오지 못했어요") },
            )
        }
    }

    // 설정에서 권한을 바꾸고 돌아오는 경우가 있어 화면이 다시 보일 때마다 확인한다.
    // 장소 재판정도 이때 다시 한다: '떠났음' 은 앱이 떠 있을 때만 알 수 있어, 확인할 기회를 늘린다.
    var resumes by remember { mutableIntStateOf(0) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            resumes++
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
        else PlaceState.Unavailable("위치 권한이 꺼져 있어요. 설정에서 ECO의 위치 권한을 켜 주세요.", retryable = false)
    }

    fun startRecording() = engine.startRecording(onStarted = { flow = Flow.Recording(it) }, onFailed = ::report)
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startRecording() else report("녹음하려면 마이크 권한이 필요해요")
    }
    fun record() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) startRecording()
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    fun play(capsule: Capsule) {
        engine.toggle(store.file(capsule), onStarted = {
            if (capsule.openedAt == null) { store.markOpened(capsule.fileName); refresh() }
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
            .onFailure { Log.e("EchoStore", "잠그기 실패", it); report("저장하지 못했어요. 다시 눌러 주세요.") }
    }

    /** 말로 한 것을 지금 화면에 맞게 실행한다. 해석은 parseVoiceIntent(순수 함수, 단위 테스트). */
    fun act(heard: String) {
        val intent = parseVoiceIntent(heard, today)
        Log.i("eco-stt", "들은 말 [$heard] → $intent")
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
                    val condition = s.condition(place)
                    when {
                        intent.confirm && condition != null -> seal(sealing, condition)
                        intent.confirm -> report("위치를 확인한 뒤 잠그기를 눌러 주세요")
                        else -> report("골랐어요. “잠가 줘” 또는 잠그기를 누르세요")
                    }
                }
                VoiceIntent.Confirm -> s.condition(place)?.let { seal(sealing, it) } ?: report("위치를 확인한 뒤 다시 말해 주세요")
                VoiceIntent.Play -> engine.toggle(sealing.file, onFailed = ::report)
                VoiceIntent.Discard -> s.confirmDiscard = true
                VoiceIntent.Record -> report("이 녹음을 잠그거나 버린 뒤에 새로 녹음할 수 있어요")
                VoiceIntent.ShowStory, VoiceIntent.Unknown -> report("“$heard” 를 알아듣지 못했어요")
            }
            return
        }
        when (intent) {
            VoiceIntent.Record -> record()
            VoiceIntent.Play -> capsules.firstOrNull { it.isWaiting(today) }?.let(::open) ?: report("새로 열린 녹음이 없어요")
            VoiceIntent.ShowStory -> destination = Destination.STORY
            is VoiceIntent.Unlock, VoiceIntent.Confirm, VoiceIntent.Discard -> report("먼저 녹음해 주세요")
            VoiceIntent.Unknown -> report("“$heard” 를 알아듣지 못했어요")
        }
    }

    fun listen() {
        val accepted = speech.listen(
            onResult = { heard -> if (heard.isNullOrBlank()) report("말소리가 들리지 않았어요") else act(heard) },
            onError = ::report,
        )
        if (!accepted) report(when (speech.state) {
            SpeechService.State.CONNECTING -> "음성 인식을 준비하고 있어요"
            SpeechService.State.TRANSCRIBING -> "받아쓰는 중이에요. 잠시 후 다시 눌러 주세요"
            SpeechService.State.UNAVAILABLE -> "음성 인식을 쓸 수 없어요"
            else -> "이미 듣고 있어요"
        })
    }

    // 받아쓰기: 아직 받아쓰지 않은 녹음을 하나씩. 이번 실행에서 실패한 것은 다시 시도하지 않고 다음 실행에서 다시 한다.
    val transcribeFailed = remember { mutableSetOf<String>() }
    LaunchedEffect(capsules, speech.state, flow is Flow.Recording) {
        if (speech.state != SpeechService.State.IDLE || flow is Flow.Recording) return@LaunchedEffect
        val target = capsules.firstOrNull { it.transcript == null && it.fileName !in transcribeFailed } ?: return@LaunchedEffect
        val pcm = attempt { withContext(Dispatchers.IO) { wavToStt(store.file(target)) } }
            .onFailure { transcribeFailed += target.fileName; Log.e("eco-stt", "받아쓰기 준비 실패 ${target.fileName}", it) }
            .getOrNull() ?: return@LaunchedEffect
        ensureActive()
        speech.transcribe(pcm,
            onResult = { text -> Log.i("eco-stt", "받아쓰기 ${target.fileName}: [$text]"); store.setTranscript(target.fileName, text); refresh() },
            onError = { transcribeFailed += target.fileName; Log.e("eco-stt", "받아쓰기 실패 ${target.fileName}: $it") })
    }

    // 장소 녹음: 앱을 켤 때 현재 위치로 한 번 판정하고, 떠 있는 동안은 지오펜스로 받는다.
    val watched = remember { mutableSetOf<String>() }
    LaunchedEffect(capsules, locationGranted, resumes) {
        val source = location.getOrNull()?.takeIf { it.supported && locationGranted } ?: return@LaunchedEffect
        val lockedPlaces = capsules.filter { it.condition is UnlockCondition.AtPlace && it.arrivedAt == null }
        if (lockedPlaces.isEmpty()) return@LaunchedEffect
        val here = attempt { source.current() }.onFailure { Log.w("EchoLocation", "장소 재판정 실패: ${it.describe()}", it) }.getOrNull()
        ensureActive()
        if (here != null) {
            var opened = 0
            lockedPlaces.forEach {
                val inside = (it.condition as UnlockCondition.AtPlace).contains(here.latitude, here.longitude)
                if (!inside) store.markLeft(it.fileName) else if (store.markArrived(it.fileName)) opened++
            }
            if (opened > 0) { refresh(); report("여기서 남긴 녹음이 열렸어요"); return@LaunchedEffect }
        }
        lockedPlaces.filter { watched.add(it.fileName) }.forEach { capsule ->
            val target = capsule.condition as UnlockCondition.AtPlace
            runCatching {
                source.watch(capsule.fileName, GeoPoint(target.latitude, target.longitude), target.radiusMeters,
                    onEnter = { name -> if (store.markArrived(name)) { refresh(); report("여기서 남긴 녹음이 열렸어요") } },
                    onExit = { name -> store.markLeft(name) },
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
    LaunchedEffect(toast) { if (toast != null) { delay(3500); toast = null } }

    val playingFile = engine.playing?.name
    Box(Modifier.fillMaxSize().background(Echo.colors.background)) {
        when (val current = flow) {
            is Flow.Recording -> RecordingScreen(engine, onFinish = {
                engine.stopRecording(onStopped = { partial ->
                    flow = Flow.Sealing(current.file, wavDurationMs(current.file))
                    if (partial) report("녹음이 중간에 끊겨 그 전까지만 남았어요")
                    if (locationGranted) locate()
                }, onFailed = { current.file.delete(); flow = Flow.Idle; report(it) })
            })
            is Flow.Sealing -> SealScreen(
                durationMs = current.durationMs, state = current.choice, engine = engine, previewFile = current.file, place = place,
                onRequestPlace = { locationPermission.launch(PleosLocation.PERMISSION) },
                onDiscard = { engine.stopPlayback(); current.file.delete(); flow = Flow.Idle; report("녹음을 버렸어요") },
                onSeal = { condition -> seal(current, condition) },
                onError = ::report,
                voice = { VoiceButton(::listen, enabled = speech.state == SpeechService.State.IDLE) },
            )
            Flow.Idle -> Shell(destination, onSelect = { engine.stopPlayback(); destination = it }) {
                when (destination) {
                    Destination.HOME -> HomeScreen(capsules, today, playingFile, progress, ::record, ::open, { destination = Destination.STORY }, fileOf = store::file,
                        voice = { VoiceButton(::listen, enabled = speech.state == SpeechService.State.IDLE) })
                    Destination.STORY -> StoryScreen(capsules, today, playingFile, progress, ::open,
                        onDelete = { names ->
                            if (playingFile in names) engine.stopPlayback()
                            val failed = store.delete(names); refresh()
                            report(if (failed.isEmpty()) "녹음 ${names.size}개를 삭제했어요" else "${failed.size}개는 삭제하지 못했어요. 다시 시도해 주세요.")
                        })
                }
            }
        }
        if (speech.state == SpeechService.State.LISTENING) {
            val examples = if (flow is Flow.Sealing) "“1년 뒤에 열어 줘” · “여기 다시 오면” · “그냥 보관해”"
            else "“녹음해 줘” · “들려줘” · “이야기 보여 줘”"
            VoicePanel(speech.partial, examples, onStop = speech::stopListening, modifier = Modifier.align(Alignment.BottomCenter))
        }
        toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter)) }
    }

    askOpen?.let { capsule ->
        SystemPopup("아직 잠긴 녹음이에요", "지금 열면 다시 잠글 수 없어요.", "지금 열기",
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
