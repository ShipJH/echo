package com.eco.ui.format

import com.eco.capsule.Capsule
import com.eco.capsule.UnlockCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class FormatTest {
    private val today = LocalDate.of(2026, 11, 11)
    private fun capsule(condition: UnlockCondition, openedAt: Long? = null, arrivedAt: Long? = null) =
        Capsule("echo-1.wav", LocalDateTime.of(2026, 11, 1, 9, 0), 5_000, condition, openedAt, arrivedAt)

    @Test fun durationIsSpokenNotClock() {
        assertEquals("5초", durationWords(5_400))
        assertEquals("1분", durationWords(60_000))
        assertEquals("1분 12초", durationWords(72_000))
        assertEquals("0초", durationWords(-1))
        assertEquals("02:14", formatClock(134_000))
    }

    @Test fun headlineTakesFirstSentence() {
        assertEquals("우리 지우 첫 바다 가는 날이야 지우야 지금 기분이 어때", headline("우리 지우 첫 바다 가는 날이야 지우야 지금 기분이 어때? 두근두근해", 40))
        assertEquals("우리 지우 첫 바다…", headline("우리 지우 첫 바다 가는 날이야", 10))
        assertEquals("녹음", headline("  녹음  "))
    }

    @Test fun statusSeparatesEveryState() {
        assertEquals(CapsuleStatus.LOCKED_DATE, capsule(UnlockCondition.OnDate(today.plusDays(1))).status(today))
        assertEquals(CapsuleStatus.LOCKED_PLACE, capsule(UnlockCondition.AtPlace(37.0, 127.0, 300f)).status(today))
        assertEquals(CapsuleStatus.NEW, capsule(UnlockCondition.OnDate(today)).status(today))
        assertEquals(CapsuleStatus.HEARD, capsule(UnlockCondition.OnDate(today), openedAt = 1).status(today))
        assertEquals(CapsuleStatus.KEPT, capsule(UnlockCondition.Anytime).status(today))
        assertEquals("2026.11.12 열림 예정", statusLabel(capsule(UnlockCondition.OnDate(today.plusDays(1))), today))
        assertEquals("돌아오면 열림", statusLabel(capsule(UnlockCondition.AtPlace(37.0, 127.0, 300f)), today))
    }
}
