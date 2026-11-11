package com.eco.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.eco.capsule.Capsule
import com.eco.capsule.UnlockCondition
import com.eco.ui.components.CapsuleRow
import com.eco.ui.components.Gutter
import com.eco.ui.components.HLine
import com.eco.ui.components.ScreenHeader
import com.eco.ui.components.Tile
import com.eco.ui.components.TouchMin
import com.eco.ui.components.VLine
import com.eco.ui.format.formatDuration
import com.eco.ui.format.koreanDate
import com.eco.ui.format.shortDate
import com.eco.ui.format.timeOfDay
import com.eco.ui.format.todayLine
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import java.time.LocalDate

@Composable
fun HomeScreen(
    capsules: List<Capsule>,
    today: LocalDate,
    playingFile: String?,
    progress: Float,
    onRecord: () -> Unit,
    onPlay: (Capsule) -> Unit,
    onShowStory: () -> Unit,
) {
    val waiting = capsules.filter { it.isWaiting(today) }
    val next = capsules.filter { !it.isUnlocked(today) && it.condition is UnlockCondition.OnDate }
        .minByOrNull { (it.condition as UnlockCondition.OnDate).date }
    val title = when {
        waiting.size == 1 -> "열린 기억이 하나 있어요"
        waiting.size > 1 -> "열린 기억이 ${waiting.size}개 있어요"
        capsules.isEmpty() -> "이 차에 첫 목소리를 남겨 보세요"
        else -> "오늘의 목소리를 남겨 보세요"
    }
    val c = Echo.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val wide = maxWidth >= 1000.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 주행 상태(CarUxRestrictions)는 아직 받지 않으므로 '정차 중' 같은 문구를 붙이지 않는다.
            ScreenHeader(title, context = todayLine(today))
            HLine()
            val first = waiting.firstOrNull()
            val left: @Composable (Modifier) -> Unit = { m ->
                when {
                    first != null -> Tile(
                        icon = if (playingFile == first.fileName) Icons.Outlined.Stop else Icons.Outlined.PlayArrow,
                        title = waitingTitle(first, today),
                        status = "${koreanDate(first.createdAt.toLocalDate())} · ${formatDuration(first.durationMs)}",
                        onClick = { onPlay(first) }, modifier = m, marker = c.accent,
                    )
                    next != null -> Tile(Icons.Outlined.HourglassEmpty, "다음 기억은 ${shortDate((next.condition as UnlockCondition.OnDate).date)}에 열려요",
                        "${koreanDate(next.createdAt.toLocalDate())}에 남긴 기억", onShowStory, m)
                    else -> Tile(Icons.Outlined.HourglassEmpty, "남긴 목소리는 정한 날, 정한 곳에서 다시 열려요",
                        "날짜 · 장소 · 조건 없이 중에서 고를 수 있어요", onRecord, m)
                }
            }
            val right: @Composable (Modifier) -> Unit = { m ->
                Tile(Icons.Outlined.Mic, "기억 남기기", "버튼을 누르면 녹음을 시작해요", onRecord, m, inverted = true)
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().height(340.dp)) {
                    left(Modifier.weight(1f).fillMaxHeight()); VLine(); right(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                left(Modifier.fillMaxWidth().height(300.dp)); HLine(); right(Modifier.fillMaxWidth().height(300.dp))
            }
            HLine()
            if (capsules.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onShowStory).padding(start = Gutter, end = 16.dp).height(TouchMin),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("최근 남긴 기억", style = EchoType.title, color = c.text, modifier = Modifier.weight(1f))
                    Text("전체 보기", style = EchoType.caption, color = c.textSecondary)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
                }
                HLine()
                capsules.take(4).forEach { capsule ->
                    CapsuleRow(capsule, today, playingFile == capsule.fileName, progress, onClick = { onPlay(capsule) })
                    HLine()
                }
            }
            Box(Modifier.height(32.dp))
        }
    }
}

private fun waitingTitle(capsule: Capsule, today: LocalDate): String = when (val c = capsule.condition) {
    is UnlockCondition.OnDate -> {
        val years = capsule.createdAt.toLocalDate().until(today).years
        if (years >= 1 && c.date == today) "${years}년 전 오늘 남긴 기억" else "${timeOfDay(capsule.createdAt)}에 남긴 기억이 열렸어요"
    }
    is UnlockCondition.AtPlace -> "이곳에서 남긴 기억이 열렸어요"
    UnlockCondition.Anytime -> "남긴 기억"
}
