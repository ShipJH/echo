package com.eco.speech

import com.eco.ui.stttest.SimulationState
import com.eco.ui.stttest.simulateCommand
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VoiceCommandTest {
    @Test fun parsesSpellingAndSpacing() {
        assertEquals(VoiceCommand.START_RECORDING, parseVoiceCommand("에코야, 녹음 시작해 줘!"))
        assertEquals(VoiceCommand.STOP_RECORDING, parseVoiceCommand("녹음 끝내줘"))
        assertEquals(VoiceCommand.PLAY_RECORDING, parseVoiceCommand("녹음 들려줘"))
        assertEquals(VoiceCommand.STOP_PLAYBACK, parseVoiceCommand("재생 멈춰"))
        assertEquals(VoiceCommand.SAVE_RECORDING, parseVoiceCommand("저장해 줘"))
        assertEquals(VoiceCommand.CANCEL_RECORDING, parseVoiceCommand("녹음 취소해 줘"))
    }

    @Test fun rejectsAmbiguousOrUnknownCommands() {
        assertEquals(VoiceCommand.UNKNOWN, parseVoiceCommand("저장하고 취소해줘"))
        assertEquals(VoiceCommand.UNKNOWN, parseVoiceCommand("오늘 날씨 어때"))
        assertEquals(VoiceCommand.UNKNOWN, parseVoiceCommand(""))
    }

    @Test fun onlyAcceptsOneFinalPerSession() {
        val session = VoiceCommandSession()
        assertNull(session.acceptFinal("저장해줘"))
        session.begin()
        assertEquals(VoiceCommand.SAVE_RECORDING, session.acceptFinal("저장해줘"))
        assertNull(session.acceptFinal("저장해줘"))
        assertNull(session.acceptFinal("취소해줘"))
        session.end()
        assertNull(session.acceptFinal("저장해줘"))
        session.begin()
        assertEquals(VoiceCommand.SAVE_RECORDING, session.acceptFinal("저장해줘"))
    }

    @Test fun simulationRespectsState() {
        var state = SimulationState.IDLE
        assertEquals(state, simulateCommand(state, VoiceCommand.SAVE_RECORDING).state)
        state = simulateCommand(state, VoiceCommand.START_RECORDING).state
        assertEquals(SimulationState.RECORDING, state)
        state = simulateCommand(state, VoiceCommand.STOP_RECORDING).state
        assertEquals(SimulationState.REVIEW, state)
        state = simulateCommand(state, VoiceCommand.PLAY_RECORDING).state
        assertEquals(SimulationState.PLAYING, state)
        assertEquals(state, simulateCommand(state, VoiceCommand.SAVE_RECORDING).state)
        state = simulateCommand(state, VoiceCommand.STOP_PLAYBACK).state
        state = simulateCommand(state, VoiceCommand.SAVE_RECORDING).state
        assertEquals(SimulationState.IDLE, state)
    }
}
