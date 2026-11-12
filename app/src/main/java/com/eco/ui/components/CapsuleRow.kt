package com.eco.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.outlined.Archive
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.eco.capsule.Capsule
import com.eco.ui.format.CapsuleStatus
import com.eco.ui.format.durationWords
import com.eco.ui.format.headline
import com.eco.ui.format.status
import com.eco.ui.format.statusLabel
import com.eco.ui.format.timeOfDay
import com.eco.ui.format.weekday
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * 날짜 칸 · 시각과 길이 · 상태로 된 한 줄. 상태는 아이콘과 짧은 말로 고정해 한눈에 갈리게 하고,
 * 재생 버튼(▶)은 들을 수 있는 줄에만 둔다. 재생 중이면 아래에 진행 막대가 붙는다.
 */
@Composable
fun CapsuleRow(
    capsule: Capsule,
    today: LocalDate,
    playing: Boolean,
    progress: Float,
    onClick: () -> Unit,
    selecting: Boolean = false,
    checked: Boolean = false,
) {
    val c = Echo.colors
    val status = capsule.status(today)
    val locked = status == CapsuleStatus.LOCKED_DATE || status == CapsuleStatus.LOCKED_PLACE
    Column(Modifier.fillMaxWidth().background(if (playing || checked) c.tile else c.background).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = Gutter, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(Modifier.size(44.dp).border(2.dp, c.text).background(if (checked) c.inverse else c.background), contentAlignment = Alignment.Center) {
                    if (checked) Icon(Icons.Outlined.Check, null, tint = c.onInverse, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(24.dp))
            }
            val day = capsule.createdAt.toLocalDate()
            Column(Modifier.width(170.dp)) {
                Text(day.format(DateTimeFormatter.ofPattern("MM.dd")), style = EchoType.title, color = c.text)
                // 올해 녹음이면 연도 대신 요일. 모든 줄에 같은 연도가 반복되면 잡음이다.
                Text(if (day.year == today.year) weekday(day) else day.year.toString(), style = EchoType.caption, color = c.textSecondary)
            }
            // 잠긴 녹음은 받아쓴 내용이 있어도 보이지 않는다(visibleTranscript).
            val spoken = capsule.visibleTranscript(today)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(spoken?.let { "\u201C${headline(it)}\u201D" } ?: timeOfDay(capsule.createdAt), style = EchoType.body, color = c.text, maxLines = 1)
                Text(listOfNotNull(timeOfDay(capsule.createdAt).takeIf { spoken != null }, durationWords(capsule.durationMs)).joinToString(" \u00B7 "),
                    style = EchoType.caption, color = c.textSecondary, maxLines = 1)
            }
            StatusTag(status, statusLabel(capsule, today))
            if (!selecting) {
                Spacer(Modifier.width(24.dp))
                Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                    when {
                        playing -> Icon(Icons.Filled.Stop, "멈추기", tint = c.text, modifier = Modifier.size(44.dp))
                        !locked -> Icon(Icons.Outlined.PlayArrow, "듣기", tint = if (status == CapsuleStatus.NEW) c.accent else c.text, modifier = Modifier.size(44.dp))
                    }
                }
            }
        }
        if (playing) {
            capsule.visibleTranscript(today)?.let {
                Text(it, style = EchoType.body, color = c.text, maxLines = 3, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = Gutter + 170.dp, end = Gutter, bottom = 20.dp))
            }
            Box(Modifier.fillMaxWidth().height(6.dp).background(c.hairline)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).background(c.accent))
            }
        }
    }
}

@Composable
private fun StatusTag(status: CapsuleStatus, label: String) {
    val c = Echo.colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        when (status) {
            CapsuleStatus.LOCKED_DATE, CapsuleStatus.LOCKED_PLACE ->
                Icon(Icons.Outlined.Lock, "잠김", tint = c.textSecondary, modifier = Modifier.size(36.dp))
            CapsuleStatus.NEW -> Box(Modifier.size(16.dp).clip(CircleShape).background(c.accent))
            CapsuleStatus.KEPT -> Icon(Icons.Outlined.Archive, "보관", tint = c.textSecondary, modifier = Modifier.size(36.dp))
            else -> Unit
        }
        // 색은 지금 할 일이 있는 '새로 열림' 에만 쓴다. 날짜 잠김이 가장 진하고 장소 잠김 파랑이 링크처럼 보였다(2026-11-12 디자인 리뷰).
        val color = if (status == CapsuleStatus.NEW) c.accent else c.textSecondary
        Text(label, style = EchoType.body, color = color, maxLines = 1)
    }
}
