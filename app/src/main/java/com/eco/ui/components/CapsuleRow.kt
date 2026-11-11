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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lock
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
import com.eco.ui.format.conditionLabel
import com.eco.ui.format.formatDuration
import com.eco.ui.format.timeOfDay
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 날짜 칸 · 내용 · 상태로 된 한 줄. 재생 중이면 아래에 진행 막대가 붙는다. */
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
    val open = capsule.isUnlocked(today)
    val waiting = capsule.isWaiting(today)
    val statusColor = when {
        waiting -> c.accent
        !open && capsule.condition is UnlockCondition.AtPlace -> c.state
        else -> c.textSecondary
    }
    Column(Modifier.fillMaxWidth().background(if (playing) c.tile else c.background).clickable(role = Role.Button, onClick = onClick)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 120.dp).padding(horizontal = Gutter, vertical = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(Modifier.size(44.dp).border(2.dp, c.text).background(if (checked) c.inverse else c.background), contentAlignment = Alignment.Center) {
                    if (checked) Icon(Icons.Outlined.Check, null, tint = c.onInverse, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(24.dp))
            }
            Column(Modifier.width(170.dp)) {
                Text(capsule.createdAt.year.toString(), style = EchoType.caption, color = c.textSecondary)
                Text(capsule.createdAt.format(DateTimeFormatter.ofPattern("MM.dd")), style = EchoType.title, color = c.text)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("${timeOfDay(capsule.createdAt)}에 남긴 기억", style = EchoType.body, color = c.text, maxLines = 1)
                val meta = buildList {
                    add(formatDuration(capsule.durationMs))
                    if (capsule.recordedPlace != null) add("장소 기록됨")
                    if (capsule.openedAt != null) add("들었음")
                }.joinToString(" · ")
                Text(meta, style = EchoType.caption, color = c.textSecondary, maxLines = 1)
            }
            Text(conditionLabel(capsule, today), style = EchoType.body, color = statusColor, maxLines = 1)
            if (!selecting) {
                Spacer(Modifier.width(24.dp))
                Icon(
                    when { playing -> Icons.Outlined.Stop; open -> Icons.Outlined.PlayArrow; else -> Icons.Outlined.Lock },
                    contentDescription = if (playing) "멈추기" else if (open) "듣기" else "봉인됨",
                    tint = if (waiting) c.accent else c.text,
                    modifier = Modifier.size(44.dp),
                )
            }
        }
        if (playing) {
            Box(Modifier.fillMaxWidth().height(6.dp).background(c.hairline)) {
                Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(6.dp).background(c.accent))
            }
        }
    }
}
