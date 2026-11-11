package com.eco.ui.format

import com.eco.capsule.Capsule
import com.eco.capsule.UnlockCondition
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val korean = Locale.KOREAN

/** 녹음 중 타이머처럼 흘러가는 시간에만 쓴다. 길이 표기에는 [durationWords]. */
fun formatClock(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    return "%02d:%02d".format(total / 60, total % 60)
}

/** "00:05" 는 자정 5분으로 읽혀(2026-11-11 리뷰) 길이는 말로 쓴다. */
fun durationWords(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val minutes = total / 60
    val seconds = total % 60
    return when {
        minutes == 0L -> "${seconds}초"
        seconds == 0L -> "${minutes}분"
        else -> "${minutes}분 ${seconds}초"
    }
}

fun koreanDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy년 M월 d일", korean))
fun shortDate(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd", korean))
fun todayLine(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("M월 d일 EEEE", korean))
fun timeOfDay(at: LocalDateTime): String = at.format(DateTimeFormatter.ofPattern("a h:mm", korean))
fun weekday(date: LocalDate): String = date.format(DateTimeFormatter.ofPattern("EEEE", korean))

/** 받아쓴 문장의 첫 문장을 제목 길이로. 너무 길면 말줄임. */
fun headline(transcript: String, max: Int = 24): String {
    val first = transcript.split('.', '?', '!', '\n').firstOrNull { it.isNotBlank() }?.trim() ?: transcript.trim()
    return if (first.length <= max) first else first.take(max).trimEnd() + "…"
}

/** 목록·홈에서 한눈에 갈라 읽혀야 하는 상태. */
enum class CapsuleStatus { LOCKED_DATE, LOCKED_PLACE, NEW, HEARD, KEPT }

fun Capsule.status(today: LocalDate): CapsuleStatus = when {
    condition == UnlockCondition.Anytime -> CapsuleStatus.KEPT
    !isUnlocked(today) -> if (condition is UnlockCondition.AtPlace) CapsuleStatus.LOCKED_PLACE else CapsuleStatus.LOCKED_DATE
    openedAt == null -> CapsuleStatus.NEW
    else -> CapsuleStatus.HEARD
}

fun statusLabel(capsule: Capsule, today: LocalDate): String = when (capsule.status(today)) {
    // "까지 잠김" 은 그날까지 잠긴다고 읽혀 당일에 열리는 동작과 어긋난다.
    CapsuleStatus.LOCKED_DATE -> "${shortDate((capsule.condition as UnlockCondition.OnDate).date)} 열림 예정"
    CapsuleStatus.LOCKED_PLACE -> "돌아오면 열림"
    CapsuleStatus.NEW -> "새로 열림"
    CapsuleStatus.HEARD -> "들었음"
    CapsuleStatus.KEPT -> "보관"
}
