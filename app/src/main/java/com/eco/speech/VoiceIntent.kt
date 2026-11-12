package com.eco.speech

import java.time.LocalDate
import java.time.MonthDay

/** 잠그기 화면에서 말로 고른 열림 조건. */
sealed interface UnlockChoice {
    data class OnDate(val date: LocalDate) : UnlockChoice
    data object Place : UnlockChoice
    data object Keep : UnlockChoice
}

/** 말로 하기를 누른 화면. 화면마다 알아듣는 명령이 다르다. */
enum class VoiceScreen { HOME, STORY, SEAL }

/**
 * 인식된 문장 하나를 앱 동작으로 해석한 결과. Pleos STT 는 텍스트만 돌려주고 명령을 해석하지 않는다
 * (SpeechToText 소개 문서). 실행은 화면이 맡는다.
 */
sealed interface VoiceIntent {
    data object Record : VoiceIntent
    data object Play : VoiceIntent
    data object Discard : VoiceIntent
    data object Confirm : VoiceIntent
    /** "응"·"네" 같은 짧은 대답. 화면을 보지 않은 운전자는 기본값(1년)을 모르니, 조건을 고른 뒤에만 잠그기로 친다. */
    data object Yes : VoiceIntent
    data object ShowStory : VoiceIntent
    data object ShowHome : VoiceIntent
    /** [confirm] 이면 "1년 뒤에 열어 줘" 처럼 확정까지 말한 것. 아니면 고르기만 한다. */
    data class Unlock(val choice: UnlockChoice, val confirm: Boolean) : VoiceIntent
    /** "버리지 마" 처럼 하지 말라는 말. 알아들었으니 실패로 답하지 않는다. */
    data object Negated : VoiceIntent
    /** "노래 틀어"·"창문 열어" 처럼 이 앱이 하지 않는 일. */
    data object OutOfScope : VoiceIntent
    data object Unknown : VoiceIntent
}

/**
 * 명령 하나와 그 명령으로 보는 말. 공백·문장부호를 지운 문장에 [words] 중 하나가 들어 있으면(contains) 후보이고,
 * [exact] 는 문장 전체가 그 말일 때만 후보다("응" 이 "응급실" 에 걸리지 않게).
 * 모델을 부르지 않는다: 차 안에서 오프라인으로, 같은 말에는 늘 같은 동작이 나와야 한다.
 * [example] 은 듣는 동안 띠에 보이는 예시라 해석과 같은 표에서 나온다.
 */
private class Rule(
    val intent: VoiceIntent,
    val screens: Set<VoiceScreen>,
    val example: String?,
    val words: List<String>,
    val exact: List<String> = emptyList(),
)

private val ALL = VoiceScreen.entries.toSet()
private val LIST = setOf(VoiceScreen.HOME, VoiceScreen.STORY)
private val SEAL = setOf(VoiceScreen.SEAL)

