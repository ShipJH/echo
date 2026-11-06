package com.eco.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eco.ui.theme.EchoAccent
import com.eco.ui.theme.EchoBackground
import com.eco.ui.theme.EchoBorder
import com.eco.ui.theme.EchoMuted
import com.eco.ui.theme.EchoSurface
import com.eco.ui.theme.EchoText
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private enum class PeriodUnit(val label: String) {
    DAYS("일"), MONTHS("개월"), YEARS("년")
}

@Composable
fun ScheduleCard(modifier: Modifier = Modifier, compact: Boolean = false) {
    var unit by remember { mutableStateOf(PeriodUnit.YEARS) }
    var sliderValue by remember { mutableFloatStateOf(3f) }
    val maxAmount = when (unit) {
        PeriodUnit.DAYS -> 365
        PeriodUnit.MONTHS -> 12
        PeriodUnit.YEARS -> 10
    }
    val amount = sliderValue.roundToInt().coerceIn(1, maxAmount)
    val today = remember { LocalDate.now() }
    val unlockDate = when (unit) {
        PeriodUnit.DAYS -> today.plusDays(amount.toLong())
        PeriodUnit.MONTHS -> today.plusMonths(amount.toLong())
        PeriodUnit.YEARS -> today.plusYears(amount.toLong())
    }

    Column(
        modifier = modifier.clip(RoundedCornerShape(18.dp)).background(EchoSurface)
            .padding(horizontal = 24.dp, vertical = if (compact) 16.dp else 24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "언제 다시 듣고 싶으세요?",
            modifier = Modifier.fillMaxWidth(),
            color = EchoText,
            fontSize = if (compact) 34.sp else 42.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(if (compact) 4.dp else 8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            PeriodUnit.entries.forEach { option ->
            UnitPill(option.label, selected = unit == option, compact = compact) {
                    unit = option
                    val optionMax = when (option) {
                        PeriodUnit.DAYS -> 365
                        PeriodUnit.MONTHS -> 12
                        PeriodUnit.YEARS -> 10
                    }
                    sliderValue = sliderValue.coerceIn(1f, optionMax.toFloat())
                }
            }
        }

        BoxWithConstraints(
            modifier = Modifier.fillMaxWidth().height(if (compact) 84.dp else 110.dp),
        ) {
            val centerSlotWidth = if (compact) 230.dp else 320.dp
            val sideSlotWidth = ((maxWidth - centerSlotWidth) / 2).coerceAtLeast(0.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier.width(sideSlotWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    CircleControl(label = "−", enabled = amount > 1, compact = compact) {
                        sliderValue = (amount - 1).coerceAtLeast(1).toFloat()
                    }
                }
                Box(
                    modifier = Modifier.width(centerSlotWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$amount${unit.label} 뒤",
                        color = EchoAccent,
                        fontSize = if (compact) 42.sp else 52.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                }
                Box(
                    modifier = Modifier.width(sideSlotWidth),
                    contentAlignment = Alignment.Center,
                ) {
                    CircleControl(label = "+", enabled = amount < maxAmount, compact = compact) {
                        sliderValue = (amount + 1).coerceAtMost(maxAmount).toFloat()
                    }
                }
            }
        }

        Text(
            "${unlockDate.format(DateTimeFormatter.ofPattern("yyyy.MM.dd"))} 공개",
            modifier = Modifier.fillMaxWidth(),
            color = EchoText,
            fontSize = if (compact) 30.sp else 40.sp,
            textAlign = TextAlign.Center,
        )

        CapsuleRangeSlider(
            value = sliderValue.coerceIn(1f, maxAmount.toFloat()),
            valueRange = 1f..maxAmount.toFloat(),
            onValueChange = { sliderValue = it },
            modifier = Modifier.fillMaxWidth().height(if (compact) 58.dp else 66.dp),
            compact = compact,
        )

        val quickPicks = when (unit) {
            PeriodUnit.DAYS -> listOf(1, 7, 14, 30)
            PeriodUnit.MONTHS -> listOf(1, 3, 6, 12)
            PeriodUnit.YEARS -> listOf(1, 3, 5, 10)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            quickPicks.forEach { value ->
                QuickPick("$value${unit.label}", selected = amount == value, compact = compact) {
                    sliderValue = value.toFloat()
                }
            }
        }
    }
}

@Composable
private fun CapsuleRangeSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean,
) {
    val density = LocalDensity.current
    val thumbRadiusPx = with(density) { (if (compact) 13.dp else 16.dp).toPx() }
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentValue by rememberUpdatedState(value)
    val currentRange by rememberUpdatedState(valueRange)

    Canvas(
        modifier = modifier
            .pointerInput(valueRange, thumbRadiusPx) {
                fun updateFromX(x: Float) {
                    val usableWidth = (size.width - thumbRadiusPx * 2).coerceAtLeast(1f)
                    val fraction = ((x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                    currentOnValueChange(currentRange.start + fraction * (currentRange.endInclusive - currentRange.start))
                }
                detectTapGestures { updateFromX(it.x) }
            }
            .pointerInput(valueRange, thumbRadiusPx) {
                fun updateFromX(x: Float) {
                    val usableWidth = (size.width - thumbRadiusPx * 2).coerceAtLeast(1f)
                    val fraction = ((x - thumbRadiusPx) / usableWidth).coerceIn(0f, 1f)
                    currentOnValueChange(currentRange.start + fraction * (currentRange.endInclusive - currentRange.start))
                }
                detectDragGestures(onDragStart = { updateFromX(it.x) }) { change, _ ->
                    updateFromX(change.position.x)
                    change.consume()
                }
            }
            .semantics {
                progressBarRangeInfo = ProgressBarRangeInfo(value, valueRange)
                setProgress { target ->
                    currentOnValueChange(target.coerceIn(currentRange.start, currentRange.endInclusive))
                    true
                }
            },
    ) {
        val centerY = size.height / 2f
        val startX = thumbRadiusPx
        val endX = size.width - thumbRadiusPx
        val fraction = ((currentValue - currentRange.start) / (currentRange.endInclusive - currentRange.start)).coerceIn(0f, 1f)
        val thumbX = startX + (endX - startX) * fraction
        val trackWidth = with(density) { (if (compact) 8.dp else 10.dp).toPx() }
        val thumbCenter = androidx.compose.ui.geometry.Offset(thumbX, centerY)

        drawLine(
            color = Color(0xFF2A3A45),
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = trackWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawLine(
            color = EchoAccent,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(thumbX, centerY),
            strokeWidth = trackWidth,
            cap = androidx.compose.ui.graphics.StrokeCap.Round,
        )
        drawCircle(color = EchoAccent, radius = thumbRadiusPx + with(density) { 2.dp.toPx() }, center = thumbCenter)
        drawCircle(color = Color.White, radius = thumbRadiusPx, center = thumbCenter)
    }
}

@Composable
private fun UnitPill(text: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.clip(RoundedCornerShape(24.dp))
            .background(if (selected) EchoBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 22.dp else 28.dp, vertical = if (compact) 14.dp else 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) EchoText else EchoMuted, fontSize = if (compact) 24.sp else 30.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CircleControl(label: String, enabled: Boolean, compact: Boolean, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier.size(if (compact) 72.dp else 92.dp).clip(CircleShape)
            .background(if (enabled) EchoBackground else EchoSurface)
            .border(if (compact) 2.dp else 3.dp, if (enabled) EchoAccent.copy(alpha = 0.78f) else EchoBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) EchoAccent else EchoMuted, fontSize = if (compact) 42.sp else 52.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun QuickPick(text: String, selected: Boolean, compact: Boolean, onClick: () -> Unit) {
    Text(
        text = text,
        color = if (selected) EchoText else EchoMuted,
        fontSize = if (compact) 18.sp else 22.sp,
        modifier = Modifier.padding(horizontal = 5.dp).clip(RoundedCornerShape(24.dp))
            .background(if (selected) EchoBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = if (compact) 11.dp else 14.dp),
    )
}
