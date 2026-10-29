package com.example

import com.example.service.AgonisticDtcResult
import com.example.service.DtcStatus
import com.example.service.ObdDiagnosticEngine
import org.junit.Assert.assertEquals
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

    @Test
    fun parsesDtcBytesIntoExpectedCodeAndStatus() {
        val results = mutableMapOf<String, AgonisticDtcResult>()

        engine.parseDtcResponse("43 01 33", DtcStatus.CONFIRMED, results)

        assertEquals(1, results.size)
        assertEquals(DtcStatus.CONFIRMED, results["P0133"]?.status)
        assertEquals("01 33", results["P0133"]?.rawHexBytes)
    }

    @Test
    fun ignoresZeroPaddingInsteadOfCreatingFaultCodes() {
        val results = mutableMapOf<String, AgonisticDtcResult>()

        engine.parseDtcResponse("43 00 00 00 00", DtcStatus.CONFIRMED, results)

        assertTrue(results.isEmpty())
    }

    @Test
    fun parsesMultipleCodesFromOneResponse() {
        val results = mutableMapOf<String, AgonisticDtcResult>()

        engine.parseDtcResponse("43 01 33 03 01", DtcStatus.CONFIRMED, results)

        assertTrue(results.containsKey("P0133"))
        assertTrue(results.containsKey("P0301"))
        assertEquals(2, results.size)
    }
}