// 더 긴 단어가 이긴다("이야기 들려줘" 는 '들려줘' 3자 > '이야기로' 없음 → 듣기, "잠가버려" 는 '버려' 가 아니라 잠그기).
// 같은 길이면 표의 앞쪽이 이긴다.
private val rules = listOf(
    Rule(VoiceIntent.Record, LIST, "녹음해 줘",
        listOf("녹음해", "녹음시작", "녹음할게", "녹음할래", "녹음하기", "녹음하자", "새로녹음", "기억남겨", "남겨줘", "남길게", "남길래")),
    Rule(VoiceIntent.Play, ALL, "들려 줘",
        listOf("들려", "들려줘", "들어볼래", "들어볼", "들어보", "들어봐", "들을래", "듣기", "재생", "틀어")),
    // 맨 '이야기' 는 "이야기 들려줘" 를 목록 열기로 가로챈다. 문장 전체가 '이야기' 일 때만 받는다.
    Rule(VoiceIntent.ShowStory, setOf(VoiceScreen.HOME), "이야기 보여 줘",
        listOf("이야기보여", "이야기목록", "이차의이야기", "이야기로", "목록", "전체보기", "다보여"), exact = listOf("이야기")),
    // 맨 '홈' 은 "홈플러스" 에 걸린다.
    Rule(VoiceIntent.ShowHome, setOf(VoiceScreen.STORY), "홈으로",
        listOf("홈으로", "홈화면", "처음화면", "처음으로", "뒤로", "이전화면", "돌아가"), exact = listOf("홈")),
    // '취소' 는 차 안에서 "방금 말한 것 무시" 로 더 자주 쓴다. 녹음을 버리는 뜻이 분명할 때만.
    Rule(VoiceIntent.Discard, SEAL, "버려 줘",
        listOf("버려", "버릴래", "지워", "지울", "삭제", "녹음취소")),
    // Pleos STT 는 문장 끝 "줘" 를 자주 떨어뜨린다(2026-11-11 실측). 확정어는 어간으로 찾는다.
    // "잠가버려" 는 Discard 의 '버려' 보다 길어 잠그기가 이긴다.
    Rule(VoiceIntent.Confirm, SEAL, "잠가 줘",
        listOf("잠가", "잠궈", "잠그", "잠글", "저장", "봉인", "잠가버려", "잠궈버려", "저장해버려", "봉인해버려")),
    // 짧은 대답은 문장 전체일 때만. '어' 는 머뭇거림("어…")으로 들어와 넣지 않는다.
    Rule(VoiceIntent.Yes, SEAL, null, emptyList(),
        exact = listOf("응", "네", "예", "그래", "그래요", "좋아", "좋아요", "오케이", "확인")),
)

/** 차량·미디어 명령. 이 앱 명령과 단어가 겹쳐도("노래 틀어", "창문 열어", "문자 남겨") 어느 화면에서도 실행하지 않는다. */
private val otherApps = listOf(
    "노래", "음악", "라디오", "유튜브", "멜론", "팟캐스트", "뉴스", "전화", "문자", "메시지", "카톡",
    "안내해", "길안내", "내비", "네비", "목적지", "날씨", "에어컨", "히터", "온도", "창문", "트렁크", "선루프", "주유소", "충전소",
)
/** 하지 말라는 말. "버리지 마", "저장 안 할래", "잠그면 안 돼". '안해' 는 "안내해" 에 걸리지 않는다. */
private val negations = listOf("지마", "지말", "안할", "안해", "않", "안돼", "안되", "말자", "말래", "싫어", "아니")

// 잠그기 화면의 조건. 맨 '여기' 는 "여기 한번 들려줘"(미리 듣기)를 장소 잠그기로 바꿔 버려 넣지 않는다.
private val placeWords = listOf("여기다시", "여기에다시", "여기오면", "여기로", "이곳", "이자리", "이장소", "같은장소",
    "다시오면", "돌아오면", "다시올때", "다시왔을때", "또오면")
private val keepWords = listOf("그냥보관", "보관", "조건없이", "언제든", "아무때나", "그냥저장", "그냥둬")
/** 조건과 함께 오면 그 조건으로 바로 잠근다. "1년 뒤에 열어", "여기 다시 오면 들려". */
private val sealNowWords = listOf("잠가", "잠궈", "잠그", "잠글", "저장", "봉인", "열어", "들려", "보관해", "보관하", "보관할")
/** '집에 돌아오면' 처럼 지금 자리가 아닌 곳. 장소 잠그기는 지금 자리만 된다. */
private val elsewhere = Regex("([가-힣]{1,6}?)에(다시|또)?(돌아오면|오면|올때|왔을때)")
private val here = setOf("여기", "이곳", "이자리", "이장소", "같은장소")

/**
 * 날짜를 말한 것 같은데 [findDate] 가 읽지 못한 문장. 이때 확정어("잠가")만 보고 잠그면 화면의 기본값(1년)으로
 * 잠겨 버린다. 되돌릴 수 없으니 알아듣지 못한 것으로 끝낸다.
 */
private val dateLike = Regex("\\d+(년|해|달|개월|주|일)|뒤|후|있다가|지나|동안|요일|봄|여름|가을|겨울|생일|기념일|말에|초에|중순|월에|년에|날에|하면|되면|흘")

