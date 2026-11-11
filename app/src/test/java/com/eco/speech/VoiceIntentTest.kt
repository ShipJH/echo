package com.eco.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class VoiceIntentTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun date(text: String) = parseDate(text.filter { it.isLetterOrDigit() }, today)
    private fun intent(text: String) = parseVoiceIntent(text, today)

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
        assertNull(date("열흘 뒤")) // 고유어 날수(열흘·사흘)는 아직 해석하지 않는다
    }

    @Test fun namedDaysAndHolidays() {
        assertEquals(LocalDate.of(2027, 11, 11), date("내년에 들려 줘"))
        assertEquals(LocalDate.of(2028, 11, 11), date("내후년"))
        assertEquals(LocalDate.of(2026, 12, 11), date("다음 달"))
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
        assertNull(date("2020년 1월 1일")) // 지난 날
        assertNull(date("2월 30일"))
    }

    @Test fun numbers() {
        assertEquals(12, number("십이"))
        assertEquals(20, number("이십"))
        assertEquals(25, number("이십오"))
        assertEquals(10, number("열"))
        assertEquals(3, number("세"))
    }

    @Test fun intentsOnSealScreen() {
        assertEquals(VoiceIntent.Unlock(UnlockChoice.OnDate(LocalDate.of(2027, 11, 11)), confirm = true), intent("1년 뒤에 열어 줘"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.OnDate(LocalDate.of(2027, 11, 11)), confirm = false), intent("1년 뒤"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.Place, confirm = true), intent("여기 다시 오면 들려 줘"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.Place, confirm = false), intent("다시 오면"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.Keep, confirm = true), intent("그냥 보관해 줘"))
        assertEquals(VoiceIntent.Play, intent("들려줘"))
        assertEquals(VoiceIntent.Discard, intent("버려 줘"))
        assertEquals(VoiceIntent.Confirm, intent("잠가 줘"))
        // STT 가 실제로 돌려준 문장(2026-11-11 에뮬레이터, 합성 음성)
        assertEquals(VoiceIntent.Unlock(UnlockChoice.OnDate(LocalDate.of(2027, 11, 11)), confirm = true), intent("일년 뒤에 열어"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.Place, confirm = true), intent("여기 다시 오면 들려"))
        assertEquals(VoiceIntent.Unlock(UnlockChoice.Keep, confirm = true), intent("그냥 보관해줘"))
        assertEquals(VoiceIntent.Play, intent("들려"))
    }

    @Test fun intentsOnHome() {
        assertEquals(VoiceIntent.Record, intent("녹음해 줘"))
        assertEquals(VoiceIntent.Record, intent("기억 남겨 줘"))
        assertEquals(VoiceIntent.Record, intent("녹음해"))
        assertEquals(VoiceIntent.ShowStory, intent("이야기 보여 줘"))
        assertEquals(VoiceIntent.Unknown, intent("오늘 날씨 어때"))
        assertEquals(VoiceIntent.Unknown, intent(""))
    }
}
