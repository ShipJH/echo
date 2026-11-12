package com.eco.capsule

import android.content.Context
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 음성은 filesDir/echo-<epochMillis>.wav, 메타데이터는 SharedPreferences("capsules") 의
 * `meta:<파일명>` 에 둔다. 파일이 기준이고 메타가 없는 파일은 '조건 없이 보관' 으로 본다.
 */
class CapsuleStore(context: Context) {
    private val dir = context.filesDir
    private val prefs = context.getSharedPreferences("capsules", Context.MODE_PRIVATE)

    /**
     * 정보를 읽지 못한 녹음의 파일 이름. 목록에서 뺀다: '보관' 으로 보이면 잠긴 녹음이 그대로 들린다.
     * 예전에는 한 항목만 망가져도 load 전체가 던져 앱이 시작할 때마다 죽었다(2026-11-12 리뷰).
     */
    var unreadable: List<String> = emptyList(); private set

    fun load(): List<Capsule> {
        migrateLegacy()
        val broken = mutableListOf<String>()
        return dir.listFiles().orEmpty()
            .filter { it.name.startsWith("echo-") && it.extension == "wav" }
            .mapNotNull { file ->
                try {
                    val meta = prefs.getString(key(file.name), null)?.let(CapsuleCodec::decode)
                        ?: CapsuleCodec.Meta(UnlockCondition.Anytime)
                    Capsule(file.name, createdAt(file), wavDurationMs(file), meta.condition, meta.openedAt, meta.arrivedAt, meta.recordedPlace, meta.leftAt, meta.transcript, meta.openedEarlyAt)
                } catch (error: Exception) {
                    Log.e("EchoStore", "녹음 정보를 읽지 못함: ${file.name}", error)
                    broken += file.name
                    null
                }
            }
            .sortedByDescending { it.createdAt }
            .also { unreadable = broken }
    }

    fun file(capsule: Capsule): File = File(dir, capsule.fileName)

    /** 녹음 임시 파일을 보관함으로 옮긴다. 메타 저장에 실패하면 옮긴 파일을 되돌린다. */
    fun seal(recording: File, condition: UnlockCondition, recordedPlace: GeoPoint?): Capsule {
        val destination = File(dir, "echo-${System.currentTimeMillis()}.wav")
        check(recording.renameTo(destination)) { "녹음 파일을 옮기지 못했어요" }
        val written = prefs.edit().putString(key(destination.name), CapsuleCodec.encode(CapsuleCodec.Meta(condition, recordedPlace = recordedPlace))).commit()
        if (!written) {
            destination.renameTo(recording)
            error("녹음 정보를 저장하지 못했어요")
        }
        return load().first { it.fileName == destination.name }
    }

    fun markOpened(fileName: String, now: Long = System.currentTimeMillis()) =
        update(fileName) { it.copy(openedAt = it.openedAt ?: now) }

    fun setTranscript(fileName: String, text: String) = update(fileName) { it.copy(transcript = text) }

    /** 반경을 벗어났다. 이미 기록돼 있으면 그대로 둔다. */
    fun markLeft(fileName: String, now: Long = System.currentTimeMillis()) =
        update(fileName) { if (it.leftAt == null) it.copy(leftAt = now) else it }

    /** 반경에 들어왔다. 떠났다가 충분히 지나 돌아온 것이면 열고 true. 금방 돌아왔으면 떠남 기록을 지운다([arrival]). */
    fun markArrived(fileName: String, now: Long = System.currentTimeMillis()): Boolean {
        val meta = read(fileName) ?: return false
        return when (arrival(meta.leftAt, meta.arrivedAt, now)) {
            Arrival.OPEN -> { update(fileName) { it.copy(arrivedAt = now) }; true }
            Arrival.TOO_SOON -> { Log.i("EchoStore", "떠난 지 얼마 안 돼 다녀온 것으로 치지 않음: $fileName"); update(fileName) { it.copy(leftAt = null) }; false }
            Arrival.NOT_LEFT, Arrival.ALREADY -> false
        }
    }

