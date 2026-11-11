package com.eco.location

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.eco.capsule.GeoPoint
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Pleos FusedLocation SDK 1.1.3 어댑터. 2026-11-11 에뮬레이터 v2.1.0 실측:
 * isSupported()=true, `adb emu geo fix` 로 넣은 좌표가 약 1초 안에 onChanged 로 오고,
 * addProximityAlert 의 ENTER 도 온다(scripts/location-probe.sh).
 * 콜백은 바인더 스레드에서 오므로 메인으로 옮겨 전달한다.
 */
class PleosLocation(context: Context) {
    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val sdk = FusedLocation(appContext)
    private val alerts = mutableMapOf<String, String>() // alertId -> 캡슐 파일명
    private var initialized = false
    private var released = false

    val supported: Boolean = sdk.isSupported()

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, PERMISSION) == PackageManager.PERMISSION_GRANTED

    private fun ensureInitialized() {
        check(!released) { "release() 뒤에는 같은 인스턴스를 쓸 수 없습니다" }
        check(supported) { "이 차량에서는 위치를 제공하지 않습니다" }
        if (!initialized) { sdk.initialize(); initialized = true }
    }

    /**
     * 마지막 위치가 없거나 오래됐으면 첫 위치 수신까지 기다린다. 시간 안에 못 받으면 일반 예외로 실패한다.
     * withTimeout 의 TimeoutCancellationException 은 취소로 취급돼, 취소를 다시 던지는 호출부에서
     * 실패 처리 없이 코루틴이 조용히 끝났다(화면이 '확인 중' 에 멈추고 지오펜스 등록도 건너뜀, 2026-11-11).
     */
    suspend fun current(timeoutMs: Long = 8_000): GeoPoint {
        ensureInitialized()
        return withTimeoutOrNull(timeoutMs) { lastKnown() ?: firstFix() }
            ?: throw IllegalStateException("${timeoutMs}ms 안에 위치를 받지 못함")
    }

    /**
     * 마지막 위치가 [MAX_AGE_MS] 보다 오래됐으면 버린다. 오래된 위치로 봉인 지점이나 '떠남' 을 판정하면
     * 떠난 적 없이 열릴 수 있다. timestampMillis 가 벽시계가 아니라면 항상 버려지고 firstFix 로 넘어간다.
     */
    private suspend fun lastKnown(): GeoPoint? = suspendCancellableCoroutine { cont ->
        val once = AtomicBoolean(false)
        sdk.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                val fresh = location?.takeIf { System.currentTimeMillis() - it.timestampMillis <= MAX_AGE_MS }
                if (once.compareAndSet(false, true)) cont.resume(fresh?.toPoint())
            }
            override fun onError(exception: Exception) { if (once.compareAndSet(false, true)) cont.resumeWithException(exception) }
        })
    }

    // 콜백은 바인더 스레드 여러 개에서 올 수 있어 isActive 확인만으로는 두 번 resume 될 수 있다. AtomicBoolean 으로 한 번만.
    private suspend fun firstFix(): GeoPoint = suspendCancellableCoroutine { cont ->
        val once = AtomicBoolean(false)
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                if (location == null || !once.compareAndSet(false, true)) return
                main.post { runCatching { sdk.unregisterFusedLocationCallback(this) } }
                cont.resume(location.toPoint())
            }
            override fun onError(exception: Exception) { if (once.compareAndSet(false, true)) cont.resumeWithException(exception) }
        }
        sdk.registerFusedLocationCallback(listener)
        cont.invokeOnCancellation { main.post { runCatching { sdk.unregisterFusedLocationCallback(listener) } } }
    }

    /**
     * 장소 캡슐마다 지오펜스를 건다. 앱 프로세스가 살아 있는 동안만 유효하다고 보고,
     * 앱을 다시 열 때는 [current] 로 한 번 더 판정한다.
     */
    fun watch(fileName: String, place: GeoPoint, radiusMeters: Float, onEnter: (String) -> Unit, onExit: (String) -> Unit, onError: (Exception) -> Unit) {
        ensureInitialized()
        val request = ProximityAlertRequest(place.latitude, place.longitude, radiusMeters, setOf(ProximityTransition.ENTER, ProximityTransition.EXIT))
        sdk.addProximityAlert(request, object : ProximityAlertListener {
            override fun onRegistered(alertId: String) { main.post { alerts[alertId] = fileName } }
            override fun onTransition(event: ProximityAlertEvent) {
                Log.i(TAG, "지오펜스 ${event.transition} $fileName")
                main.post {
                    if (released) return@post
                    when (event.transition) {
                        ProximityTransition.ENTER -> onEnter(fileName)
                        ProximityTransition.EXIT -> onExit(fileName)
                    }
                }
            }
            override fun onError(exception: Exception) {
                Log.e(TAG, "지오펜스 오류 $fileName: ${exception.describe()}", exception)
                main.post { if (!released) onError(exception) }
            }
        })
    }

    fun release() {
        if (released) return
        released = true
        alerts.keys.forEach { id -> runCatching { sdk.removeProximityAlert(id) }.onFailure { Log.w(TAG, "지오펜스 해제 실패 $id", it) } }
        alerts.clear()
        if (initialized) sdk.release()
    }

    companion object {
        // PleosPermission.FUSED_LOCATION 과 같은 값. 그 상수가 든 fused-location-data 는 SDK 가
        // runtime 의존성으로만 끌어와 컴파일 시점에 보이지 않는다.
        const val PERMISSION: String = "pleos.car.permission.FUSED_LOCATION"
        private const val TAG = "EchoLocation"
        private const val MAX_AGE_MS = 2 * 60_000L
    }
}

private fun LocationData.toPoint() = GeoPoint(latitude, longitude)

/** LocationException 은 ErrorCode 를 들고 있지만 컴파일 시점에 타입이 보이지 않아 toString 으로 드러낸다. */
fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: toString()
