package com.eco.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eco.capsule.Capsule
import com.eco.ui.components.CapsuleRow
import com.eco.ui.components.Gutter
import com.eco.ui.components.HLine
import com.eco.ui.components.ScreenHeader
import com.eco.ui.components.Segmented
import com.eco.ui.components.Stat
import com.eco.ui.components.SystemPopup
import com.eco.ui.components.TouchMin
import com.eco.ui.components.VLine
import com.eco.ui.format.shortDate
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import java.time.LocalDate

@Composable
fun StoryScreen(
    capsules: List<Capsule>,
    today: LocalDate,
    playingFile: String?,
    progress: Float,
    onPlay: (Capsule) -> Unit,
    onOpenEarly: (Capsule) -> Unit,
    onDelete: (Set<String>) -> Unit,
) {
    var filter by remember { mutableIntStateOf(0) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Capsule?>(null) }
    val c = Echo.colors

    Column(Modifier.fillMaxSize().background(c.background)) {
        ScreenHeader("이 차의 이야기", context = "이 차에서 남긴 목소리") {
            if (capsules.isNotEmpty()) {
                val label = when { !selecting -> "선택"; selected.isEmpty() -> "취소"; else -> "삭제 ${selected.size}" }
                Text(label, style = EchoType.label, color = if (selecting && selected.isNotEmpty()) c.recording else c.text,
                    modifier = Modifier.heightIn(min = TouchMin).clickable(role = Role.Button) {
                        when { !selecting -> selecting = true; selected.isEmpty() -> selecting = false; else -> confirmDelete = true }
                    }.padding(horizontal = 24.dp, vertical = 26.dp))
            }
        }
        HLine()
        Row(Modifier.fillMaxWidth()) {
            Stat("남긴 기억", "${capsules.size}")
            VLine(Modifier.height(120.dp))
            Stat("열린 기억", "${capsules.count { it.isUnlocked(today) }}")
            VLine(Modifier.height(120.dp))
            Stat("봉인 중", "${capsules.count { !it.isUnlocked(today) }}")
            VLine(Modifier.height(120.dp))
            Stat("첫 기억", capsules.minByOrNull { it.createdAt }?.let { shortDate(it.createdAt.toLocalDate()) } ?: "—")
        }
        HLine()
        Segmented(listOf("전체", "봉인 중", "열림"), filter, { filter = it; selected = emptySet() },
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 24.dp))
        HLine()
        val shown = when (filter) {
            1 -> capsules.filter { !it.isUnlocked(today) }
            2 -> capsules.filter { it.isUnlocked(today) }
            else -> capsules
        }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    when (filter) { 1 -> "봉인 중인 기억이 없어요"; 2 -> "아직 열린 기억이 없어요"; else -> "기억 남기기에서 첫 목소리를 남겨 보세요" },
                    style = EchoType.body, color = c.textSecondary, textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.fileName }) { capsule ->
                    CapsuleRow(
                        capsule, today, playingFile == capsule.fileName, progress,
                        selecting = selecting, checked = capsule.fileName in selected,
                        onClick = {
                            when {
                                selecting -> selected = if (capsule.fileName in selected) selected - capsule.fileName else selected + capsule.fileName
                                capsule.isUnlocked(today) -> onPlay(capsule)
                                else -> pending = capsule
                            }
                        },
                    )
                    HLine()
                }
            }
        }
    }

    if (confirmDelete) {
        SystemPopup("선택한 기억 ${selected.size}개를 지울까요?", "지운 목소리는 되살릴 수 없어요.", "지우기",
            onConfirm = { confirmDelete = false; onDelete(selected); selected = emptySet(); selecting = false },
            onDismiss = { confirmDelete = false })
    }
    pending?.let { capsule ->
        SystemPopup("아직 봉인된 기억이에요", "정한 순간을 기다리지 않고 지금 들어요.", "지금 열기",
            onConfirm = { pending = null; onOpenEarly(capsule) }, onDismiss = { pending = null })
    }
}
