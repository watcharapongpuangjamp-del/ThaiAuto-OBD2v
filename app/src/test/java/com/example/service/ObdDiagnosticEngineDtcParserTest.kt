package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ObdDiagnosticEngineDtcParserTest {
    @Test
    fun parsesSpacedMode03Response() {
        val results = mutableMapOf<String, AgonisticDtcResult>()

        ObdDiagnosticEngine().parseDtcResponse("43 01 33 00 00 >", DtcStatus.CONFIRMED, results)

        assertEquals(listOf("P0133"), results.keys.toList())
        assertEquals(DtcStatus.CONFIRMED, results["P0133"]?.status)
    }

    @Test
    fun parsesCompactMode03ResponseWhenAts0IsEnabled() {
        val results = mutableMapOf<String, AgonisticDtcResult>()

        ObdDiagnosticEngine().parseDtcResponse("4301330000>", DtcStatus.CONFIRMED, results)

        assertEquals(listOf("P0133"), results.keys.toList())
    }

    @Test
    fun ignoresZeroPaddingAndNonHexErrorResponses() {
        val results = mutableMapOf<String, AgonisticDtcResult>()

        ObdDiagnosticEngine().parseDtcResponse("NO DATA>", DtcStatus.CONFIRMED, results)
        ObdDiagnosticEngine().parseDtcResponse("43 00 00 >", DtcStatus.CONFIRMED, results)

        assertEquals(emptyList<String>(), results.keys.toList())
    }
}
