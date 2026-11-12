package com.eco.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eco.capsule.GeoPoint
import com.eco.capsule.UnlockCondition
import com.eco.ui.components.BlockButton
import com.eco.ui.components.Gutter
import com.eco.ui.components.HLine
import com.eco.ui.components.ScreenHeader
import com.eco.ui.components.Segmented
import com.eco.ui.components.SystemPopup
import com.eco.ui.components.TouchMin
import com.eco.ui.components.VLine
import com.eco.ui.components.WaveformView
import com.eco.ui.components.rememberPeaks
import com.eco.ui.format.durationWords
import com.eco.ui.format.formatClock
import com.eco.ui.format.koreanDate
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import kotlinx.coroutines.delay
import java.io.File
import java.time.LocalDate

/**
 * 녹음 중 화면. 안내 한 줄과 버튼 하나만 둔다(주행 중 방해 최소화 가이드).
 * 막대는 실제 입력 크기를 보여 주는 피드백이라 장식 애니메이션을 넣지 않는다.
 */
@Composable
fun RecordingScreen(engine: AudioEngine, onFinish: () -> Unit) {
    var elapsed by remember { mutableIntStateOf(0) }
    var bars by remember { mutableStateOf(List(32) { 0f }) }
    LaunchedEffect(engine.recording) {
        while (engine.recording) {
            engine.sampleLevel()
            elapsed = (engine.elapsedMs / 1000).toInt()
            bars = bars.drop(1) + engine.level
            delay(120)
        }
    }
    val c = Echo.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(20.dp).clip(CircleShape).background(c.recording))
            Spacer(Modifier.width(16.dp))
            Text("녹음 중", style = EchoType.title, color = c.text)
        }
        HLine()
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(formatClock(elapsed * 1000L), style = EchoType.display, color = c.text)
            LevelBars(bars, Modifier.fillMaxWidth(0.6f).height(120.dp))
            Spacer(Modifier.height(24.dp))
            Text(
                if (engine.interrupted) "마이크가 끊겼어요. 끊기기 전까지 녹음됐어요" else "다 말했으면 녹음 끝내기를 눌러 주세요",
                style = EchoType.body, color = if (engine.interrupted) c.text else c.textSecondary, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            // 이 화면의 유일한 행동이라 주요 버튼으로 둔다. 전체 폭 반전 막대는 Night 에서 눈부셔 되돌린 적이 있어(2026-11-11)
            // 가운데 블록으로만 반전한다. Night 녹음 화면의 밝은 면은 0.5% 라 블록 하나는 여유가 있다(2026-11-12 디자인 리뷰 실측).
            BlockButton("녹음 끝내기", onFinish, Modifier.width(560.dp).heightIn(min = 120.dp), primary = true,
                icon = Icons.Filled.Stop, iconTint = c.recording, enabled = !engine.busy)
        }
    }
}

@Composable
private fun LevelBars(values: List<Float>, modifier: Modifier) {
    val active = Echo.colors.accent
    // 무음일 때도 막대가 보여야 녹음이 살아 있는지 안다. hint 는 비텍스트 대비 3:1 이상.
    val idle = Echo.colors.hint
    Canvas(modifier) {
        val slot = size.width / values.size
        val stroke = (slot * 0.5f).coerceAtMost(10.dp.toPx())
        values.forEachIndexed { i, v ->
            val h = size.height * (0.08f + kotlin.math.sqrt(v) * 0.92f).coerceIn(0.08f, 1f)
            val x = slot * i + slot / 2
            drawLine(if (v > 0.02f) active else idle, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), stroke, StrokeCap.Butt)
        }
    }
}

/** 잠그기 화면에서 고른 열림 조건. 장소는 위치를 받은 뒤에만 확정된다. */
enum class ConditionKind { DATE, PLACE, ANYTIME }

sealed interface PlaceState {
    data object NeedsPermission : PlaceState
    data object Locating : PlaceState
    data class Ready(val point: GeoPoint) : PlaceState
    /** [retryable] 이 false 면 다시 눌러도 소용없다(영구 거부, 미지원). */
    data class Unavailable(val reason: String, val retryable: Boolean = true) : PlaceState
}

private data class Preset(val label: String, val unit: DateUnit, val amount: Int)
enum class DateUnit(val label: String) { DAYS("일"), MONTHS("개월"), YEARS("년") }

private val presets = listOf(
    Preset("1개월", DateUnit.MONTHS, 1), Preset("6개월", DateUnit.MONTHS, 6), Preset("1년", DateUnit.YEARS, 1),
    Preset("3년", DateUnit.YEARS, 3), Preset("10년", DateUnit.YEARS, 10),
)
private val radii = listOf(100f to "100m", 300f to "300m", 1000f to "1km")

