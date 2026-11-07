package com.eco.ui.capsule

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eco.ui.components.EchoHeader
import com.eco.ui.components.TopTabs
import com.eco.ui.theme.*
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class CapsuleDto(
    val fileName: String,
    val createdDate: LocalDate,
    val unlockDate: LocalDate,
    val opened: Boolean,
) {
    val sealed: Boolean get() = unlockDate.isAfter(LocalDate.now())
}

private fun loadCapsules(context: Context): List<CapsuleDto> {
    val prefs = context.getSharedPreferences("capsules", Context.MODE_PRIVATE)
    return context.filesDir.listFiles().orEmpty()
        .filter { it.name.startsWith("echo-") && it.extension == "wav" }
        .sortedByDescending { it.lastModified() }
        .map { file ->
            val created = Instant.ofEpochMilli(file.lastModified()).atZone(ZoneId.systemDefault()).toLocalDate()
            val unlock = prefs.getString(file.name, null)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: created
            CapsuleDto(file.name, created, unlock, prefs.getBoolean("opened:${file.name}", false))
        }
}

@Composable
fun CapsuleListScreen(onCreate: () -> Unit, onSttTest: () -> Unit) {
    val context = LocalContext.current
    var filter by remember { mutableIntStateOf(0) }
    var capsules by remember { mutableStateOf(loadCapsules(context)) }
    var pending by remember { mutableStateOf<CapsuleDto?>(null) }
    var playing by remember { mutableStateOf<String?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleting by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { player?.release(); player = null }
    }

    fun play(capsule: CapsuleDto, openEarly: Boolean) {
        if (playing == capsule.fileName) {
            player?.release()
            player = null
            playing = null
            return
        }
        player?.release()
        player = null
        playing = null
        var candidate: MediaPlayer? = null
        try {
            val media = MediaPlayer()
            candidate = media
            media.setDataSource(File(context.filesDir, capsule.fileName).absolutePath)
            media.prepare()
            media.setOnCompletionListener { completed ->
                completed.release()
                if (player === completed) { player = null; playing = null }
            }
            media.start()
            player = media
            playing = capsule.fileName
            val editor = context.getSharedPreferences("capsules", Context.MODE_PRIVATE).edit()
                .putBoolean("opened:${capsule.fileName}", true)
            if (openEarly) editor.putString(capsule.fileName, LocalDate.now().toString())
            editor.apply()
            capsules = loadCapsules(context)
            if (openEarly) filter = 1
        } catch (exception: Exception) {
            candidate?.release()
            error = "녹음 파일을 재생하지 못했어요."
        }
    }

    Column(Modifier.fillMaxSize().background(EchoBackground)) {
        EchoHeader()
        TopTabs(selected = 1, onSelect = { if (it == 0) onCreate() else if (it == 2) onSttTest() })
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).widthIn(min = 600.dp).padding(start = 24.dp, end = 24.dp, bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("봉인 중", "공개됨").forEachIndexed { index, label ->
                Text(label, color = if (filter == index) EchoText else EchoMuted, fontSize = 28.sp,
                    modifier = Modifier.clip(RoundedCornerShape(24.dp))
                        .background(if (filter == index) EchoSurface else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { filter = index; selected = emptySet(); deleting = false }.padding(horizontal = 16.dp, vertical = 10.dp))
            }
            Spacer(Modifier.weight(1f))
            if (deleting) {
                TextButton(onClick = { deleting = false; selected = emptySet() }) { Text("취소", fontSize = 24.sp, color = EchoMuted) }
                TextButton(onClick = { confirmDelete = true }, enabled = selected.isNotEmpty()) {
                    Text("삭제 (${selected.size})", fontSize = 24.sp, color = if (selected.isEmpty()) EchoMuted else EchoAccent)
                }
            } else {
                Canvas(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).clickable { deleting = true }
                    .semantics { contentDescription = "캡슐 삭제 선택" }.padding(14.dp)) {
                    val stroke = 2.5.dp.toPx()
                    drawLine(EchoMuted, Offset(size.width * .15f, size.height * .25f), Offset(size.width * .85f, size.height * .25f), stroke, StrokeCap.Round)
                    drawLine(EchoMuted, Offset(size.width * .35f, size.height * .1f), Offset(size.width * .65f, size.height * .1f), stroke, StrokeCap.Round)
                    drawLine(EchoMuted, Offset(size.width * .25f, size.height * .3f), Offset(size.width * .3f, size.height * .9f), stroke, StrokeCap.Round)
                    drawLine(EchoMuted, Offset(size.width * .75f, size.height * .3f), Offset(size.width * .7f, size.height * .9f), stroke, StrokeCap.Round)
                    drawLine(EchoMuted, Offset(size.width * .3f, size.height * .9f), Offset(size.width * .7f, size.height * .9f), stroke, StrokeCap.Round)
                }
            }
        }
        val shown = capsules.filter { it.sealed == (filter == 0) }
        if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(if (filter == 1) "현재 타임캡슐에 공개된 음성이 없습니다." else "타임캡슐에 저장한 음성이 없습니다.", color = EchoMuted, fontSize = 26.sp)
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(shown, key = { it.fileName }) { capsule ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp))
                        .background(EchoSurface).padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (deleting) Checkbox(checked = capsule.fileName in selected, onCheckedChange = { checked ->
                            selected = if (checked) selected + capsule.fileName else selected - capsule.fileName
                        })
                        Text(if (capsule.sealed) "◈" else "♪", color = EchoAccent, fontSize = 30.sp)
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text("${capsule.createdDate} 녹음", color = EchoText, fontSize = 30.sp)
                            Text("${capsule.unlockDate} ${if (capsule.sealed) "공개 예정" else "공개됨"} · ${if (capsule.opened) "열어봄" else "아직 열지 않음"}", color = EchoMuted, fontSize = 26.sp)
                        }
                        if (!deleting) Text(if (playing == capsule.fileName) "■" else "▶", color = EchoAccent, fontSize = 30.sp,
                            modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable {
                                if (capsule.sealed) pending = capsule else play(capsule, false)
                            }.padding(16.dp))
                    }
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, containerColor = EchoSurface,
            title = { Text("선택한 캡슐 ${selected.size}개를 삭제할까요?", color = EchoText, fontSize = 28.sp) },
            text = { Text("삭제한 음성은 복구할 수 없습니다.", color = EchoMuted, fontSize = 22.sp) },
            confirmButton = { TextButton(onClick = {
                confirmDelete = false
                if (playing?.let { it in selected } == true) { player?.release(); player = null; playing = null }
                val prefs = context.getSharedPreferences("capsules", Context.MODE_PRIVATE)
                val editor = prefs.edit()
                var failed = 0
                selected.forEach { name ->
                    val file = File(context.filesDir, name)
                    if (!file.exists() || file.delete()) {
                        editor.remove(name).remove("opened:$name")
                    } else failed++
                }
                editor.apply()
                capsules = loadCapsules(context)
                selected = emptySet()
                deleting = false
                if (failed > 0) error = "${failed}개 음성을 삭제하지 못했어요."
            }) { Text("삭제", fontSize = 22.sp) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("취소", fontSize = 22.sp) } })
    }
    pending?.let { capsule ->
        AlertDialog(onDismissRequest = { pending = null }, containerColor = EchoSurface,
            title = { Text("아직 봉인되어 있는 상태입니다.", color = EchoText, fontSize = 28.sp) },
            text = { Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("확인하면 공개 날짜가 오늘로 변경됩니다.", color = EchoText, fontSize = 22.sp)
                Text("타임캡슐은 봉인된 시간이 지난 뒤 열어보는 것이 더욱 의미 있습니다.", color = EchoAccent, fontSize = 27.sp, fontWeight = FontWeight.Bold, lineHeight = 36.sp)
                Text("재생하시겠습니까?", color = EchoText, fontSize = 24.sp)
            } },
            confirmButton = { TextButton(onClick = { pending = null; play(capsule, true) }) { Text("확인", fontSize = 22.sp) } },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("취소", fontSize = 22.sp) } })
    }
    error?.let { message ->
        AlertDialog(onDismissRequest = { error = null }, title = { Text(message) }, confirmButton = {
            TextButton(onClick = { error = null }) { Text("확인") }
        })
    }
}