private val sealExamples = listOf("1년 뒤에 열어 줘", "여기 다시 오면 열어 줘", "그냥 보관해 줘")

/** 듣는 동안 띠에 보일, 이 화면에서 할 수 있는 말. 띠는 한 줄이라 앞의 몇 개만 쓴다. */
fun voiceExamples(screen: VoiceScreen): List<String> =
    (if (screen == VoiceScreen.SEAL) sealExamples else emptyList()) +
        rules.filter { screen in it.screens }.mapNotNull { it.example }

/** 알아듣지 못했을 때 토스트에 보일 예시 하나. 그 화면에서 가장 먼저 할 법한 말. */
fun voiceHint(screen: VoiceScreen): String = when (screen) {
    VoiceScreen.SEAL -> sealExamples.first()
    VoiceScreen.HOME -> rules.first { it.intent == VoiceIntent.Record }.example!!
    VoiceScreen.STORY -> rules.first { it.intent == VoiceIntent.Play }.example!!
}

/** 이 화면에서 [text] 가 뜻하는 명령. 이 화면에 없는 명령의 말이면 Unknown. */
fun parseVoiceIntent(text: String, today: LocalDate, screen: VoiceScreen): VoiceIntent {
    val t = normalize(text)
    if (t.isEmpty()) return VoiceIntent.Unknown
    if (otherApps.any(t::contains)) return VoiceIntent.OutOfScope
    if (negations.any(t::contains)) return VoiceIntent.Negated
    if (screen == VoiceScreen.SEAL) sealChoice(t, today)?.let { return it }
    return rules.asSequence()
        .filter { screen in it.screens }
        .mapNotNull { rule ->
            val longest = if (t in rule.exact) Int.MAX_VALUE else rule.words.filter(t::contains).maxOfOrNull { it.length }
            longest?.let { rule to it }
        }
        .maxWithOrNull(compareBy<Pair<Rule, Int>> { it.second }.thenByDescending { rules.indexOf(it.first) })
        ?.first?.intent
        ?: VoiceIntent.Unknown
}

/** 잠그기 화면에서 조건을 말했으면 그 조건. 날짜처럼 들리는데 못 읽었으면 Unknown. 조건이 없으면 null. */
private fun sealChoice(t: String, today: LocalDate): VoiceIntent? {
    val confirm = sealNowWords.any(t::contains)
    findDate(t, today)?.let { return VoiceIntent.Unlock(UnlockChoice.OnDate(it.date), confirm && !it.approximate) }
    if (placeWords.any(t::contains)) {
        val spot = elsewhere.find(t)?.groupValues?.get(1)
        if (spot != null && here.none(spot::endsWith)) return VoiceIntent.Unknown
        return VoiceIntent.Unlock(UnlockChoice.Place, confirm)
    }
    if (keepWords.any(t::contains)) return VoiceIntent.Unlock(UnlockChoice.Keep, confirm)
    if (dateLike.containsMatchIn(t)) return VoiceIntent.Unknown
    return null
}

private fun normalize(text: String) = text.filter { it.isLetterOrDigit() }.lowercase()

// ---- 날짜 ----

/** [approximate] 면 "내년 5월" 처럼 날을 말하지 않아 1일로 잡은 것. 확정어가 있어도 고르기만 한다. */
internal data class SpokenDate(val date: LocalDate, val approximate: Boolean = false)

private val nativeBase = listOf("한" to 1, "두" to 2, "세" to 3, "네" to 4, "다섯" to 5, "여섯" to 6, "일곱" to 7, "여덟" to 8, "아홉" to 9)
/** 고유어 수 1~29. "열두"·"스물한" 처럼 붙은 꼴까지. */
private val native: Map<String, Int> = buildMap {
    nativeBase.forEach { (w, n) -> put(w, n); put("열$w", 10 + n); put("스물$w", 20 + n) }
    put("열", 10); put("스무", 20); put("스물", 20)
}
private val NATIVE = native.keys.sortedByDescending { it.length }.joinToString("|")
private const val SINO = "[일이삼사오육칠팔구십백]+"
private const val DIGITS = "\\d+"
/** 숫자 한가운데서 매칭을 시작하지 않는다. 없으면 "열두 달" 의 '두 달', "이천이십칠년" 의 '칠년' 을 읽었다. */
private const val START = "(?<![\\d일이삼사오육칠팔구십백천만열물])"
private const val TAIL = "(반)?(쯤|정도|만|가량|이)?(뒤|후|지나|있다|만에|동안|간|에|$)"

