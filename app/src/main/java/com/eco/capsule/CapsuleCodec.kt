package com.eco.capsule

import java.time.LocalDate

/**
 * 한 캡슐의 메타데이터를 한 줄 문자열로 바꾼다. org.json 은 로컬 단위 테스트에서 동작하지
 * 않아(android.jar 스텁) 직접 `key=value;` 형식을 쓴다. 값에는 구분자가 들어가지 않는다.
 */
object CapsuleCodec {
    data class Meta(
        val condition: UnlockCondition,
        val openedAt: Long? = null,
        val arrivedAt: Long? = null,
        val recordedPlace: GeoPoint? = null,
        val leftAt: Long? = null,
    )

    fun encode(meta: Meta): String = buildList {
        when (val c = meta.condition) {
            is UnlockCondition.OnDate -> { add("type=date"); add("date=${c.date}") }
            is UnlockCondition.AtPlace -> { add("type=place"); add("lat=${c.latitude}"); add("lng=${c.longitude}"); add("radius=${c.radiusMeters}") }
            UnlockCondition.Anytime -> add("type=anytime")
        }
        meta.openedAt?.let { add("opened=$it") }
        meta.arrivedAt?.let { add("arrived=$it") }
        meta.leftAt?.let { add("left=$it") }
        meta.recordedPlace?.let { add("recLat=${it.latitude}"); add("recLng=${it.longitude}") }
    }.joinToString(";")

    fun decode(raw: String): Meta {
        val fields = raw.split(';').filter { it.isNotBlank() }.associate {
            val i = it.indexOf('=')
            require(i > 0) { "잘못된 캡슐 필드: $it" }
            it.substring(0, i) to it.substring(i + 1)
        }
        val condition = when (val type = fields["type"]) {
            "date" -> UnlockCondition.OnDate(LocalDate.parse(fields.getValue("date")))
            "place" -> UnlockCondition.AtPlace(fields.getValue("lat").toDouble(), fields.getValue("lng").toDouble(), fields.getValue("radius").toFloat())
            "anytime" -> UnlockCondition.Anytime
            else -> error("알 수 없는 열림 조건: $type")
        }
        val recorded = fields["recLat"]?.let { GeoPoint(it.toDouble(), fields.getValue("recLng").toDouble()) }
        return Meta(condition, fields["opened"]?.toLong(), fields["arrived"]?.toLong(), recorded, fields["left"]?.toLong())
    }
}