/**
 * 잠그기 화면의 선택. 화면 밖(EchoApp)에 두어 음성 명령도 같은 상태를 바꾼다.
 * [spokenDate] 는 "2030년 5월 5일" 처럼 프리셋으로 나타낼 수 없는 날을 말로 고른 경우다.
 */
class SealState(val today: LocalDate = LocalDate.now()) {
    var kind by mutableStateOf(ConditionKind.DATE)
    var preset by mutableIntStateOf(2) // 1년
    var custom by mutableStateOf(false)
    var unit by mutableStateOf(DateUnit.MONTHS)
    var amount by mutableIntStateOf(6)
    var radius by mutableIntStateOf(1)
    var spokenDate by mutableStateOf<LocalDate?>(null)
    var confirmDiscard by mutableStateOf(false)
    /** 이 화면에서 조건을 한 번이라도 골랐다(터치든 말이든). "응" 같은 짧은 대답은 그 뒤에만 잠그기로 친다. */
    var picked by mutableStateOf(false)

    val date: LocalDate get() = spokenDate ?: if (custom) unlockDate(today, unit, amount) else presets[preset].let { unlockDate(today, it.unit, it.amount) }

    /** 말로 고른 날짜. 프리셋과 같으면 그 칸을 고른 것으로 보인다. */
    fun chooseDate(date: LocalDate) {
        picked = true
        kind = ConditionKind.DATE
        val match = presets.indexOfFirst { unlockDate(today, it.unit, it.amount) == date }
        if (match >= 0) { preset = match; custom = false; spokenDate = null } else { spokenDate = date; custom = false }
    }

    fun condition(place: PlaceState): UnlockCondition? = when (kind) {
        ConditionKind.DATE -> UnlockCondition.OnDate(date)
        ConditionKind.PLACE -> (place as? PlaceState.Ready)?.point?.let { UnlockCondition.AtPlace(it.latitude, it.longitude, radii[radius].first) }
        ConditionKind.ANYTIME -> UnlockCondition.Anytime
    }
}

fun unlockDate(today: LocalDate, unit: DateUnit, amount: Int): LocalDate = when (unit) {
    DateUnit.DAYS -> today.plusDays(amount.toLong())
    DateUnit.MONTHS -> today.plusMonths(amount.toLong())
    DateUnit.YEARS -> today.plusYears(amount.toLong())
}

@Composable
fun SealScreen(
    durationMs: Long,
    state: SealState,
    engine: AudioEngine,
    previewFile: File,
    place: PlaceState,
    onRequestPlace: () -> Unit,
    onDiscard: () -> Unit,
    onSeal: (UnlockCondition) -> Unit,
    onError: (String) -> Unit,
    voice: @Composable RowScope.() -> Unit = {},
) {
    val s = state
    val c = Echo.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("언제 다시 들을까요?", context = "방금 녹음 · ${durationWords(durationMs)}", trailing = voice)
        HLine()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ConditionRow(Icons.Outlined.CalendarToday, "정한 날에", "${koreanDate(s.date)}에 열려요", s.kind == ConditionKind.DATE, { s.picked = true; s.kind = ConditionKind.DATE }) {
                val selected = when { s.spokenDate != null -> -1; s.custom -> presets.size; else -> s.preset }
                Segmented(presets.map { it.label } + "직접 설정", selected, {
                    s.picked = true
                    s.spokenDate = null
                    if (it == presets.size) s.custom = true else { s.custom = false; s.preset = it }
                }, Modifier.fillMaxWidth())
                if (s.spokenDate != null) Text("말로 정한 날 · ${koreanDate(s.spokenDate!!)}", style = EchoType.title, color = c.text)
                else if (s.custom) CustomDate(s.unit, s.amount, { s.picked = true; s.unit = it; s.amount = s.amount.coerceIn(1, amountLimit(it)) }, { s.picked = true; s.amount = it })
            }
            HLine()
            val placeStatus = when (place) {
                PlaceState.NeedsPermission -> "누르면 위치 권한을 요청해요"
                PlaceState.Locating -> "현재 위치 확인 중…"
                // 좌표는 운전자에게 의미가 없어 보여 주지 않는다. '떠났다가 돌아와야' 열리는 조건을 문장에 담는다.
                is PlaceState.Ready -> "여기를 떠났다가 ${radii[s.radius].second} 안으로 돌아오면 열려요"
                is PlaceState.Unavailable -> if (place.retryable) "${place.reason}. 누르면 다시 찾아요" else place.reason
            }
            ConditionRow(Icons.Outlined.Place, "여기 다시 오면", placeStatus, s.kind == ConditionKind.PLACE, {
                s.picked = true
                s.kind = ConditionKind.PLACE
                // 거부·실패 뒤에도 다시 누르면 요청한다. 이미 허용돼 있으면 시스템이 바로 허용으로 돌려주고 위치를 다시 받는다.
                if (place == PlaceState.NeedsPermission || (place is PlaceState.Unavailable && place.retryable)) onRequestPlace()
            }) {
                if (place is PlaceState.Ready) Segmented(radii.map { it.second }, s.radius, { s.picked = true; s.radius = it }, Modifier.widthIn(max = 600.dp).fillMaxWidth())
            }
            HLine()
            ConditionRow(Icons.Outlined.Archive, "언제든", "잠그지 않고 이 차의 이야기에 보관해요", s.kind == ConditionKind.ANYTIME, { s.picked = true; s.kind = ConditionKind.ANYTIME })
            HLine()
            // 방금 녹음의 실제 파형과 들어보기를 한자리에 둔다. 들어보는 동안 재생된 부분이 틸색으로 찬다.
            val previewing = engine.playing == previewFile
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 28.dp), verticalAlignment = Alignment.CenterVertically) {
                BlockButton(if (previewing) "멈추기" else "들어보기", { engine.toggle(previewFile, onFailed = onError) }, Modifier.width(300.dp),
                    icon = if (previewing) Icons.Filled.Stop else Icons.Outlined.PlayArrow, enabled = !engine.busy)
                Spacer(Modifier.width(32.dp))
                WaveformView(rememberPeaks(previewFile, buckets = 96), color = c.hint, played = c.accent,
                    progress = if (previewing && engine.durationMs > 0) engine.positionMs / engine.durationMs.toFloat() else 0f,
                    modifier = Modifier.weight(1f).height(120.dp))
            }
        }
        HLine()
        val previewing = engine.playing == previewFile
        val condition = s.condition(place)
        Row(Modifier.fillMaxWidth()) {
            BlockButton("버리기", { s.confirmDiscard = true }, Modifier.weight(1f), icon = Icons.Outlined.Delete, enabled = !engine.busy)
            BlockButton(if (s.kind == ConditionKind.ANYTIME) "보관하기" else "잠그기", {
                if (previewing) engine.stopPlayback()
                condition?.let(onSeal)
            }, Modifier.weight(2f), primary = true, icon = if (s.kind == ConditionKind.ANYTIME) Icons.Outlined.Archive else Icons.Outlined.Lock,
                enabled = condition != null && !engine.busy)
        }
    }

    if (s.confirmDiscard) {
        SystemPopup("녹음을 버릴까요?", "버리면 되돌릴 수 없어요", "버리기",
            onConfirm = { s.confirmDiscard = false; onDiscard() }, onDismiss = { s.confirmDiscard = false })
    }
}

