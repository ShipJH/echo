package com.eco.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.eco.ui.theme.EchoAccent
import com.eco.ui.theme.EchoBackground
import com.eco.ui.theme.EchoMuted
import com.eco.ui.theme.EchoSurface
import com.eco.ui.theme.EchoText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.io.File
import java.time.LocalDate
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.max
import kotlin.math.sqrt

private enum class RecordingStage { IDLE, RECORDING, REVIEW, REVIEW_PLAYING, SAVED, PLAYING }

@Composable
fun VoicePrompt(modifier: Modifier = Modifier, compact: Boolean = false, onRecordingChanged: () -> Unit = {}, unlockDate: LocalDate = LocalDate.now().plusMonths(6)) {
    val context = LocalContext.current.applicationContext
    val audioWorker = remember { Executors.newSingleThreadExecutor { task -> Thread(task, "EchoAudio") } }
    val audioDispatcher = remember { audioWorker.asCoroutineDispatcher() }
    val disposed = remember { AtomicBoolean(false) }
    var busy by remember { mutableStateOf(false) }
    fun audioAction(action: () -> Unit) {
        if (busy || disposed.get()) return
        busy = true
        audioWorker.execute {
            val started = SystemClock.elapsedRealtime()
            try {
                if (!disposed.get()) action()
            } catch (error: Exception) {
                Log.e("EchoRecording", "Audio operation failed", error)
            } finally {
                Log.i("EchoRecording", "Audio operation finished in ${SystemClock.elapsedRealtime() - started} ms")
                busy = false
            }
        }
    }
    var stage by remember { mutableStateOf(RecordingStage.IDLE) }
    var recorder by remember { mutableStateOf<PcmRecorder?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    var tempFile by remember { mutableStateOf<File?>(null) }
    var savedFile by remember { mutableStateOf<File?>(null) }
    var amplitude by remember { mutableFloatStateOf(0f) }
    var bars by remember { mutableStateOf(List(56) { 0.12f }) }
    var recordedWaveform by remember { mutableStateOf<List<Float>>(emptyList()) }
    var playbackPositionMs by remember { mutableIntStateOf(0) }
    var playbackDurationMs by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var recordingStartedAt by remember { mutableStateOf(0L) }
    var elapsedSeconds by remember { mutableStateOf(0) }

    fun beginRecording() {
        var candidate: PcmRecorder? = null
        var output: File? = null
        try {
            val outputFile = File.createTempFile("echo-recording-", ".wav", context.cacheDir)
            output = outputFile
            val newRecorder = PcmRecorder(outputFile)
            candidate = newRecorder
            newRecorder.start()
            tempFile = outputFile
            recorder = newRecorder
            recordingStartedAt = SystemClock.elapsedRealtime()
            elapsedSeconds = 0
            amplitude = 0f
            bars = List(56) { 0.12f }
            recordedWaveform = emptyList()
            message = null
            stage = RecordingStage.RECORDING
        } catch (error: Exception) {
            Log.e("EchoRecording", "Unable to start microphone recording", error)
            runCatching { candidate?.release() }
            output?.delete()
            tempFile = null
            recorder = null
            message = when (error) {
                is SecurityException -> "마이크 권한을 허용한 뒤 다시 눌러주세요."
                is RuntimeException -> if (error.message?.contains("start failed", ignoreCase = true) == true) {
                    "오디오 서비스가 녹음을 시작하지 못했어요. 잠시 후 다시 시도해 주세요."
                } else {
                    "녹음을 시작하지 못했어요. 에뮬레이터의 마이크 설정을 확인해 주세요."
                }
                else -> "녹음을 시작하지 못했어요. 에뮬레이터의 마이크 설정을 확인해 주세요."
            }
            stage = RecordingStage.IDLE
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) audioAction { beginRecording() } else message = "녹음하려면 마이크 권한이 필요해요."
    }

    fun stopPlayback() {
        runCatching { player?.stop() }
        player?.release()
        player = null
    }

    fun finishRecording() {
        try {
            recorder?.stop()
            stage = RecordingStage.REVIEW
            message = null
        } catch (error: RuntimeException) {
            tempFile?.delete()
            tempFile = null
            stage = RecordingStage.IDLE
            message = "녹음 입력이 중단되었거나 너무 짧아요. 다시 시도해 주세요."
        } finally {
            recorder?.release()
            recorder = null
        }
    }

    LaunchedEffect(stage) {
        while (stage == RecordingStage.RECORDING) {
            val raw = withContext(audioDispatcher) { runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0) }
            amplitude = (raw / 32767f).coerceIn(0f, 1f)
            elapsedSeconds = ((SystemClock.elapsedRealtime() - recordingStartedAt) / 1000L).toInt()
            val level = (0.04f + kotlin.math.sqrt(amplitude) * 1.15f).coerceIn(0.04f, 1f)
            bars = bars.drop(1) + level
            recordedWaveform = recordedWaveform + level
            delay(120)
        }
    }

    LaunchedEffect(stage, player) {
        if (stage == RecordingStage.REVIEW_PLAYING) {
            val activePlayer = player
            while (isActive && stage == RecordingStage.REVIEW_PLAYING && activePlayer != null) {
                playbackPositionMs = runCatching { activePlayer.currentPosition }.getOrDefault(playbackPositionMs)
                val source = recordedWaveform
                if (source.isNotEmpty()) {
                    val count = (playbackPositionMs / 120 + 1).coerceIn(0, source.size)
                    val visible = source.take(count).takeLast(56)
                    bars = List(56 - visible.size) { 0.12f } + visible
                }
                delay(40)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            disposed.set(true)
            audioWorker.execute {
                runCatching { recorder?.stop() }
                runCatching { recorder?.release() }
                runCatching { player?.release() }
                tempFile?.delete()
            }
            audioWorker.shutdown()
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        if (stage == RecordingStage.IDLE) {
            Box(
                modifier = Modifier.size(if (compact) 88.dp else 108.dp).clip(CircleShape)
                    .background(EchoAccent.copy(alpha = 0.22f))
                    .clickable(enabled = !busy) { audioAction {
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) beginRecording()
                        else {
                            android.os.Handler(android.os.Looper.getMainLooper()).post { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
                        }
                    } },
                contentAlignment = Alignment.Center,
            ) { MicrophoneGlyph(Modifier.size(if (compact) 38.dp else 48.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (busy) "오디오 처리 중…" else "\"에코야, 녹음 시작해줘\"",
                color = EchoText,
                fontSize = if (compact) 26.sp else 40.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(4.dp))
            Text("마이크 버튼을 눌러 녹음해 보세요", color = EchoMuted, fontSize = if (compact) 20.sp else 24.sp)
        }

        if (stage == RecordingStage.REVIEW || stage == RecordingStage.REVIEW_PLAYING) {
            Text(if (stage == RecordingStage.REVIEW_PLAYING) "녹음 들어보는 중" else "녹음이 완료됐어요", color = EchoText, fontSize = if (compact) 28.sp else 36.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Waveform(bars, Modifier.fillMaxWidth().height(if (compact) 38.dp else 54.dp).padding(horizontal = 28.dp))
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ActionButton(if (stage == RecordingStage.REVIEW_PLAYING) "듣기 멈춤" else "들어보기", false, !busy) { audioAction {
                    if (stage == RecordingStage.REVIEW_PLAYING) {
                        stopPlayback()
                        stage = RecordingStage.REVIEW
                    } else {
                        val file = tempFile
                        if (file != null && file.exists()) {
                            try {
                                val mediaPlayer = MediaPlayer().apply {
                                    setDataSource(file.absolutePath)
                                    setOnCompletionListener {
                                        audioWorker.execute { runCatching { it.release() } }
                                        player = null
                                        stage = RecordingStage.REVIEW
                                    }
                                    prepare()
                                    start()
                                }
                                player = mediaPlayer
                                playbackDurationMs = mediaPlayer.duration
                                playbackPositionMs = 0
                                bars = List(56) { 0.12f }
                                stage = RecordingStage.REVIEW_PLAYING
                            } catch (error: Exception) {
                                message = "녹음 파일을 재생하지 못했어요."
                            }
                        }
                    }
                }
                }
                ActionButton("저장", true, !busy) { audioAction {
                    val source = tempFile
                    if (stage == RecordingStage.REVIEW && source != null && source.exists()) {
                        val destination = File(context.filesDir, "echo-${System.currentTimeMillis()}.wav")
                        if (source.renameTo(destination)) {
                            context.getSharedPreferences("capsules", android.content.Context.MODE_PRIVATE).edit().putString(destination.name, unlockDate.toString()).apply()
                            savedFile = destination
                            tempFile = null
                            stage = RecordingStage.SAVED
                            message = "앱 내부 저장소에 저장했어요."
                        } else message = "저장하지 못했어요. 다시 시도해 주세요."
                    }
                }
                }
                ActionButton("취소", false, !busy) { audioAction {
                    if (stage == RecordingStage.REVIEW_PLAYING) stopPlayback()
                    tempFile?.delete()
                    tempFile = null
                    stage = RecordingStage.IDLE
                    message = "녹음 파일을 삭제했어요."
                } }
            }
        }

        if (stage == RecordingStage.SAVED || stage == RecordingStage.PLAYING) {
            Text("녹음 파일이 저장됐어요", color = EchoText, fontSize = if (compact) 28.sp else 36.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(14.dp))
            ActionButton(if (stage == RecordingStage.PLAYING) "재생 중 · 멈추기" else "녹음 들어보기", true, !busy) { audioAction {
                if (stage == RecordingStage.PLAYING) {
                    stopPlayback()
                    stage = RecordingStage.SAVED
                } else {
                    try {
                        val mediaPlayer = MediaPlayer().apply {
                            setDataSource(savedFile?.absolutePath)
                            setOnCompletionListener {
                                audioWorker.execute { runCatching { it.release() } }
                                player = null
                                stage = RecordingStage.SAVED
                            }
                            prepare()
                            start()
                        }
                        player = mediaPlayer
                        stage = RecordingStage.PLAYING
                    } catch (error: Exception) {
                        message = "녹음 파일을 재생하지 못했어요."
                    }
                }
            }
        }
        }
        message?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = EchoMuted, fontSize = if (compact) 16.sp else 20.sp)
        }
    }

    if (stage == RecordingStage.RECORDING || stage == RecordingStage.REVIEW || stage == RecordingStage.REVIEW_PLAYING) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color(0xCC071018)),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(0.90f).clip(RoundedCornerShape(32.dp))
                    .background(Brush.verticalGradient(listOf(EchoSurface, EchoBackground)))
                    .padding(horizontal = 36.dp, vertical = 34.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(if (compact) 12.dp else 20.dp),
            ) {
                if (stage == RecordingStage.RECORDING) {
                    Text("소중한 순간을 담고 있어요", color = EchoMuted, fontSize = 24.sp)
                    Text("${formatDuration(elapsedSeconds)}", color = EchoText, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                    Waveform(bars, Modifier.fillMaxWidth().height(if (compact) 120.dp else 170.dp).padding(horizontal = 12.dp))
                    Box(
                        modifier = Modifier.size(if (compact) 112.dp else 136.dp).clip(CircleShape)
                            .background(EchoAccent.copy(alpha = 0.22f))
                            .clickable(enabled = !busy) { audioAction { finishRecording() } },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(Modifier.size(if (compact) 88.dp else 108.dp).clip(CircleShape).background(EchoAccent.copy(alpha = 0.30f)), contentAlignment = Alignment.Center) {
                            MicrophoneGlyph(Modifier.size(if (compact) 52.dp else 64.dp))
                        }
                    }
                    Text("마이크를 누르면 녹음이 끝나요", color = EchoText, fontSize = 23.sp)
                } else {
                    Text("녹음이 완료되었어요.", color = EchoText, fontSize = if (compact) 30.sp else 38.sp, fontWeight = FontWeight.Bold, maxLines = 1, softWrap = false)
                    Waveform(bars, Modifier.fillMaxWidth().height(if (compact) 120.dp else 170.dp).padding(horizontal = 12.dp))
                    Text("음성 명령 준비 중 · 아래 버튼으로 선택해 주세요", color = EchoMuted, fontSize = if (compact) 18.sp else 22.sp, maxLines = 1, textAlign = TextAlign.Center)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VoiceCommand("“에코야, 녹음 저장해줘”", compact)
                        ActionButton("저장", false, !busy, Modifier.fillMaxWidth()) { audioAction {
                            if (stage == RecordingStage.REVIEW_PLAYING) {
                                stopPlayback()
                                stage = RecordingStage.REVIEW
                            }
                            val source = tempFile
                            if (stage == RecordingStage.REVIEW && source != null && source.exists()) {
                                val destination = File(context.filesDir, "echo-${System.currentTimeMillis()}.wav")
                                if (source.renameTo(destination)) {
                                    context.getSharedPreferences("capsules", android.content.Context.MODE_PRIVATE).edit().putString(destination.name, unlockDate.toString()).apply()
                                    savedFile = destination
                                    tempFile = null
                                    stage = RecordingStage.SAVED
                                    message = "앱 내부 저장소에 저장했어요."
                                } else message = "저장하지 못했어요. 다시 시도해 주세요."
                            }
                        } }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VoiceCommand("“에코야, 녹음 들려줘”", compact)
                        ActionButton(if (stage == RecordingStage.REVIEW_PLAYING) "멈춤" else "들어보기", false, !busy, Modifier.fillMaxWidth()) { audioAction {
                            if (stage == RecordingStage.REVIEW_PLAYING) {
                                stopPlayback()
                                stage = RecordingStage.REVIEW
                            } else {
                                val file = tempFile
                                if (file != null && file.exists()) {
                                    try {
                                        val mediaPlayer = MediaPlayer().apply {
                                            setDataSource(file.absolutePath)
                                            setOnCompletionListener {
                                                audioWorker.execute { runCatching { it.release() } }
                                                player = null
                                                stage = RecordingStage.REVIEW
                                            }
                                            prepare()
                                            start()
                                        }
                                        player = mediaPlayer
                                        playbackDurationMs = mediaPlayer.duration
                                        playbackPositionMs = 0
                                        bars = List(56) { 0.12f }
                                        stage = RecordingStage.REVIEW_PLAYING
                                    } catch (error: Exception) {
                                        message = "녹음 파일을 재생하지 못했어요."
                                    }
                                }
                            }
                        } }
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        VoiceCommand("“에코야, 녹음 취소해줘”", compact)
                        ActionButton("취소", false, !busy, Modifier.fillMaxWidth()) { audioAction {
                            if (stage == RecordingStage.REVIEW_PLAYING) stopPlayback()
                            tempFile?.delete()
                            tempFile = null
                            stage = RecordingStage.IDLE
                            message = "녹음 파일을 삭제했어요."
                        } }
                        }
                    }
                }
            }
        }
    }
}

