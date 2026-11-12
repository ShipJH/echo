package com.eco.speech

import ai.pleos.playground.stt.constant.ErrorType
import org.junit.Assert.assertEquals
import org.junit.Test

class SpeechServiceTest {
    /** 말로 하기를 거두는 오류는 다시 눌러도 같은 결과가 나올 것뿐이다. 마이크·시간 초과로 거두면 한 번 실패에 기능이 사라진다. */
    @Test fun onlyPermanentErrorsWithdrawVoice() {
        val withdrawn = ErrorType.entries.filter(SpeechService::unsupported).toSet()
        assertEquals(setOf(ErrorType.UNAVAILABLE_LANGUAGE, ErrorType.NOT_SUPPORTED_API), withdrawn)
    }
}
