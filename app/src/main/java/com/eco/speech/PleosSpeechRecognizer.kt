package com.eco.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import java.util.Locale

/** Own one instance per active screen; call close when it is no longer needed. */
class PleosSpeechRecognizer(context: Context, private val onEvent: (Event) -> Unit) {
    sealed interface Event {
        data object Initialized : Event
        data object Ready : Event
        data object Started : Event
        data class Result(val text: String, val completed: Boolean) : Event
        data object Ended : Event
        data class Error(val message: String) : Event
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var closed = false
    private val sdk = SpeechToText(context.applicationContext, Mode.ON_DEVICE)
    private fun emit(event: Event) { mainHandler.post { if (!closed) onEvent(event) } }
    private val listener = object : ResultListener {
        override fun onReady() = emit(Event.Ready)
        override fun onStartedRecognition() = emit(Event.Started)
        override fun onUpdated(stt: String, completed: Boolean) = emit(Event.Result(stt, completed))
        override fun onUpdatedEpdData(on: Long, off: Long) = Unit
        override fun onUpdatedRms(rms: Double) = Unit
        override fun onEndedRecognition() = emit(Event.Ended)
        override fun onError(errorType: ErrorType) = emit(Event.Error(errorType.toString()))
    }

    fun initialize() {
        sdk.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                mainHandler.post {
                    if (!closed) {
                        try {
                            sdk.addListener(listener)
                            sdk.setLocale(Locale.KOREA)
                            onEvent(Event.Initialized)
                        } catch (error: Exception) { onEvent(Event.Error(error.message ?: "초기화 실패")) }
                    }
                }
            }
            override fun onFailure(errorType: ErrorType) = emit(Event.Error(errorType.toString()))
        })
    }

    fun start() = sdk.request()
    fun stop() = sdk.stop()
    fun close() {
        if (closed) return
        closed = true
        runCatching { sdk.stop() }
        runCatching { sdk.removeListener(listener) }
        runCatching { sdk.release() }
    }
}
