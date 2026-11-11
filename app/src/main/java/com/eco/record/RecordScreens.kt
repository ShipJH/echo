package com.eco.record

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
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
import com.eco.ui.components.VLine
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import com.eco.ui.format.formatDuration
import com.eco.ui.format.koreanDate
import com.eco.ui.format.shortDate
import kotlinx.coroutines.delay
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
            Text("기억 남기는 중", style = EchoType.title, color = c.text)
        }
        HLine()
        Column(Modifier.weight(1f).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Text(koreanDate(LocalDate.now()), style = EchoType.body, color = c.textSecondary)
            Text(formatDuration(elapsed * 1000L), style = EchoType.display, color = c.text)
            LevelBars(bars, Modifier.fillMaxWidth(0.6f).height(120.dp))
            Spacer(Modifier.height(24.dp))
            Text("말씀하시는 그대로 남깁니다. 끝나면 아래 버튼을 눌러 주세요.", style = EchoType.body, color = c.textSecondary, textAlign = TextAlign.Center)
        }
        BlockButton("여기까지", onFinish, Modifier.fillMaxWidth(), primary = true, icon = Icons.Outlined.Stop, enabled = !engine.busy)
    }
}

@Composable
private fun LevelBars(values: List<Float>, modifier: Modifier) {
    val active = Echo.colors.accent
    val idle = Echo.colors.hairline
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

/** 봉인 화면에서 고른 열림 조건. 장소는 위치를 받은 뒤에만 확정된다. */
enum class ConditionKind { DATE, PLACE, ANYTIME }

sealed interface PlaceState {
    data object NeedsPermission : PlaceState
    data object Locating : PlaceState
    data class Ready(val point: GeoPoint) : PlaceState
    data class Unavailable(val reason: String) : PlaceState
}

private data class Preset(val label: String, val unit: DateUnit, val amount: Int)
enum class DateUnit(val label: String) { DAYS("일"), MONTHS("개월"), YEARS("년") }

private val presets = listOf(
    Preset("1개월", DateUnit.MONTHS, 1), Preset("6개월", DateUnit.MONTHS, 6), Preset("1년", DateUnit.YEARS, 1),
    Preset("3년", DateUnit.YEARS, 3), Preset("10년", DateUnit.YEARS, 10),
)
private val radii = listOf(100f to "100m", 300f to "300m", 1000f to "1km")

fun unlockDate(today: LocalDate, unit: DateUnit, amount: Int): LocalDate = when (unit) {
    DateUnit.DAYS -> today.plusDays(amount.toLong())
    DateUnit.MONTHS -> today.plusMonths(amount.toLong())
    DateUnit.YEARS -> today.plusYears(amount.toLong())
}

@Composable
fun SealScreen(
    durationMs: Long,
    engine: AudioEngine,
    previewFile: java.io.File,
    place: PlaceState,
    onRequestPlace: () -> Unit,
    onDiscard: () -> Unit,
    onSeal: (UnlockCondition) -> Unit,
    onError: (String) -> Unit,
) {
    val today = remember { LocalDate.now() }
    var kind by remember { mutableStateOf(ConditionKind.DATE) }
    var preset by remember { mutableIntStateOf(2) } // 1년
    var custom by remember { mutableStateOf(false) }
    var unit by remember { mutableStateOf(DateUnit.MONTHS) }
    var amount by remember { mutableIntStateOf(6) }
    var radius by remember { mutableIntStateOf(1) }
    val date = if (custom) unlockDate(today, unit, amount) else presets[preset].let { unlockDate(today, it.unit, it.amount) }

    val c = Echo.colors
    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("언제 다시 들을까요?", context = "방금 남긴 기억 · ${formatDuration(durationMs)}")
        HLine()
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            ConditionRow(Icons.Outlined.CalendarToday, "날짜를 정해서", "${koreanDate(date)}에 열려요", kind == ConditionKind.DATE, { kind = ConditionKind.DATE }) { inverted ->
                Segmented(presets.map { it.label } + "직접", if (custom) presets.size else preset, {
                    if (it == presets.size) custom = true else { custom = false; preset = it }
                }, Modifier.fillMaxWidth(), inverted = inverted)
                if (custom) CustomDate(unit, amount, inverted, { unit = it; amount = amount.coerceIn(1, amountLimit(it)) }, { amount = it })
            }
            HLine()
            val placeStatus = when (place) {
                PlaceState.NeedsPermission -> "위치 권한을 허용하면 지금 이곳을 기억해요"
                PlaceState.Locating -> "현재 위치를 확인하고 있어요…"
                is PlaceState.Ready -> "지금 이곳 반경 %s 안에 다시 오면 열려요 · %.4f, %.4f".format(radii[radius].second, place.point.latitude, place.point.longitude)
                is PlaceState.Unavailable -> place.reason
            }
            ConditionRow(Icons.Outlined.Place, "여기 다시 오면", placeStatus, kind == ConditionKind.PLACE, {
                kind = ConditionKind.PLACE
                if (place == PlaceState.NeedsPermission) onRequestPlace()
            }) { inverted ->
                if (place is PlaceState.Ready) Segmented(radii.map { it.second }, radius, { radius = it }, Modifier.fillMaxWidth(0.6f), inverted = inverted)
            }
            HLine()
            ConditionRow(Icons.Outlined.Archive, "조건 없이 보관하기", "이 차의 이야기에서 언제든 들을 수 있어요", kind == ConditionKind.ANYTIME, { kind = ConditionKind.ANYTIME })
            HLine()
        }
        HLine()
        val previewing = engine.playing == previewFile
        val canSeal = kind != ConditionKind.PLACE || place is PlaceState.Ready
        Row(Modifier.fillMaxWidth()) {
            BlockButton("버리기", onDiscard, Modifier.weight(1f), icon = Icons.Outlined.Delete, enabled = !engine.busy)
            VLine(Modifier.height(104.dp))
            BlockButton(if (previewing) "멈추기" else "들어보기", { engine.toggle(previewFile, onFailed = onError) }, Modifier.weight(1f),
                icon = if (previewing) Icons.Outlined.Stop else Icons.Outlined.PlayArrow, enabled = !engine.busy)
            BlockButton("봉인하기", {
                if (previewing) engine.stopPlayback()
                onSeal(when (kind) {
                    ConditionKind.DATE -> UnlockCondition.OnDate(date)
                    ConditionKind.PLACE -> (place as PlaceState.Ready).point.let { UnlockCondition.AtPlace(it.latitude, it.longitude, radii[radius].first) }
                    ConditionKind.ANYTIME -> UnlockCondition.Anytime
                })
            }, Modifier.weight(2f), primary = true, icon = Icons.Outlined.Lock, enabled = canSeal && !engine.busy)
        }
    }
}