/** 단위마다 받는 수가 다르다: '달'·'해' 는 고유어("세 달"), '년'·'개월'·'일' 은 한자어("삼 년"). "이달"·"이해" 는 수가 아니다. */
private enum class Span { YEAR, MONTH, WEEK, DAY }
private val relatives = listOf(
    Regex("$START($DIGITS|$SINO)년$TAIL") to Span.YEAR,
    Regex("$START($DIGITS|$NATIVE)해$TAIL") to Span.YEAR,
    Regex("$START($DIGITS|$SINO)개월$TAIL") to Span.MONTH,
    Regex("$START($DIGITS|$NATIVE)달$TAIL") to Span.MONTH,
    Regex("$START($DIGITS|$SINO|$NATIVE)주일?$TAIL") to Span.WEEK,
    // '일' 뒤의 '에'·문장 끝은 받지 않는다. "엔진 오일에" 를 5일 뒤로, "25일에" 를 25일 뒤로 읽었다.
    Regex("$START($DIGITS|$SINO)일(반)?(쯤|정도|만|가량|이)?(뒤|후|지나|있다|만에|동안|간)") to Span.DAY,
)
private val dayWords = mapOf("하루" to 1, "이틀" to 2, "사흘" to 3, "나흘" to 4, "닷새" to 5, "엿새" to 6, "이레" to 7, "열흘" to 10, "보름" to 15)
private val dayWord = Regex("(${dayWords.keys.joinToString("|")})(쯤|정도|만|가량|이)?(뒤|후|지나|있다|만에|동안|간|$)")
private val halfYear = Regex("반년(쯤|정도|만|가량|이)?(뒤|후|지나|있다|만에|동안|간|에|$)")
private val fullDate = Regex("(\\d{4})년($DIGITS|$SINO)월($DIGITS|$SINO)일")
private val monthDay = Regex("$START($DIGITS|$SINO)월($DIGITS|$SINO)일")
private val monthOnly = Regex("$START($DIGITS|$SINO)월(에|$)")
private val dayOfMonth = Regex("(?<![\\d월])(\\d{1,2})일에")
/** 계절·요일·월말처럼 날을 하나로 정할 수 없는 말. 있으면 "내년"·"다음 주" 로 떨어뜨리지 않고 읽지 못한 것으로 둔다. */
private val vague = Regex("요일|봄|여름|가을|겨울|말에|초에|중순|생일|기념일|첫째|둘째|셋째|넷째|마지막")

private val holidays = mapOf(
    "크리스마스" to MonthDay.of(12, 25), "성탄절" to MonthDay.of(12, 25),
    "어린이날" to MonthDay.of(5, 5), "새해" to MonthDay.of(1, 1), "신정" to MonthDay.of(1, 1),
)

/** 공백·문장부호를 지운 문장에서 미래 날짜를 찾는다. 오늘이거나 지난 날은 돌려주지 않는다. */
fun parseDate(t: String, today: LocalDate): LocalDate? = findDate(t, today)?.date

