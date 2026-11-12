package com.eco.ui.story

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.eco.capsule.Capsule
import com.eco.ui.components.CapsuleRow
import com.eco.ui.components.Gutter
import com.eco.ui.components.HLine
import com.eco.ui.components.ScreenHeader
import com.eco.ui.components.Segmented
import com.eco.ui.components.SmallButton
import com.eco.ui.components.SystemPopup
import com.eco.ui.format.CapsuleStatus
import com.eco.ui.format.koreanDate
import com.eco.ui.format.status
import com.eco.ui.theme.Echo
import com.eco.ui.theme.EchoType
import java.time.LocalDate

private enum class Filter(val label: String) { ALL("전체"), LOCKED("잠김"), OPEN("열림"), KEPT("보관") }

private fun Capsule.matches(filter: Filter, today: LocalDate): Boolean = when (filter) {
    Filter.ALL -> true
    Filter.LOCKED -> status(today) == CapsuleStatus.LOCKED_DATE || status(today) == CapsuleStatus.LOCKED_PLACE
    Filter.OPEN -> status(today) == CapsuleStatus.NEW || status(today) == CapsuleStatus.HEARD
    Filter.KEPT -> status(today) == CapsuleStatus.KEPT
}

/** 줄을 누르면 [onOpen] 으로 넘긴다. 잠긴 녹음을 열지 묻는 것은 부르는 쪽(EchoApp) 한 곳에서 한다. */
@Composable
fun StoryScreen(
    capsules: List<Capsule>,
    today: LocalDate,
    playingFile: String?,
    progress: Float,
    onOpen: (Capsule) -> Unit,
    onDelete: (Set<String>) -> Unit,
    voice: (@Composable RowScope.() -> Unit)? = null,
) {
    var filter by remember { mutableStateOf(Filter.ALL) }
    var selecting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val c = Echo.colors

    Column(Modifier.fillMaxSize().background(c.background)) {
        val since = capsules.minByOrNull { it.createdAt }?.let { "${koreanDate(it.createdAt.toLocalDate())}부터 녹음 ${capsules.size}개" }
        ScreenHeader("이 차의 이야기", context = since) {
            if (capsules.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (selecting) {
                    SmallButton("취소", { selecting = false; selected = emptySet() })
                    if (selected.isNotEmpty()) SmallButton("${selected.size}개 삭제", { confirmDelete = true }, icon = Icons.Outlined.Delete)
                } else SmallButton("선택", { selecting = true })
            }
            if (!selecting && voice != null) { Spacer(Modifier.width(12.dp)); voice() }
        }
        HLine()
        val counts = Filter.entries.associateWith { f -> capsules.count { it.matches(f, today) } }
        Segmented(Filter.entries.map { "${it.label} ${counts[it]}" }, filter.ordinal, { filter = Filter.entries[it]; selected = emptySet() },
            Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 24.dp))
        HLine()
        val shown = capsules.filter { it.matches(filter, today) }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    when (filter) {
                        Filter.LOCKED -> "잠긴 녹음 없음"
                        Filter.OPEN -> "열린 녹음 없음"
                        Filter.KEPT -> "보관한 녹음 없음"
                        Filter.ALL -> "아직 녹음 없음"
                    },
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
                            if (selecting) selected = if (capsule.fileName in selected) selected - capsule.fileName else selected + capsule.fileName
                            else onOpen(capsule)
                        },
                    )
                    HLine()
                }
            }
        }
    }

    if (confirmDelete) {
        SystemPopup("녹음 ${selected.size}개를 삭제할까요?", "삭제하면 되돌릴 수 없어요", "삭제",
            onConfirm = { confirmDelete = false; onDelete(selected); selected = emptySet(); selecting = false },
            onDismiss = { confirmDelete = false })
    }
}
