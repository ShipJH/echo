package com.eco.ui

import android.Manifest
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import com.eco.ui.components.HLine
import com.eco.ui.components.NavItem
import com.eco.ui.components.NavRail
import com.eco.ui.components.Segmented
import com.eco.ui.format.shortDate
import com.eco.ui.home.HomeScreen
import com.eco.ui.story.StoryScreen
import com.eco.ui.stttest.SttTestScreen
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate

private enum class Destination { HOME, STORY, STT }

private sealed interface Flow {
    data object Idle : Flow
    data class Recording(val file: File) : Flow
    data class Sealing(val file: File, val durationMs: Long) : Flow
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
    val today = LocalDate.now()
    val debuggable = remember { context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }

    // FusedLocation 생성 자체가 실패할 수 있다(서비스 없음). 그 이유를 화면에 그대로 보여 준다.
    val location = remember { runCatching { PleosLocation(context) } }
    var locationGranted by remember { mutableStateOf(location.getOrNull()?.hasPermission() == true) }
    var place by remember { mutableStateOf<PlaceState>(PlaceState.NeedsPermission) }
    DisposableEffect(Unit) { onDispose { engine.release(); location.getOrNull()?.release() } }

    fun refresh() { capsules = store.load() }
    fun report(message: String) { toast = message }

    fun locate() {
        val source = location.getOrElse { place = PlaceState.Unavailable("위치 서비스를 열지 못했어요: ${it.describe()}"); return }
        if (!source.supported) { place = PlaceState.Unavailable("이 차량은 위치 정보를 제공하지 않아요"); return }
        if (!locationGranted) { place = PlaceState.NeedsPermission; return }
        place = PlaceState.Locating
        scope.launch {
            place = runCatching { source.current() }.fold(
                { PlaceState.Ready(it) },
                { Log.w("EchoLocation", "현재 위치 실패", it); PlaceState.Unavailable("위치를 받지 못했어요(${it.describe()})") },
            )
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        locationGranted = granted
        if (granted) locate() else place = PlaceState.Unavailable("위치 권한이 없어 장소를 기억할 수 없어요")
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
            if (capsule.openedAt == null) { store.markOpened(capsule); refresh() }
        }, onFailed = ::report)
    }

    // 장소 캡슐: 앱을 켤 때 현재 위치로 한 번 판정하고, 떠 있는 동안은 지오펜스로 받는다.
    val watched = remember { mutableSetOf<String>() }
    LaunchedEffect(capsules, locationGranted) {
        val source = location.getOrNull()?.takeIf { it.supported && locationGranted } ?: return@LaunchedEffect
        val sealedPlaces = capsules.filter { it.condition is UnlockCondition.AtPlace && it.arrivedAt == null }
        if (sealedPlaces.isEmpty()) return@LaunchedEffect
        val here = runCatching { source.current() }.onFailure { Log.w("EchoLocation", "장소 재판정 실패", it) }.getOrNull()
        if (here != null) {
            var opened = 0
            sealedPlaces.forEach {
                val inside = (it.condition as UnlockCondition.AtPlace).contains(here.latitude, here.longitude)
                if (!inside) store.markLeft(it.fileName) else if (store.markArrived(it.fileName)) opened++
            }
            if (opened > 0) { refresh(); report("이곳에서 남긴 기억이 열렸어요"); return@LaunchedEffect }
        }
        sealedPlaces.filter { watched.add(it.fileName) }.forEach { capsule ->
            val target = capsule.condition as UnlockCondition.AtPlace
            runCatching {
                source.watch(capsule.fileName, GeoPoint(target.latitude, target.longitude), target.radiusMeters,
                    onEnter = { name -> if (store.markArrived(name)) { refresh(); report("이곳에서 남긴 기억이 열렸어요") } },
                    onExit = { name -> store.markLeft(name) },
                    onError = { report("장소 감시 오류: ${it.describe()}") })
            }.onFailure { watched.remove(capsule.fileName); report("장소 감시를 시작하지 못했어요: ${it.describe()}") }
        }
    }

    var progress by remember { mutableStateOf(0f) }
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
                engine.stopRecording(onStopped = {
                    flow = Flow.Sealing(current.file, wavDurationMs(current.file))
                    if (locationGranted) locate()
                }, onFailed = { current.file.delete(); flow = Flow.Idle; report(it) })
            })
            is Flow.Sealing -> SealScreen(
                durationMs = current.durationMs, engine = engine, previewFile = current.file, place = place,
                onRequestPlace = { locationPermission.launch(PleosLocation.PERMISSION) },
                onDiscard = { engine.stopPlayback(); current.file.delete(); flow = Flow.Idle; report("녹음을 지웠어요") },
                onSeal = { condition ->
                    runCatching { store.seal(current.file, condition, (place as? PlaceState.Ready)?.point) }
                        .onSuccess { sealed ->
                            refresh(); flow = Flow.Idle; destination = Destination.HOME
                            report(when (val c = sealed.condition) {
                                is UnlockCondition.OnDate -> "봉인했어요 · ${shortDate(c.date)}에 열려요"
                                is UnlockCondition.AtPlace -> "봉인했어요 · 이곳에 다시 오면 열려요"
                                UnlockCondition.Anytime -> "이 차의 이야기에 보관했어요"
                            })
                        }
                        .onFailure { report(it.message ?: "봉인하지 못했어요") }
                },
                onError = ::report,
            )
            Flow.Idle -> Shell(destination, debuggable, onSelect = { engine.stopPlayback(); destination = it }) {
                when (destination) {
                    Destination.HOME -> HomeScreen(capsules, today, playingFile, progress, ::record, ::play) { destination = Destination.STORY }
                    Destination.STORY -> StoryScreen(capsules, today, playingFile, progress, ::play,
                        onOpenEarly = { store.openEarly(it); refresh(); play(capsules.first { c -> c.fileName == it.fileName }) },
                        onDelete = { names ->
                            if (playingFile in names) engine.stopPlayback()
                            val failed = store.delete(names); refresh()
                            report(if (failed.isEmpty()) "${names.size}개를 지웠어요" else "${failed.size}개는 지우지 못했어요")
                        })
                    Destination.STT -> SttTestScreen()
                }
            }
        }
        toast?.let { Toast(it, Modifier.align(Alignment.BottomCenter)) }
    }
}

private val navItems = listOf(
    NavItem("기억 남기기", Icons.Outlined.Mic),
    NavItem("이 차의 이야기", Icons.Outlined.ViewAgenda),
    NavItem("STT 테스트", Icons.Outlined.RecordVoiceOver),
)

/** 넓은 창은 차량 설정 앱처럼 왼쪽 목록, 좁은 창(2분할)은 위쪽 세그먼트. */
@Composable
private fun Shell(destination: Destination, debuggable: Boolean, onSelect: (Destination) -> Unit, content: @Composable () -> Unit) {
    val items = if (debuggable) navItems else navItems.take(2)
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