    /**
     * 잠긴 녹음을 지금 연다. 예전에는 날짜를 오늘로 덮어써 원래 정한 날이 사라졌다. 이제 연 시각만 따로 남긴다.
     */
    fun openEarly(fileName: String, now: Long = System.currentTimeMillis()) =
        update(fileName) { it.copy(openedEarlyAt = it.openedEarlyAt ?: now) }

    /** 실패한 파일 이름을 돌려준다. */
    fun delete(fileNames: Collection<String>): List<String> {
        val editor = prefs.edit()
        val failed = fileNames.filter { name ->
            val file = File(dir, name)
            val removed = !file.exists() || file.delete()
            if (removed) editor.remove(key(name))
            !removed
        }
        editor.apply()
        return failed
    }

    private fun read(fileName: String): CapsuleCodec.Meta? =
        prefs.getString(key(fileName), null)?.let(CapsuleCodec::decode)

    /**
     * 항상 저장된 최신 값에서 고친다. 화면이 들고 있는 Capsule 은 지오펜스 이탈(markLeft)처럼
     * 화면을 다시 읽지 않는 갱신을 모르기 때문에, 그것으로 덮어쓰면 leftAt 이 지워진다(2026-11-11 리뷰).
     */
    private fun update(fileName: String, change: (CapsuleCodec.Meta) -> CapsuleCodec.Meta) {
        // 지운 녹음에 늦게 온 지오펜스·받아쓰기 결과가 메타만 다시 만들었다.
        if (!File(dir, fileName).exists()) { Log.w("EchoStore", "없는 녹음이라 갱신하지 않음: $fileName"); return }
        val current = read(fileName) ?: CapsuleCodec.Meta(UnlockCondition.Anytime)
        val next = change(current)
        if (next == current) return
        check(prefs.edit().putString(key(fileName), CapsuleCodec.encode(next)).commit()) { "녹음 정보를 저장하지 못했어요" }
    }

    /**
     * 2026-11-08 이전 빌드는 `<파일명>=yyyy-MM-dd`, `opened:<파일명>=true` 로 저장했다.
     * 한 번 읽어 새 형식으로 옮기고 옛 키를 지운다.
     */
    private fun migrateLegacy() {
        val legacy = prefs.all.filterKeys { it.startsWith("echo-") && it.endsWith(".wav") }
        if (legacy.isEmpty()) return
        val editor = prefs.edit()
        legacy.forEach { (name, value) ->
            val date = (value as? String)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            if (date != null && !prefs.contains(key(name))) {
                val opened = if (prefs.getBoolean("opened:$name", false)) File(dir, name).lastModified() else null
                editor.putString(key(name), CapsuleCodec.encode(CapsuleCodec.Meta(UnlockCondition.OnDate(date), opened)))
            }
            editor.remove(name).remove("opened:$name")
        }
        check(editor.commit()) { "옛 캡슐 정보를 옮기지 못했습니다" }
    }

    private fun key(fileName: String) = "meta:$fileName"

    private fun createdAt(file: File): LocalDateTime {
        val millis = file.nameWithoutExtension.removePrefix("echo-").toLongOrNull() ?: file.lastModified()
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
    }
}

/** WAV 헤더의 byteRate(오프셋 28)로 길이를 계산한다. 녹음 형식이 바뀌어도 맞게. */
fun wavDurationMs(file: File): Long {
    if (file.length() <= 44) return 0
    return RandomAccessFile(file, "r").use { input ->
        input.seek(28)
        val b = ByteArray(4).also(input::readFully)
        val byteRate = (b[0].toLong() and 0xFF) or ((b[1].toLong() and 0xFF) shl 8) or
            ((b[2].toLong() and 0xFF) shl 16) or ((b[3].toLong() and 0xFF) shl 24)
        if (byteRate <= 0) 0 else (file.length() - 44) * 1000 / byteRate
    }
}
