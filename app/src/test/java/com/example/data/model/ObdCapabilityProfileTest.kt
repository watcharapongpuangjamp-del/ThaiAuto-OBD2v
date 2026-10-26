package com.example.data.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObdCapabilityProfileTest {

    @Test
    fun vehicleDiagnosticsRequireVerifiedEcuAndSupportedTest() {
        val result = ObdCapabilityResult(
            testId = "confirmed-dtc",
            support = CapabilitySupport.SUPPORTED
        )
        val adapterOnly = ObdCapabilityProfile(
            adapterIdentity = "PL2303",
            linkLevel = ObdLinkLevel.ADAPTER_RESPONDING,
            results = listOf(result)
        )
        assertFalse(adapterOnly.canRunVehicleDiagnostic("confirmed-dtc"))

        val ecuVerified = adapterOnly.copy(linkLevel = ObdLinkLevel.ECU_RESPONDING)
        assertTrue(ecuVerified.canRunVehicleDiagnostic("confirmed-dtc"))
    }

    @Test
    fun untestedOrUnsupportedCapabilityDoesNotEnableFeature() {
        val profile = ObdCapabilityProfile(
            adapterIdentity = "ELM-compatible",
            linkLevel = ObdLinkLevel.ECU_RESPONDING,
            results = listOf(
                ObdCapabilityResult("mode-09", CapabilitySupport.UNSUPPORTED)
            )
        )
        assertEquals(CapabilitySupport.NOT_TESTED, profile.supportFor("mode-03"))
        assertFalse(profile.canRunVehicleDiagnostic("mode-03"))
        assertFalse(profile.canRunVehicleDiagnostic("mode-09"))
    }

    @Test
    fun recommendedTestsAreReadOnlyAndDoNotClearCodes() {
        assertTrue(RecommendedObdCapabilityTests.defaults.isNotEmpty())
        assertTrue(RecommendedObdCapabilityTests.defaults.all { it.recommended && it.readOnly })
        assertTrue(RecommendedObdCapabilityTests.defaults.none { it.command.trim() == "04" })
    }

    @Test(expected = IllegalArgumentException::class)
    fun invalidTimeoutIsRejected() {
        ObdCapabilityTest(
            id = "invalid",
            title = "Invalid timeout",
            command = "ATI",
            category = ObdTestCategory.ADAPTER,
            timeoutMs = 0L
        )
    }
}