/**
 * 조건 한 줄. 선택된 줄은 surface_high 바탕과 라디오 표시로 보이고, 하위 선택지는 그 안에 펼친다.
 * 줄 전체를 반전하던 방식은 안쪽 세그먼트와 극성이 뒤집혀 무엇을 골랐는지 읽기 어려웠다(2026-11-11 리뷰).
 */
@Composable
private fun ConditionRow(
    icon: ImageVector,
    title: String,
    status: String,
    selected: Boolean,
    onSelect: () -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val c = Echo.colors
    Column(
        Modifier.fillMaxWidth().background(if (selected) c.tile else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 120.dp).padding(horizontal = Gutter, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = c.text, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = EchoType.title, color = c.text)
                Text(status, style = EchoType.body, color = c.textSecondary)
            }
            Icon(if (selected) Icons.Filled.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked, null,
                tint = if (selected) c.text else c.hint, modifier = Modifier.size(44.dp))
        }
        if (selected && extra != null) Column(Modifier.padding(start = 68.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { extra() }
    }
}

private fun amountLimit(unit: DateUnit) = when (unit) { DateUnit.DAYS -> 365; DateUnit.MONTHS -> 24; DateUnit.YEARS -> 30 }

@Composable
private fun CustomDate(unit: DateUnit, amount: Int, onUnit: (DateUnit) -> Unit, onAmount: (Int) -> Unit) {
    // 날짜는 위 줄("…에 열려요")에 이미 있다. 여기서는 고른 양만 보인다.
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("${amount}${unit.label} 뒤", style = EchoType.title, color = Echo.colors.text, modifier = Modifier.weight(1f), maxLines = 1)
        Segmented(DateUnit.entries.map { it.label }, unit.ordinal, { onUnit(DateUnit.entries[it]) }, Modifier.width(330.dp))
        Stepper(Icons.Outlined.Remove, "줄이기", amount > 1) { onAmount((amount - 1).coerceAtLeast(1)) }
        Stepper(Icons.Outlined.Add, "늘리기", amount < amountLimit(unit)) { onAmount((amount + 1).coerceAtMost(amountLimit(unit))) }
    }
}

@Composable
private fun Stepper(icon: ImageVector, label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Echo.colors
    Box(
        Modifier.size(TouchMin).border(1.dp, c.controlLine).clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = if (enabled) c.text else c.hint, modifier = Modifier.size(44.dp)) }
}
