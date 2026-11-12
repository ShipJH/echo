package com.eco.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PlayArrow
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
import com.eco.ui.components.WaveformView
import com.eco.ui.components.rememberPeaks
import com.eco.ui.format.durationWords
import com.eco.ui.format.headline
import com.eco.ui.format.koreanDate
import com.eco.ui.format.shortDate
import com.eco.ui.format.timeOfDay
import com.eco.ui.format.todayLine
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import java.time.LocalDate

/**
 * 홈의 중심은 '지금 열린 녹음' 이다. 그래서 넓은 창에서 그 칸을 2/3, 녹음하기를 1/3 로 둔다.
 * 목록 줄을 누르면 [onOpen] 으로 넘기고, 잠긴 녹음인지는 부르는 쪽이 판단한다.
 */
@Composable
fun HomeScreen(
    capsules: List<Capsule>,
    today: LocalDate,
    playingFile: String?,
    progress: Float,
    onRecord: () -> Unit,
    onOpen: (Capsule) -> Unit,
    onShowStory: () -> Unit,
    fileOf: (Capsule) -> java.io.File,
    voice: @Composable RowScope.() -> Unit = {},
) {
    // 재생을 시작하면 '들었음' 이 바로 기록된다. 듣는 동안 칸이 다른 내용으로 바뀌지 않도록 재생 중인 녹음을 붙잡아 둔다.
    val playingCapsule = capsules.firstOrNull { it.fileName == playingFile && it.condition != UnlockCondition.Anytime && it.isUnlocked(today) }
    val waiting = listOfNotNull(playingCapsule) + capsules.filter { it.isWaiting(today) && it.fileName != playingFile }
    val next = capsules.filter { !it.isUnlocked(today) && it.condition is UnlockCondition.OnDate }
        .minByOrNull { (it.condition as UnlockCondition.OnDate).date }
    val title = when {
        playingCapsule != null -> "재생 중"
        waiting.isNotEmpty() -> "새로 열린 녹음 ${waiting.size}개"
        capsules.isEmpty() -> "아직 녹음 없음"
        else -> "새로 열린 녹음 없음"
    }
    val c = Echo.colors
    BoxWithConstraints(Modifier.fillMaxSize().background(c.background)) {
        val wide = maxWidth >= 1000.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            // 주행 상태(CarUxRestrictions)는 아직 받지 않으므로 '정차 중' 같은 문구를 붙이지 않는다.
            ScreenHeader(title, context = todayLine(today), trailing = voice)
            HLine()
            val first = waiting.firstOrNull()
            val memory: @Composable (Modifier) -> Unit = { m ->
                when {
                    first != null -> Tile(
                        icon = if (playingFile == first.fileName) Icons.Filled.Stop else Icons.Outlined.PlayArrow,
                        title = openedTitle(first, today),
                        status = first.visibleTranscript(today)?.let { "\u201C${headline(it, 40)}\u201D" }
                            ?: "${timeOfDay(first.createdAt)} · ${durationWords(first.durationMs)}",
                        onClick = { onOpen(first) }, modifier = m, marker = c.accent, action = true, actionFill = c.accent,
                        bottom = {
                            val isPlaying = playingFile == first.fileName
                            WaveformView(rememberPeaks(fileOf(first)), color = if (isPlaying) c.hint else c.accent, played = c.accent,
                                progress = if (isPlaying) progress else 0f, modifier = Modifier.fillMaxWidth().height(72.dp))
                        },
                    )
                    next != null -> Tile(Icons.Outlined.HourglassEmpty, "${koreanDate((next.condition as UnlockCondition.OnDate).date)}에 열려요",
                        "${koreanDate(next.createdAt.toLocalDate())}에 잠근 녹음", onShowStory, m)
                    // 설명 칸이다. 누르면 녹음이 시작되던 것은 놀랍다(2026-11-12 디자인 리뷰). 녹음은 옆 칸에서만.
                    else -> Tile(Icons.Outlined.HourglassEmpty, "잠근 녹음은 정한 날이나 그 자리에서 열려요",
                        "잠그지 않고 보관할 수도 있어요", null, m)
                }
            }
            val record: @Composable (Modifier) -> Unit = { m ->
                Tile(Icons.Outlined.Mic, "녹음하기", "누르면 바로 시작해요", onRecord, m, action = true)
            }
            if (wide) {
                Row(Modifier.fillMaxWidth().height(360.dp)) {
                    memory(Modifier.weight(2f).fillMaxHeight()); VLine(); record(Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                memory(Modifier.fillMaxWidth().height(300.dp)); HLine(); record(Modifier.fillMaxWidth().height(300.dp))
            }
            HLine()
            if (capsules.isNotEmpty()) {
                Row(
                    Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onShowStory).padding(start = Gutter, end = 16.dp).height(TouchMin),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("최근 녹음", style = EchoType.title, color = c.text, modifier = Modifier.weight(1f))
                    Text("전체 보기", style = EchoType.caption, color = c.textSecondary)
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = c.textSecondary, modifier = Modifier.padding(start = 4.dp))
                }
                HLine()
                capsules.take(4).forEach { capsule ->
                    CapsuleRow(capsule, today, playingFile == capsule.fileName, progress, onClick = { onOpen(capsule) })
                    HLine()
                }
            }
            Box(Modifier.height(32.dp))
        }
    }
}

/** 이 칸은 그 장소를 떠난 뒤에도 보이므로 '이곳' 같은 말을 쓰지 않는다. */
private fun openedTitle(capsule: Capsule, today: LocalDate): String {
    val years = capsule.createdAt.toLocalDate().until(today).years
    val anniversary = capsule.condition is UnlockCondition.OnDate && years >= 1 &&
        capsule.createdAt.monthValue == today.monthValue && capsule.createdAt.dayOfMonth == today.dayOfMonth
    // 머리가 이미 "새로 열린 녹음 N개" 라 여기서 '열렸어요' 를 되풀이하지 않는다. "오전 11:37 녹음이 열렸어요" 는 11:37 에 열린 것으로 읽혔다.
    val day = capsule.createdAt.toLocalDate()
    return when {
        anniversary -> "${years}년 전 오늘 녹음"
        day.year == today.year -> "${day.monthValue}월 ${day.dayOfMonth}일 녹음"
        else -> "${koreanDate(day)} 녹음"
    }
}