/** 선택된 조건은 반전 블록으로 펼치고, 하위 선택지는 그 안에 둔다. */
@Composable
private fun ConditionRow(
    icon: ImageVector,
    title: String,
    status: String,
    selected: Boolean,
    onSelect: () -> Unit,
    extra: (@Composable (inverted: Boolean) -> Unit)? = null,
) {
    val c = Echo.colors
    val ink = if (selected) c.onInverse else c.text
    Column(
        Modifier.fillMaxWidth().background(if (selected) c.inverse else Color.Transparent)
            .semantics { this.selected = selected }
            .clickable(role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = 120.dp).padding(horizontal = Gutter, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = ink, modifier = Modifier.size(44.dp))
            Spacer(Modifier.width(24.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = EchoType.title, color = ink)
                Text(status, style = EchoType.body, color = if (selected) ink.copy(alpha = 0.78f) else c.textSecondary)
            }
        }
        if (selected && extra != null) Box(Modifier.padding(start = 68.dp)) { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) { extra(true) } }
    }
}

private fun amountLimit(unit: DateUnit) = when (unit) { DateUnit.DAYS -> 365; DateUnit.MONTHS -> 24; DateUnit.YEARS -> 30 }

@Composable
private fun CustomDate(unit: DateUnit, amount: Int, inverted: Boolean, onUnit: (DateUnit) -> Unit, onAmount: (Int) -> Unit) {
    val ink = if (inverted) Echo.colors.onInverse else Echo.colors.text
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val unitWidth = minOf(maxWidth * 0.45f, 420.dp)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Segmented(DateUnit.entries.map { it.label }, unit.ordinal, { onUnit(DateUnit.entries[it]) }, Modifier.width(unitWidth), inverted = inverted)
            Segmented(listOf("−", "+"), -1, { onAmount((amount + if (it == 0) -1 else 1).coerceIn(1, amountLimit(unit))) }, Modifier.width(220.dp), inverted = inverted)
            Text("${amount}${unit.label} 뒤 · ${shortDate(unlockDate(LocalDate.now(), unit, amount))}", style = EchoType.title, color = ink)
        }
    }
}
