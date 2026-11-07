package com.eco.speech

/** One final command is accepted for each explicitly started recognition session. */
class VoiceCommandSession {
    private var active = false
    private var handled = false

    fun begin() { active = true; handled = false }
    fun end() { active = false }

    fun acceptFinal(text: String): VoiceCommand? {
        if (!active || handled) return null
        handled = true
        return parseVoiceCommand(text)
    }
}
