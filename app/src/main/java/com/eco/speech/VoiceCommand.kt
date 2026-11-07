package com.eco.speech

enum class VoiceCommand(val label: String) {
    START_RECORDING("녹음 시작"),
    STOP_RECORDING("녹음 종료"),
    PLAY_RECORDING("녹음 들어보기"),
    STOP_PLAYBACK("재생 중지"),
    SAVE_RECORDING("저장"),
    CANCEL_RECORDING("취소"),
    UNKNOWN("명령을 확인할 수 없음"),
}

/** Interpret a final recognition result; it does not execute the command. */
fun parseVoiceCommand(text: String): VoiceCommand {
    val normalized = text.filter { it.isLetterOrDigit() }.lowercase()
    val commands = buildList {
        if (listOf("녹음끝", "녹음종료", "녹음그만", "녹음멈춰", "녹음중지", "종료해줘", "끝내줘").any(normalized::contains)) add(VoiceCommand.STOP_RECORDING)
        if (listOf("재생멈춰", "재생중지", "듣기멈춰", "듣기중지", "재생그만").any(normalized::contains)) add(VoiceCommand.STOP_PLAYBACK)
        if (normalized.contains("취소")) add(VoiceCommand.CANCEL_RECORDING)
        if (normalized.contains("저장")) add(VoiceCommand.SAVE_RECORDING)
        if (listOf("들려줘", "들려주세요", "들어보기", "들어볼게", "재생해줘", "재생해주세요", "재생시작").any(normalized::contains)) {
            add(VoiceCommand.PLAY_RECORDING)
        }
        if (listOf("녹음해줘", "녹음해주세요", "녹음시작", "녹음할게", "녹음하기").any(normalized::contains)) {
            add(VoiceCommand.START_RECORDING)
        }
    }
    return commands.singleOrNull() ?: VoiceCommand.UNKNOWN
}
