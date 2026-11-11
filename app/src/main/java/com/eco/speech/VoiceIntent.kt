package com.eco.speech

import java.time.LocalDate
import java.time.MonthDay

/** 잠그기 화면에서 말로 고른 열림 조건. */
sealed interface UnlockChoice {
    data class OnDate(val date: LocalDate) : UnlockChoice
    data object Place : UnlockChoice
    data object Keep : UnlockChoice
}

/**
 * 인식된 문장 하나를 앱 동작으로 해석한 결과. Pleos STT 는 텍스트만 돌려주고 명령을 해석하지 않는다
 * (SpeechToText 소개 문서). 실행은 화면이 맡는다.
 */
sealed interface VoiceIntent {
    data object Record : VoiceIntent
    data object Play : VoiceIntent
    data object Discard : VoiceIntent
    data object Confirm : VoiceIntent
    data object ShowStory : VoiceIntent
    /** [confirm] 이면 "1년 뒤에 열어 줘" 처럼 확정까지 말한 것. 아니면 고르기만 한다. */
    data class Unlock(val choice: UnlockChoice, val confirm: Boolean) : VoiceIntent
    data object Unknown : VoiceIntent
}

private val placeWords = listOf("여기", "이곳", "이자리", "다시오면", "돌아오면", "다시올때", "장소")
private val keepWords = listOf("그냥보관", "보관만", "보관해", "조건없이", "언제든", "아무때나")
// Pleos STT 는 문장 끝 "줘" 를 자주 떨어뜨린다("일 년 뒤에 열어 줘" → "일년 뒤에 열어", 2026-11-11 실측). 어간으로 찾는다.
private val confirmWords = listOf("잠가", "잠궈", "잠그", "저장", "봉인", "열어", "들려", "보관해")
private val recordWords = listOf("녹음해", "녹음시작", "녹음할게", "녹음하기", "기억남겨", "남겨줘", "남길게")
private val playWords = listOf("들려", "들어볼래", "들어보기", "재생")
private val discardWords = listOf("버려", "버릴래", "지워", "삭제", "취소")
private val yesWords = listOf("잠가줘", "잠궈줘", "잠그기", "저장해", "좋아", "그래", "확인", "응")
private val storyWords = listOf("이야기", "목록")

fun parseVoiceIntent(text: String, today: LocalDate): VoiceIntent {
    val t = text.filter { it.isLetterOrDigit() }.lowercase()
    if (t.isEmpty()) return VoiceIntent.Unknown
    val choice = parseDate(t, today)?.let { UnlockChoice.OnDate(it) }
        ?: if (placeWords.any(t::contains)) UnlockChoice.Place
        else if (keepWords.any(t::contains)) UnlockChoice.Keep
        else null
    if (choice != null) return VoiceIntent.Unlock(choice, confirm = confirmWords.any(t::contains))
    return when {
        discardWords.any(t::contains) -> VoiceIntent.Discard
        recordWords.any(t::contains) -> VoiceIntent.Record
        playWords.any(t::contains) -> VoiceIntent.Play
        yesWords.any(t::contains) -> VoiceIntent.Confirm
        storyWords.any(t::contains) -> VoiceIntent.ShowStory
        else -> VoiceIntent.Unknown
    }
}

// ---- 날짜 ----

private const val NUM = "(\\d+|한|두|세|네|다섯|여섯|일곱|여덟|아홉|열|스무|[일이삼사오육칠팔구십]+)"
private val relative = Regex("${NUM}(년|해|개월|달|주|일)(뒤|후|지나|있다|만에|에|$)")
private val fullDate = Regex("(\\d{4})년${NUM}월${NUM}일")
private val monthDay = Regex("${NUM}월${NUM}일")

private val holidays = mapOf(
    "크리스마스" to MonthDay.of(12, 25), "성탄절" to MonthDay.of(12, 25),
    "어린이날" to MonthDay.of(5, 5), "새해" to MonthDay.of(1, 1), "신정" to MonthDay.of(1, 1),
)

/** 공백·문장부호를 지운 문장에서 미래 날짜를 찾는다. 오늘이거나 지난 날은 돌려주지 않는다. */
fun parseDate(t: String, today: LocalDate): LocalDate? {
    val yearShift = when {
        t.contains("내후년") -> 2L
        t.contains("내년") -> 1L
        else -> 0L
    }
    fullDate.find(t)?.let { m ->
        val (y, mo, d) = Triple(m.groupValues[1].toInt(), number(m.groupValues[2]), number(m.groupValues[3]))
        return dateOrNull(y, mo, d)?.takeIf { it.isAfter(today) }
    }
    monthDay.find(t)?.let { m ->
        val mo = number(m.groupValues[1]); val d = number(m.groupValues[2])
        return nextOccurrence(mo, d, today, yearShift)
    }
    holidays.entries.firstOrNull { t.contains(it.key) }?.let { (_, md) ->
        return nextOccurrence(md.monthValue, md.dayOfMonth, today, yearShift)
    }
    relative.find(t)?.let { m ->
        val n = number(m.groupValues[1]).takeIf { it in 1..400 } ?: return null
        val date = when (m.groupValues[2]) {
            "년", "해" -> today.plusYears(n.toLong())
            "개월", "달" -> today.plusMonths(n.toLong())
            "주" -> today.plusWeeks(n.toLong())
            else -> today.plusDays(n.toLong())
        }
        return date.takeIf { !it.isAfter(today.plusYears(30)) }
    }
    return when {
        t.contains("내후년") -> today.plusYears(2)
        t.contains("내년") -> today.plusYears(1)
        t.contains("다음달") -> today.plusMonths(1)
        t.contains("다음주") || t.contains("일주일") -> today.plusWeeks(1)
        t.contains("모레") -> today.plusDays(2)
        t.contains("내일") -> today.plusDays(1)
        else -> null
    }
}

/** 올해 그날이 지났거나 오늘이면 내년. "내년" 이 붙으면 그만큼 더 민다. */
private fun nextOccurrence(month: Int, day: Int, today: LocalDate, yearShift: Long): LocalDate? {
    if (yearShift > 0) return dateOrNull(today.year + yearShift.toInt(), month, day)
    val thisYear = dateOrNull(today.year, month, day) ?: return null
    return if (thisYear.isAfter(today)) thisYear else dateOrNull(today.year + 1, month, day)
}

private fun dateOrNull(y: Int, m: Int, d: Int): LocalDate? = runCatching { LocalDate.of(y, m, d) }.getOrNull()

private val native = mapOf("한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9, "열" to 10, "스무" to 20)
private val sino = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)

/** 숫자, 고유어 수(한·두·세…), 한자어 수(일·이·삼…십) 를 정수로. */
internal fun number(word: String): Int {
    word.toIntOrNull()?.let { return it }
    native[word]?.let { return it }
    // 한자어: "십이" = 12, "이십" = 20, "이십오" = 25
    val ten = word.indexOf('십')
    if (ten < 0) return sino[word.singleOrNull() ?: return -1] ?: -1
    val tens = if (ten == 0) 1 else sino[word[ten - 1]] ?: return -1
    val ones = if (ten == word.lastIndex) 0 else sino[word[ten + 1]] ?: return -1
    return tens * 10 + ones
}
