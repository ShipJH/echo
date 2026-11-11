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
import kotlinx.coroutines.withTimeout
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

    /** 마지막 위치가 없으면 첫 위치 수신까지 기다린다. */
    suspend fun current(timeoutMs: Long = 8_000): GeoPoint = withTimeout(timeoutMs) {
        ensureInitialized()
        lastKnown() ?: firstFix()
    }

    private suspend fun lastKnown(): GeoPoint? = suspendCancellableCoroutine { cont ->
        sdk.getLastKnownLocation(object : FusedLocationListener {
            override fun onChanged(location: LocationData?) { if (cont.isActive) cont.resume(location?.toPoint()) }
            override fun onError(exception: Exception) { if (cont.isActive) cont.resumeWithException(exception) }
        })
    }

    private suspend fun firstFix(): GeoPoint = suspendCancellableCoroutine { cont ->
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                if (location == null || !cont.isActive) return
                main.post { runCatching { sdk.unregisterFusedLocationCallback(this) } }
                cont.resume(location.toPoint())
            }
            override fun onError(exception: Exception) { if (cont.isActive) cont.resumeWithException(exception) }
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
    }
}

private fun LocationData.toPoint() = GeoPoint(latitude, longitude)

/** LocationException 은 ErrorCode 를 들고 있지만 컴파일 시점에 타입이 보이지 않아 toString 으로 드러낸다. */
fun Throwable.describe(): String = message?.takeIf { it.isNotBlank() } ?: toString()
