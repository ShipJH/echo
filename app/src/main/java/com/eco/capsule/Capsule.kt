package com.eco.capsule

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class GeoPoint(val latitude: Double, val longitude: Double)

sealed interface UnlockCondition {
    data class OnDate(val date: LocalDate) : UnlockCondition
    /** 남긴 곳 반경 안에 다시 들어오면 열린다. */
    data class AtPlace(val latitude: Double, val longitude: Double, val radiusMeters: Float) : UnlockCondition
    data object Anytime : UnlockCondition
}

data class Capsule(
    val fileName: String,
    val createdAt: LocalDateTime,
    val durationMs: Long,
    val condition: UnlockCondition,
    /** 처음 들은 시각(epoch ms). 들은 적 없으면 null. */
    val openedAt: Long? = null,
    /** 장소 조건이 충족된 시각(epoch ms). [leftAt] 이후의 진입만 인정한다. */
    val arrivedAt: Long? = null,
    /** 녹음한 곳. 열림 조건과 별개로 남겨 두어 나중에 '여기서 남긴 기억' 을 찾을 수 있게 한다. */
    val recordedPlace: GeoPoint? = null,
    /**
     * 장소 반경을 처음 벗어난 시각. 녹음한 자리에서 바로 열리지 않도록 '떠났다가 돌아옴' 을 요구한다.
     */
    val leftAt: Long? = null,
    /**
     * 받아쓴 문장. null 이면 아직 받아쓰지 않았고, "" 이면 받아썼지만 말이 없었다.
     * 잠긴 녹음의 내용이 미리 보이지 않도록 화면에는 열린 뒤에만 보인다([visibleTranscript]).
     */
    val transcript: String? = null,
    /** 조건을 기다리지 않고 '지금 열기' 한 시각. 원래 조건(날짜·장소)은 그대로 남긴다. */
    val openedEarlyAt: Long? = null,
) {
    fun visibleTranscript(today: LocalDate): String? = transcript?.takeIf { it.isNotBlank() && isUnlocked(today) }

    fun isUnlocked(today: LocalDate): Boolean = openedEarlyAt != null || when (condition) {
        is UnlockCondition.OnDate -> !today.isBefore(condition.date)
        is UnlockCondition.AtPlace -> arrivedAt != null
        UnlockCondition.Anytime -> true
    }

    /** 열렸지만 아직 듣지 않은 기억 — 홈에서 먼저 제안한다. */
    fun isWaiting(today: LocalDate): Boolean = isUnlocked(today) && openedAt == null && condition != UnlockCondition.Anytime
}

/** 하버사인 거리(m). 지오펜스가 놓친 경우 앱 시작 시 현재 위치로 다시 판정할 때 쓴다. */
fun distanceMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
    val r = 6_371_000.0
    val dLat = Math.toRadians(lat2 - lat1)
    val dLng = Math.toRadians(lng2 - lng1)
    val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
    return 2 * r * asin(sqrt(a))
}

fun UnlockCondition.AtPlace.contains(latitude: Double, longitude: Double): Boolean =
    distanceMeters(this.latitude, this.longitude, latitude, longitude) <= radiusMeters
