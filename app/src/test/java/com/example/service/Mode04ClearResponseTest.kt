package com.example.service

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mode04ClearResponseTest {
    @Test
    fun acceptsPositiveMode04ResponsesWithOrWithoutCanHeader() {
        assertTrue(isMode04ClearAcknowledged("44>"))
        assertTrue(isMode04ClearAcknowledged("7E8 44\r\n>"))
        assertTrue(isMode04ClearAcknowledged("7E844>"))
    }

    @Test
    fun rejectsResponsesWithoutPositiveAcknowledgment() {
        assertFalse(isMode04ClearAcknowledged(""))
        assertFalse(isMode04ClearAcknowledged("NO DATA>"))
        assertFalse(isMode04ClearAcknowledged("UNABLE TO CONNECT>"))
        assertFalse(isMode04ClearAcknowledged("7F 04 22>"))
        assertFalse(isMode04ClearAcknowledged("7F 04 44>"))
        assertFalse(isMode04ClearAcknowledged("7F0444>"))
    }

    @Test
    fun errorTextOverridesAnAcknowledgmentToken() {
        assertFalse(isMode04ClearAcknowledged("ERROR 44>"))
    }
}
