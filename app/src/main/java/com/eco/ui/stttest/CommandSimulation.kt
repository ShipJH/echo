package com.eco.ui.stttest

import com.eco.speech.VoiceCommand

enum class SimulationState(val label: String) { IDLE("녹음 대기"), RECORDING("녹음 중"), REVIEW("녹음 완료"), PLAYING("들어보는 중") }

data class SimulationResult(val state: SimulationState, val message: String)

fun simulateCommand(state: SimulationState, command: VoiceCommand): SimulationResult {
    val next = when (state) {
        SimulationState.IDLE -> if (command == VoiceCommand.START_RECORDING) SimulationState.RECORDING else null
        SimulationState.RECORDING -> when (command) {
            VoiceCommand.STOP_RECORDING -> SimulationState.REVIEW
            VoiceCommand.CANCEL_RECORDING -> SimulationState.IDLE
            else -> null
        }
        SimulationState.REVIEW -> when (command) {
            VoiceCommand.PLAY_RECORDING -> SimulationState.PLAYING
            VoiceCommand.SAVE_RECORDING, VoiceCommand.CANCEL_RECORDING -> SimulationState.IDLE
            else -> null
        }
        SimulationState.PLAYING -> when (command) {
            VoiceCommand.STOP_PLAYBACK -> SimulationState.REVIEW
            VoiceCommand.CANCEL_RECORDING -> SimulationState.IDLE
            else -> null
        }
    }
    return if (next == null) SimulationResult(state, "현재 상태에서 실행할 수 없는 명령입니다.")
    else SimulationResult(next, "${command.label} 처리됨")
}
