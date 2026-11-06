package com.eco.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eco.ui.theme.EchoAccent
import com.eco.ui.theme.EchoMuted
import com.eco.ui.theme.EchoText

@Composable
fun VoicePrompt(modifier: Modifier = Modifier, compact: Boolean = false) {
    var previewListening by remember { mutableStateOf(false) }
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier.offset(y = (-12).dp).size(if (compact) 88.dp else 108.dp).clip(CircleShape)
                .background(EchoAccent.copy(alpha = if (previewListening) 0.34f else 0.22f))
                .clickable { previewListening = !previewListening },
            contentAlignment = Alignment.Center,
        ) {
            MicrophoneGlyph(modifier = Modifier.size(if (compact) 38.dp else 48.dp))
        }
        Spacer(Modifier.height(if (compact) 5.dp else 8.dp))
        Text(
            if (previewListening) "말씀해 주세요" else "\"에코야, 녹음 시작해줘\"",
            color = EchoText,
            fontSize = if (compact) 30.sp else 40.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            if (previewListening) "말씀해 주세요" else "음성 호출 또는 화면 터치",
            color = EchoMuted,
            fontSize = if (compact) 26.sp else 40.sp,
        )
    }
}

@Composable
private fun MicrophoneGlyph(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
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
        drawArc(
            color = EchoAccent,
            startAngle = 0f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(centerX - size.width * 0.29f, arcTop),
            size = androidx.compose.ui.geometry.Size(size.width * 0.58f, arcHeight),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )
        drawLine(EchoAccent, androidx.compose.ui.geometry.Offset(centerX, arcTop + arcHeight * 0.77f), androidx.compose.ui.geometry.Offset(centerX, size.height * 0.88f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        drawLine(EchoAccent, androidx.compose.ui.geometry.Offset(size.width * 0.34f, size.height * 0.90f), androidx.compose.ui.geometry.Offset(size.width * 0.66f, size.height * 0.90f), stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