private fun formatDuration(value: Int): String = "00:${value.toString().padStart(2, '0')}"

@Composable
private fun VoiceCommand(text: String, compact: Boolean) {
    Text(text, modifier = Modifier.fillMaxWidth(), color = EchoText, fontSize = if (compact) 20.sp else 24.sp,
        textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

private fun extractWaveform(file: File, bucketCount: Int): List<Float> {
    if (!file.exists() || file.length() <= 44L) return emptyList()
    return runCatching {
        RandomAccessFile(file, "r").use { input ->
            input.seek(44)
            val sampleCount = ((file.length() - 44L) / 2L).toInt()
            val samplesPerBucket = (sampleCount / bucketCount).coerceAtLeast(1)
            val buffer = ByteBuffer.allocate(2).order(ByteOrder.LITTLE_ENDIAN)
            buildList {
                repeat(bucketCount) { bucket ->
                    val start = bucket * samplesPerBucket
                    if (start >= sampleCount) return@repeat
                    val end = minOf(sampleCount, start + samplesPerBucket)
                    var peak = 0
                    for (sample in start until end) {
                        buffer.clear()
                        input.readFully(buffer.array())
                        peak = max(peak, kotlin.math.abs(buffer.getShort(0).toInt()))
                    }
                    add((sqrt(peak / 32767f) * 1.18f).coerceIn(0.06f, 1f))
                }
            }
        }
    }.getOrDefault(emptyList())
}

@Composable
private fun ActionButton(label: String, primary: Boolean, enabled: Boolean = true, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier.height(64.dp).clip(RoundedCornerShape(22.dp))
            .background(if (primary) EchoAccent else EchoSurface)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (primary) EchoSurface else EchoText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
    }
}

@Composable
private fun Waveform(values: List<Float>, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val slot = size.width / values.size
        val stroke = (size.height * 0.012f).coerceIn(2.dp.toPx(), 4.dp.toPx())
        val pink = Color(0xFFFF38B8)
        val blue = Color(0xFF35CFFF)
        values.forEachIndexed { index, value ->
            val envelope = (0.08f + value * 0.92f).coerceIn(0.10f, 1f)
            val barHeight = size.height * envelope
            val x = slot * index + slot / 2f
            val top = androidx.compose.ui.geometry.Offset(x, (size.height - barHeight) / 2f)
            val bottom = androidx.compose.ui.geometry.Offset(x, (size.height + barHeight) / 2f)
            val progress = index.toFloat() / (values.size - 1).coerceAtLeast(1)
            val color = androidx.compose.ui.graphics.lerp(pink, blue, progress)
            drawLine(color.copy(alpha = 0.10f), top, bottom, stroke * 5f, cap = StrokeCap.Round)
            drawLine(color.copy(alpha = 0.32f), top, bottom, stroke * 2f, cap = StrokeCap.Round)
            drawLine(color, top, bottom, stroke, cap = StrokeCap.Round)
        }
    }
}

@Composable
private fun MicrophoneGlyph(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val stroke = size.minDimension * 0.085f
        val micWidth = size.width * 0.34f
        val micHeight = size.height * 0.50f
        val micLeft = (size.width - micWidth) / 2f
        val micTop = size.height * 0.10f
        drawRoundRect(
            color = EchoAccent,
            topLeft = androidx.compose.ui.geometry.Offset(micLeft, micTop),
            size = androidx.compose.ui.geometry.Size(micWidth, micHeight),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(micWidth / 2f),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke),
        )
        val centerX = size.width / 2f
        val arcTop = size.height * 0.31f
        val arcHeight = size.height * 0.44f
        drawArc(EchoAccent, 0f, 180f, false, androidx.compose.ui.geometry.Offset(centerX - size.width * 0.29f, arcTop), androidx.compose.ui.geometry.Size(size.width * 0.58f, arcHeight), style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(EchoAccent, androidx.compose.ui.geometry.Offset(centerX, arcTop + arcHeight * 0.77f), androidx.compose.ui.geometry.Offset(centerX, size.height * 0.88f), stroke, cap = StrokeCap.Round)
        drawLine(EchoAccent, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.90f), androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.90f), stroke, cap = StrokeCap.Round)
    }
}
