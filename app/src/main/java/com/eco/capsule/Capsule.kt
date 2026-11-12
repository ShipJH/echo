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

/**
 * 떠났다고 볼 거리. 반경 바로 밖 한 번으로 떠남을 찍으면, 주차한 채 GPS 가 한 번 튀었다 돌아와도 열렸다(2026-11-12 리뷰).
 * 반경에 '반경과 500m 중 큰 값' 을 더한다: 100m → 600m, 300m → 800m, 1km → 2km. 고정 5km 는 동네 안에서 오가는 장소가
 * 영원히 열리지 않아 쓰지 않았다. 500m 는 도심 GPS 오차를 근거로 한 가정값이고 실차에서 재지 않았다.
 */
val UnlockCondition.AtPlace.leaveMeters: Float get() = radiusMeters + maxOf(LEAVE_MARGIN_METERS, radiusMeters)

/** 지금 위치가 이 장소에 대해 어디쯤인지. 반경과 떠남 거리 사이(NEAR)는 아무것도 판정하지 않는다. */
enum class PlaceReading { INSIDE, NEAR, AWAY }

fun UnlockCondition.AtPlace.read(latitude: Double, longitude: Double): PlaceReading {
    val d = distanceMeters(this.latitude, this.longitude, latitude, longitude)
    return when {
        d <= radiusMeters -> PlaceReading.INSIDE
        d > leaveMeters -> PlaceReading.AWAY
        else -> PlaceReading.NEAR
    }
}

enum class Arrival { OPEN, NOT_LEFT, TOO_SOON, ALREADY }

/**
 * 반경 안에 들어왔을 때 열지. 떠난 지 [MIN_AWAY_MS] 가 안 됐으면 다녀온 것으로 치지 않고 떠남 기록을 지운다(TOO_SOON):
 * GPS 튐은 잠깐 나갔다 바로 돌아온다. 지우지 않으면 튄 기록이 남아 있다가 10분 뒤 아무 위치 확인에서나 열렸다.
 */
fun arrival(leftAt: Long?, arrivedAt: Long?, now: Long): Arrival = when {
    arrivedAt != null -> Arrival.ALREADY
    leftAt == null -> Arrival.NOT_LEFT
    now - leftAt < MIN_AWAY_MS -> Arrival.TOO_SOON
    else -> Arrival.OPEN
}

const val LEAVE_MARGIN_METERS = 500f
const val MIN_AWAY_MS = 10 * 60_000L