internal fun findDate(t: String, today: LocalDate): SpokenDate? {
    if (vague.containsMatchIn(t)) return null
    val yearShift = when {
        t.contains("내후년") -> 2L
        t.contains("내년") -> 1L
        else -> 0L
    }
    val limit = today.plusYears(30)
    fun future(date: LocalDate?, approximate: Boolean = false) =
        date?.takeIf { it.isAfter(today) && !it.isAfter(limit) }?.let { SpokenDate(it, approximate) }

    fullDate.find(t)?.let { m ->
        return future(dateOrNull(m.groupValues[1].toInt(), number(m.groupValues[2]), number(m.groupValues[3])))
    }
    // 한글로 읽은 연도("이천이십칠년")나 월·일 없는 연도("2027년에")는 읽지 않는다.
    if (Regex("\\d{4}년|천").containsMatchIn(t)) return null
    monthDay.find(t)?.let { m -> return future(nextOccurrence(number(m.groupValues[1]), number(m.groupValues[2]), today, yearShift)) }
    holidays.entries.firstOrNull { t.contains(it.key) }?.let { (_, md) ->
        return future(nextOccurrence(md.monthValue, md.dayOfMonth, today, yearShift))
    }
    monthOnly.find(t)?.let { m -> return future(nextOccurrence(number(m.groupValues[1]), 1, today, yearShift), approximate = true) }
    dayOfMonth.find(t)?.let { m ->
        val day = m.groupValues[1].toInt()
        val thisMonth = dateOrNull(today.year, today.monthValue, day)
        return future(if (thisMonth != null && thisMonth.isAfter(today)) thisMonth else today.plusMonths(1).let { dateOrNull(it.year, it.monthValue, day) })
    }
    relatives.mapNotNull { (regex, span) -> regex.find(t)?.let { it to span } }.minByOrNull { it.first.range.first }?.let { (m, span) ->
        val n = number(m.groupValues[1]).takeIf { it in 1..400 } ?: return null
        val half = m.groupValues[2] == "반"
        val date = when (span) {
            Span.YEAR -> today.plusYears(n.toLong()).let { if (half) it.plusMonths(6) else it }
            Span.MONTH -> if (half) return null else today.plusMonths(n.toLong()) // "한 달 반" 은 며칠인지 정하지 않았다
            Span.WEEK -> if (half) return null else today.plusWeeks(n.toLong())
            Span.DAY -> if (half) return null else today.plusDays(n.toLong())
        }
        return future(date)
    }
    dayWord.find(t)?.let { return future(today.plusDays(dayWords.getValue(it.groupValues[1]).toLong())) }
    if (halfYear.containsMatchIn(t)) return future(today.plusMonths(6))
    return future(when {
        t.contains("내후년") -> today.plusYears(2)
        t.contains("내년") -> today.plusYears(1)
        t.contains("다음달") -> today.plusMonths(1)
        t.contains("다음주") -> today.plusWeeks(1)
        t.contains("모레") -> today.plusDays(2)
        t.contains("내일") -> today.plusDays(1)
        else -> null
    })
}

/** 올해 그날이 지났거나 오늘이면 내년. "내년" 이 붙으면 그만큼 더 민다. */
private fun nextOccurrence(month: Int, day: Int, today: LocalDate, yearShift: Long): LocalDate? {
    if (yearShift > 0) return dateOrNull(today.year + yearShift.toInt(), month, day)
    val thisYear = dateOrNull(today.year, month, day) ?: return null
    return if (thisYear.isAfter(today)) thisYear else dateOrNull(today.year + 1, month, day)
}

private fun dateOrNull(y: Int, m: Int, d: Int): LocalDate? =
    if (m !in 1..12 || d !in 1..31) null else runCatching { LocalDate.of(y, m, d) }.getOrNull()

private val sinoDigit = mapOf('일' to 1, '이' to 2, '삼' to 3, '사' to 4, '오' to 5, '육' to 6, '칠' to 7, '팔' to 8, '구' to 9)

/** 숫자, 고유어 수(한·두·열두·스물…), 한자어 수(일·이·십이·백·이백오십…) 를 정수로. 읽지 못하면 -1. */
internal fun number(word: String): Int {
    word.toIntOrNull()?.let { return it }
    native[word]?.let { return it }
    var total = 0
    var digit = 0
    for (c in word) {
        val place = when (c) { '백' -> 100; '십' -> 10; else -> 0 }
        if (place > 0) {
            total += (if (digit == 0) 1 else digit) * place
            digit = 0
        } else {
            if (digit != 0) return -1 // "일이" 처럼 자리 없이 붙은 수
            digit = sinoDigit[c] ?: return -1
        }
    }
    return (total + digit).takeIf { word.isNotEmpty() } ?: -1
}
