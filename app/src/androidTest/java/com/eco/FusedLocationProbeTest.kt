package com.eco

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Measures what the Pleos emulator actually provides. The host injects positions with
 * `adb emu geo fix` while these tests wait, so run them through scripts/location-probe.sh.
 */
@RunWith(AndroidJUnit4::class)
class FusedLocationProbeTest {
    private val tag = "eco-location-probe"
    private lateinit var fused: FusedLocation

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fused = FusedLocation(context) }
    }

    @After
    fun tearDown() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { runCatching { fused.release() } }
    }

    @Test
    fun receivesInjectedLocation() {
        var supported = false
        InstrumentationRegistry.getInstrumentation().runOnMainSync { supported = fused.isSupported() }
        Log.i(tag, "isSupported=$supported")
        assertTrue("FusedLocation.isSupported() returned false", supported)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fused.initialize() }

        val received = CountDownLatch(1)
        var last: LocationData? = null
        var failure: Exception? = null
        val listener = object : FusedLocationListener {
            override fun onChanged(location: LocationData?) {
                Log.i(tag, "onChanged lat=${location?.latitude} lng=${location?.longitude} conf=${location?.confidence} t=${location?.timestampMillis}")
                if (location != null) { last = location; received.countDown() }
            }
            override fun onError(exception: Exception) {
                Log.e(tag, "onError $exception", exception)
                failure = exception
            }
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fused.registerFusedLocationCallback(listener) }
        val ok = received.await(45, TimeUnit.SECONDS)
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fused.unregisterFusedLocationCallback(listener) }
        assertTrue("no location within 45s, error=$failure", ok)
        assertNotNull(last)
    }

    @Test
    fun proximityAlertFiresOnEnter() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fused.initialize() }
        val entered = CountDownLatch(1)
        var failure: Exception? = null
        val listener = object : ProximityAlertListener {
            override fun onRegistered(alertId: String) { Log.i(tag, "proximity registered id=$alertId") }
            override fun onTransition(event: ProximityAlertEvent) {
                Log.i(tag, "proximity transition id=${event.alertId} ${event.transition}")
                if (event.transition == ProximityTransition.ENTER) entered.countDown()
            }
            override fun onError(exception: Exception) {
                Log.e(tag, "proximity onError $exception", exception)
                failure = exception
            }
        }
        // 해운대 해수욕장: the host script starts far away (서울시청) and then moves here.
        val request = ProximityAlertRequest(35.1587, 129.1604, 300f, setOf(ProximityTransition.ENTER))
        InstrumentationRegistry.getInstrumentation().runOnMainSync { fused.addProximityAlert(request, listener) }
        val ok = entered.await(60, TimeUnit.SECONDS)
        assertTrue("no ENTER within 60s, error=$failure", ok)
    }
}
