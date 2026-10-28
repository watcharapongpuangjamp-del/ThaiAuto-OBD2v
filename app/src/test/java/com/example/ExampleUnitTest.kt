package com.example

import com.example.service.ObdDiagnosticEngine
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {
    private val engine = ObdDiagnosticEngine()

    @Test
    fun acceptsCompletePositiveDtcResponses() {
        assertTrue(engine.isValidDtcModeResponse("43 00 00", "43"))
        assertTrue(engine.isValidDtcModeResponse("47 01 2F", "47"))
        assertTrue(engine.isValidDtcModeResponse("4A 00 00", "4A"))
    }

    @Test
    fun acceptsExplicitNoDataResponse() {
        assertTrue(engine.isValidDtcModeResponse("NO DATA", "43"))
    }

    @Test
    fun rejectsTruncatedOrWrongServiceResponses() {
        assertFalse(engine.isValidDtcModeResponse("43", "43"))
        assertFalse(engine.isValidDtcModeResponse("43 00", "43"))
        assertFalse(engine.isValidDtcModeResponse("47 00 00", "43"))
        assertFalse(engine.isValidDtcModeResponse("", "43"))
    }

    @Test
    fun rejectsAdapterErrorsAndUncertainResponses() {
        assertFalse(engine.isValidDtcModeResponse("ERROR", "43"))
        assertFalse(engine.isValidDtcModeResponse("UNABLE TO CONNECT", "43"))
        assertFalse(engine.isValidDtcModeResponse("STOPPED", "43"))
        assertFalse(engine.isValidDtcModeResponse("?", "43"))
    }
}
