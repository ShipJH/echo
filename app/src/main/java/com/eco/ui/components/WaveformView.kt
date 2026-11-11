package com.eco.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.eco.capsule.wavePeaks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** 파일에서 진폭을 IO 스레드로 읽어 온다. 읽는 동안에는 빈 배열. */
@Composable
fun rememberPeaks(file: File, buckets: Int = 48): FloatArray {
    val peaks by produceState(FloatArray(0), file, buckets) {
        value = withContext(Dispatchers.IO) { wavePeaks(file, buckets) }
    }
    return peaks
}

/**
 * 녹음의 실제 진폭으로 그린 정지 파형. ECHO 의 표식이다(장식 애니메이션이 아니라 목소리 자체의 모양).
 * [progress] 앞부분은 [played] 색, 나머지는 [color].
 */
@Composable
fun WaveformView(peaks: FloatArray, color: Color, modifier: Modifier = Modifier, played: Color = color, progress: Float = 0f) {
    Canvas(modifier) {
        if (peaks.isEmpty()) return@Canvas
        val slot = size.width / peaks.size
        val stroke = (slot * 0.55f).coerceAtMost(8.dp.toPx())
        val cut = (progress.coerceIn(0f, 1f) * peaks.size).toInt()
        peaks.forEachIndexed { i, v ->
            val h = size.height * (0.06f + v * 0.94f)
            val x = slot * i + slot / 2
            drawLine(if (i < cut) played else color, Offset(x, (size.height - h) / 2), Offset(x, (size.height + h) / 2), stroke, StrokeCap.Butt)
        }
    }
}
