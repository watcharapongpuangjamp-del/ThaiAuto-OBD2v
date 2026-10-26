package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdDiagnosticEngineResponseClassificationTest {
    @Test
    fun recognizesAdapterConnectionErrors() {
        val engine = ObdDiagnosticEngine()

        assertTrue(engine.isAdapterErrorResponse("UNABLE TO CONNECT>"))
        assertTrue(engine.isAdapterErrorResponse("CAN ERROR>"))
        assertTrue(engine.isAdapterErrorResponse("BUS INIT: ERROR"))
    }

    @Test
    fun doesNotTreatNoDataAsAnAdapterError() {
        assertFalse(ObdDiagnosticEngine().isAdapterErrorResponse("NO DATA>"))
    }
}
