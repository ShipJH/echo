package com.eco.capsule

import com.eco.record.DateUnit
import com.eco.record.unlockDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime

class CapsuleTest {
    private val seoulCityHall = GeoPoint(37.5665, 126.9780)
    private fun capsule(condition: UnlockCondition, openedAt: Long? = null, arrivedAt: Long? = null) =
        Capsule("echo-1.wav", LocalDateTime.of(2026, 11, 11, 10, 0), 12_000, condition, openedAt, arrivedAt)

    @Test fun codecRoundTripsEveryCondition() {
        listOf(
            CapsuleCodec.Meta(UnlockCondition.OnDate(LocalDate.of(2029, 11, 4)), openedAt = 5L),
            CapsuleCodec.Meta(UnlockCondition.AtPlace(35.1587, 129.1604, 300f), arrivedAt = 9L, recordedPlace = seoulCityHall, leftAt = 7L),
            CapsuleCodec.Meta(UnlockCondition.Anytime),
        ).forEach { assertEquals(it, CapsuleCodec.decode(CapsuleCodec.encode(it))) }
    }

    @Test(expected = IllegalStateException::class)
    fun codecRejectsUnknownCondition() { CapsuleCodec.decode("type=mileage") }

    @Test fun dateOpensOnTheDayNotBefore() {
        val c = capsule(UnlockCondition.OnDate(LocalDate.of(2027, 11, 11)))
        assertFalse(c.isUnlocked(LocalDate.of(2027, 11, 10)))
        assertTrue(c.isUnlocked(LocalDate.of(2027, 11, 11)))
        assertTrue(c.isWaiting(LocalDate.of(2027, 11, 12)))
        assertFalse(c.copy(openedAt = 1).isWaiting(LocalDate.of(2027, 11, 12)))
    }

    @Test fun placeOpensOnlyAfterArrival() {
        val c = capsule(UnlockCondition.AtPlace(35.1587, 129.1604, 300f))
        assertFalse(c.isUnlocked(LocalDate.MAX))
        assertTrue(c.copy(arrivedAt = 1).isUnlocked(LocalDate.MIN))
    }

    @Test fun anytimeIsOpenButNeverWaiting() {
        val c = capsule(UnlockCondition.Anytime)
        assertTrue(c.isUnlocked(LocalDate.of(2026, 11, 11)))
        assertFalse(c.isWaiting(LocalDate.of(2026, 11, 11)))
    }

    @Test fun radiusCheckUsesGreatCircleDistance() {
        // 서울시청 → 부산 해운대 직선거리 약 331km.
        val km = distanceMeters(37.5665, 126.9780, 35.1587, 129.1604) / 1000
        assertTrue("got $km", km in 328.0..335.0)
        val place = UnlockCondition.AtPlace(37.5665, 126.9780, 300f)
        assertTrue(place.contains(37.5680, 126.9790)) // 약 190m
        assertFalse(place.contains(37.5700, 126.9780)) // 약 390m
    }

    @Test fun relativeDatesClampToMonthEndAndLeapDay() {
        assertEquals(LocalDate.of(2027, 2, 28), unlockDate(LocalDate.of(2027, 1, 31), DateUnit.MONTHS, 1))
        assertEquals(LocalDate.of(2028, 2, 29), unlockDate(LocalDate.of(2028, 1, 31), DateUnit.MONTHS, 1))
        assertEquals(LocalDate.of(2029, 2, 28), unlockDate(LocalDate.of(2028, 2, 29), DateUnit.YEARS, 1))
        assertEquals(LocalDate.of(2029, 11, 4), unlockDate(LocalDate.of(2026, 11, 4), DateUnit.YEARS, 3))
        assertEquals(LocalDate.of(2027, 1, 4), unlockDate(LocalDate.of(2026, 11, 4), DateUnit.MONTHS, 2))
    }
}
