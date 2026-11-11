package com.eco.ui.format

import com.eco.capsule.Capsule
import com.eco.capsule.UnlockCondition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val korean = Locale.KOREAN

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

fun koreanDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", korean))
fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", korean))
fun todayLine(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", korean))
fun timeOfDay(at: LocalDateTime): String = at.format(DateTimeFormatter.ofPattern("a h:mm", korean))

/** 목록 오른쪽에 붙는 상태 문구. */
fun conditionLabel(capsule: Capsule, today: LocalDate): String {
    val open = capsule.isUnlocked(today)
    return when (val c = capsule.condition) {
        is UnlockCondition.OnDate -> if (open) "열림" else "${shortDate(c.date)} 열림"
        is UnlockCondition.AtPlace -> if (open) "그곳에서 열림" else "그곳에 가면 열림"
        UnlockCondition.Anytime -> "보관"
    }
}
