package com.eco.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class VoiceIntentTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun date(text: String) = parseDate(text.filter { it.isLetterOrDigit() }, today)
    private fun seal(text: String) = parseVoiceIntent(text, today, VoiceScreen.SEAL)
    private fun home(text: String) = parseVoiceIntent(text, today, VoiceScreen.HOME)
    private fun story(text: String) = parseVoiceIntent(text, today, VoiceScreen.STORY)
    private fun on(y: Int, m: Int, d: Int, confirm: Boolean) = VoiceIntent.Unlock(UnlockChoice.OnDate(LocalDate.of(y, m, d)), confirm)

    @Test fun relativeWithDigitsAndKoreanNumerals() {
        assertEquals(LocalDate.of(2027, 11, 11), date("1년 뒤에"))
        assertEquals(LocalDate.of(2027, 11, 11), date("일 년 뒤에 열어 줘"))
        assertEquals(LocalDate.of(2029, 11, 11), date("삼 년 후"))
        assertEquals(LocalDate.of(2036, 11, 11), date("십 년 뒤"))
        assertEquals(LocalDate.of(2027, 2, 11), date("세 달 뒤"))
        assertEquals(LocalDate.of(2026, 12, 11), date("한 달 후에"))
        assertEquals(LocalDate.of(2027, 5, 11), date("6개월 뒤"))
        assertEquals(LocalDate.of(2027, 5, 11), date("육 개월 뒤"))
        assertEquals(LocalDate.of(2026, 11, 25), date("2주 뒤"))
        assertEquals(LocalDate.of(2026, 11, 18), date("일주일 뒤"))
        assertEquals(LocalDate.of(2026, 11, 25), date("이주일 뒤"))
        assertEquals(LocalDate.of(2027, 11, 11), date("열두 달 뒤")) // '두 달' 로 읽지 않는다
        assertEquals(LocalDate.of(2027, 10, 11), date("열한 달 뒤"))
        assertEquals(LocalDate.of(2051, 11, 11), date("스물다섯 해 뒤"))
        assertEquals(LocalDate.of(2027, 2, 19), date("백 일 뒤"))
        assertEquals(LocalDate.of(2027, 2, 19), date("100일 뒤"))
        assertEquals(LocalDate.of(2028, 5, 11), date("1년 반 뒤"))
        assertEquals(LocalDate.of(2027, 5, 11), date("반년 뒤"))
        assertEquals(LocalDate.of(2027, 11, 11), date("1년쯤 뒤"))
        assertEquals(LocalDate.of(2027, 11, 11), date("1년 동안"))
    }

    @Test fun nativeDayWords() {
        assertEquals(LocalDate.of(2026, 11, 13), date("이틀 뒤"))
        assertEquals(LocalDate.of(2026, 11, 14), date("사흘 뒤"))
        assertEquals(LocalDate.of(2026, 11, 21), date("열흘 뒤"))
        assertEquals(LocalDate.of(2026, 11, 26), date("보름 뒤"))
    }

    @Test fun namedDaysAndHolidays() {
        assertEquals(LocalDate.of(2027, 11, 11), date("내년에 들려 줘"))
        assertEquals(LocalDate.of(2028, 11, 11), date("내후년"))
        assertEquals(LocalDate.of(2026, 12, 11), date("다음 달"))
        assertEquals(LocalDate.of(2026, 11, 18), date("다음 주"))
        assertEquals(LocalDate.of(2026, 12, 25), date("크리스마스에"))
        assertEquals(LocalDate.of(2027, 12, 25), date("내년 크리스마스"))
        assertEquals(LocalDate.of(2027, 5, 5), date("어린이날"))
        assertEquals(LocalDate.of(2026, 11, 12), date("내일"))
    }

    @Test fun absoluteDates() {
        assertEquals(LocalDate.of(2030, 5, 5), date("2030년 5월 5일에 열어 줘"))
        assertEquals(LocalDate.of(2027, 5, 5), date("5월 5일"))
        assertEquals(LocalDate.of(2026, 12, 24), date("12월 24일"))
        assertEquals(LocalDate.of(2027, 11, 11), date("11월 11일")) // 오늘이면 내년
        assertEquals(LocalDate.of(2027, 5, 5), date("내년 오월 오일"))
        assertEquals(LocalDate.of(2026, 11, 25), date("25일에")) // 다가오는 그 날
        assertEquals(LocalDate.of(2026, 12, 5), date("5일에"))
        assertNull(date("2020년 1월 1일")) // 지난 날
        assertNull(date("2월 30일"))
    }

    @Test fun vagueOrMisreadDatesAreNotDates() {
        assertNull(date("이달에"))
        assertNull(date("이해"))
        assertNull(date("엔진 오일에 대해"))
        assertNull(date("이천이십칠년에"))
        assertNull(date("2027년에"))
        assertNull(date("다음 주 월요일에"))
        assertNull(date("다음 달 말에"))
        assertNull(date("내년 봄에"))
        assertNull(date("한 달 반 뒤"))
        assertNull(date("50년 뒤")) // 30년보다 먼 날은 받지 않는다
    }

    @Test fun numbers() {
        assertEquals(12, number("십이"))
        assertEquals(20, number("이십"))
        assertEquals(25, number("이십오"))
        assertEquals(100, number("백"))
        assertEquals(250, number("이백오십"))
        assertEquals(10, number("열"))
        assertEquals(12, number("열두"))
        assertEquals(21, number("스물한"))
        assertEquals(3, number("세"))
        assertEquals(-1, number("일이"))
    }

    /** STT 가 실제로 돌려준 문장(2026-11-11 에뮬레이터, 합성 음성). */
    @Test fun measuredSttSentences() {
        assertEquals(on(2027, 11, 11, true), seal("일년 뒤에 열어"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.Place, true), seal("여기 다시 오면 들려"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.Keep, true), seal("그냥 보관해줘"))
        assertEquals(VoiceIntent.Play, seal("들려"))
        assertEquals(VoiceIntent.Record, home("녹음해"))
    }

    @Test fun commandsBelongToTheirScreen() {
        assertEquals(VoiceIntent.Unknown, home("버려 줘"))
        assertEquals(VoiceIntent.Unknown, home("1년 뒤에 열어 줘"))
        assertEquals(VoiceIntent.Unknown, story("잠가 줘"))
        assertEquals(VoiceIntent.Unknown, story("삭제해 줘"))
        assertEquals(VoiceIntent.Unknown, seal("녹음해 줘"))
        assertEquals(VoiceIntent.Unknown, seal("이야기 보여 줘"))
    }

    @Test fun hintIsUnderstoodOnItsScreen() {
        VoiceScreen.entries.forEach { screen ->
            assertNotEquals(screen.name, VoiceIntent.Unknown, parseVoiceIntent(voiceHint(screen), today, screen))
        }
    }

    @Test fun examplesAreUnderstoodOnTheirScreen() {
        // 띠에 보이는 예시를 그대로 말하면 그 화면에서 알아들어야 한다.
        VoiceScreen.entries.forEach { screen ->
            voiceExamples(screen).forEach { example ->
                assertNotEquals("$screen: $example", VoiceIntent.Unknown, parseVoiceIntent(example, today, screen))
            }
        }
    }

    /**
     * 화면 | 말 | 기대. 잠그기 화면의 오탐은 되돌릴 수 없는 잠금이 되므로 Unknown 쪽 사례를 많이 둔다
     * (2026-11-12 어휘 QA 에서 실제로 잘못 잠기던 문장들).
     */
    @Test fun regressionTable() {
        val U = VoiceIntent.Unknown
        val cases = listOf(
            // 되돌릴 수 없는 잠금이 되면 안 되는 말
            Triple(VoiceScreen.SEAL, "응급실 가는 길 알려줘", U),
            Triple(VoiceScreen.SEAL, "반응이 왜 이래", U),
            Triple(VoiceScreen.SEAL, "그래프 보여줘", U),
            Triple(VoiceScreen.SEAL, "그래도 될까", U),
            Triple(VoiceScreen.SEAL, "안 좋아", U),
            Triple(VoiceScreen.SEAL, "날씨 좋아?", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.SEAL, "다시 확인해 볼래", U),
            Triple(VoiceScreen.SEAL, "저장 안 할래", VoiceIntent.Negated),
            Triple(VoiceScreen.SEAL, "아직 잠그지 않을래", VoiceIntent.Negated),
            Triple(VoiceScreen.SEAL, "잠그면 안 돼", VoiceIntent.Negated),
            Triple(VoiceScreen.SEAL, "버리지 마", VoiceIntent.Negated),
            Triple(VoiceScreen.SEAL, "삭제하지 말고", VoiceIntent.Negated),
            Triple(VoiceScreen.SEAL, "삭제하면 안 돼", VoiceIntent.Negated),
            Triple(VoiceScreen.SEAL, "아니 취소", VoiceIntent.Negated),
            Triple(VoiceScreen.SEAL, "여기 창문 좀 열어 줘", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.SEAL, "내일 트렁크 열어", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.SEAL, "여기 주유소 들려서 가자", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.SEAL, "집에 돌아오면 들려줘", U),
            Triple(VoiceScreen.SEAL, "노래 틀어줘", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.SEAL, "한 달 반 뒤에 잠가", U),
            Triple(VoiceScreen.SEAL, "생일에 잠가 줘", U),
            Triple(VoiceScreen.SEAL, "설날에 잠가 줘", U),
            Triple(VoiceScreen.SEAL, "졸업하면 잠가 줘", U),
            Triple(VoiceScreen.SEAL, "다음 주 월요일에 열어", U),
            Triple(VoiceScreen.SEAL, "다음 달 말에 열어", U),
            Triple(VoiceScreen.SEAL, "내년 봄에 열어", U),
            Triple(VoiceScreen.SEAL, "첫째 주", U),
            Triple(VoiceScreen.SEAL, "결혼기념일에 열어", U),
            Triple(VoiceScreen.SEAL, "이천이십칠년에 열어", U),
            Triple(VoiceScreen.SEAL, "2027년에 열어", U),
            Triple(VoiceScreen.SEAL, "이달에 열어", U),
            Triple(VoiceScreen.SEAL, "엔진 오일에 대해", U),
            Triple(VoiceScreen.SEAL, "이월 이십구일", U),
            Triple(VoiceScreen.SEAL, "다른 장소에서 열래", U),
            Triple(VoiceScreen.SEAL, "어", U),
            // 날짜
            Triple(VoiceScreen.SEAL, "1년 뒤에 열어 줘", on(2027, 11, 11, true)),
            Triple(VoiceScreen.SEAL, "1년 뒤", on(2027, 11, 11, false)),
            Triple(VoiceScreen.SEAL, "열흘 뒤에 잠가 줘", on(2026, 11, 21, true)),
            Triple(VoiceScreen.SEAL, "열두 달 뒤", on(2027, 11, 11, false)),
            Triple(VoiceScreen.SEAL, "열한 달 뒤에 열어", on(2027, 10, 11, true)),
            Triple(VoiceScreen.SEAL, "25일에 열어", on(2026, 11, 25, true)),
            Triple(VoiceScreen.SEAL, "내년 5월에 열어", on(2027, 5, 1, false)), // 날을 말하지 않았으니 고르기만
            Triple(VoiceScreen.SEAL, "이주일 뒤", on(2026, 11, 25, false)),
            Triple(VoiceScreen.SEAL, "1년쯤 뒤에 열어", on(2027, 11, 11, true)),
            Triple(VoiceScreen.SEAL, "1년 동안 잠가", on(2027, 11, 11, true)),
            Triple(VoiceScreen.SEAL, "1년 반 뒤에 열어", on(2028, 5, 11, true)),
            Triple(VoiceScreen.SEAL, "반년 뒤", on(2027, 5, 11, false)),
            Triple(VoiceScreen.SEAL, "보름 뒤에 열어", on(2026, 11, 26, true)),
            Triple(VoiceScreen.SEAL, "백 일 뒤에 열어", on(2027, 2, 19, true)),
            Triple(VoiceScreen.SEAL, "10년 후에 열어", on(2036, 11, 11, true)),
            Triple(VoiceScreen.SEAL, "2주 후에 열어 주세요", on(2026, 11, 25, true)),
            Triple(VoiceScreen.SEAL, "크리스마스에 열어", on(2026, 12, 25, true)),
            // 장소·보관
            Triple(VoiceScreen.SEAL, "여기 다시 오면 들려 줘", VoiceIntent.Unlock(UnlockChoice.Place, true)),
            Triple(VoiceScreen.SEAL, "다시 오면", VoiceIntent.Unlock(UnlockChoice.Place, false)),
            Triple(VoiceScreen.SEAL, "또 오면 열어", VoiceIntent.Unlock(UnlockChoice.Place, true)),
            Triple(VoiceScreen.SEAL, "여기에 다시 오면 열어", VoiceIntent.Unlock(UnlockChoice.Place, true)),
            Triple(VoiceScreen.SEAL, "그냥 보관해 줘", VoiceIntent.Unlock(UnlockChoice.Keep, true)),
            Triple(VoiceScreen.SEAL, "그냥 저장", VoiceIntent.Unlock(UnlockChoice.Keep, true)),
            Triple(VoiceScreen.SEAL, "보관할래", VoiceIntent.Unlock(UnlockChoice.Keep, true)),
            Triple(VoiceScreen.SEAL, "그냥 둬", VoiceIntent.Unlock(UnlockChoice.Keep, false)),
            // 잠그기 화면의 다른 명령
            Triple(VoiceScreen.SEAL, "잠가 줘", VoiceIntent.Confirm),
            Triple(VoiceScreen.SEAL, "잠글래", VoiceIntent.Confirm),
            Triple(VoiceScreen.SEAL, "잠가 주세요", VoiceIntent.Confirm),
            Triple(VoiceScreen.SEAL, "잠가버려", VoiceIntent.Confirm),
            Triple(VoiceScreen.SEAL, "저장해 버려", VoiceIntent.Confirm),
            Triple(VoiceScreen.SEAL, "응", VoiceIntent.Yes),
            Triple(VoiceScreen.SEAL, "네", VoiceIntent.Yes),
            Triple(VoiceScreen.SEAL, "좋아", VoiceIntent.Yes),
            Triple(VoiceScreen.SEAL, "들려줘", VoiceIntent.Play),
            Triple(VoiceScreen.SEAL, "여기 한번 들려줘", VoiceIntent.Play),
            Triple(VoiceScreen.SEAL, "다시 들어 보자", VoiceIntent.Play),
            Triple(VoiceScreen.SEAL, "버려 줘", VoiceIntent.Discard),
            Triple(VoiceScreen.SEAL, "지울래", VoiceIntent.Discard),
            Triple(VoiceScreen.SEAL, "녹음 취소", VoiceIntent.Discard),
            // 홈
            Triple(VoiceScreen.HOME, "녹음해 줘", VoiceIntent.Record),
            Triple(VoiceScreen.HOME, "기억 남겨 줘", VoiceIntent.Record),
            Triple(VoiceScreen.HOME, "녹음할래", VoiceIntent.Record),
            Triple(VoiceScreen.HOME, "새로 녹음 시작", VoiceIntent.Record),
            Triple(VoiceScreen.HOME, "오늘 이야기 녹음할래", VoiceIntent.Record),
            Triple(VoiceScreen.HOME, "녹음 들려", VoiceIntent.Play),
            Triple(VoiceScreen.HOME, "녹음 들려줘", VoiceIntent.Play),
            Triple(VoiceScreen.HOME, "이야기 들려줘", VoiceIntent.Play),
            Triple(VoiceScreen.HOME, "들어볼까", VoiceIntent.Play),
            Triple(VoiceScreen.HOME, "재생해 줘", VoiceIntent.Play),
            Triple(VoiceScreen.HOME, "이야기 보여 줘", VoiceIntent.ShowStory),
            Triple(VoiceScreen.HOME, "이 차의 이야기", VoiceIntent.ShowStory),
            Triple(VoiceScreen.HOME, "이야기", VoiceIntent.ShowStory),
            Triple(VoiceScreen.HOME, "목록", VoiceIntent.ShowStory),
            Triple(VoiceScreen.HOME, "음악 재생해 줘", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.HOME, "라디오 틀어", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.HOME, "문자 남겨 줘", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.HOME, "녹음하지 마", VoiceIntent.Negated),
            Triple(VoiceScreen.HOME, "오늘 날씨 어때", VoiceIntent.OutOfScope),
            Triple(VoiceScreen.HOME, "열어 줘", U),
            Triple(VoiceScreen.HOME, "", U),
            // 이 차의 이야기
            Triple(VoiceScreen.STORY, "들려줘", VoiceIntent.Play),
            Triple(VoiceScreen.STORY, "이야기 들려줘", VoiceIntent.Play),
            Triple(VoiceScreen.STORY, "홈으로 가 줘", VoiceIntent.ShowHome),
            Triple(VoiceScreen.STORY, "처음으로", VoiceIntent.ShowHome),
            Triple(VoiceScreen.STORY, "뒤로 가기", VoiceIntent.ShowHome),
            Triple(VoiceScreen.STORY, "홈플러스 가자", U),
            Triple(VoiceScreen.STORY, "녹음해 줘", VoiceIntent.Record),
        )
        val failures = cases.mapNotNull { (screen, text, expected) ->
            val actual = parseVoiceIntent(text, today, screen)
            if (actual == expected) null else "$screen | $text | 기대 $expected | 실제 $actual"
        }
        assertEquals(failures.joinToString("\n"), 0, failures.size)
    }
}
