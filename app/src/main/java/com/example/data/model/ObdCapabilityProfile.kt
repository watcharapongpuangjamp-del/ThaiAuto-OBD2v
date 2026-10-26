package com.example.data.model

/**
 * Independent evidence levels for a physical OBD connection.
 *
 * Finding a USB serial adapter does not prove that an ELM327 responds,
 * and an ELM327 response does not prove that a vehicle ECU is reachable.
 */
enum class ObdLinkLevel {
    DISCONNECTED,
    USB_DEVICE_DETECTED,
    SERIAL_PORT_OPEN,
    ADAPTER_RESPONDING,
    VEHICLE_PROTOCOL_DETECTED,
    ECU_RESPONDING
}

enum class CapabilitySupport {
    SUPPORTED,
    UNSUPPORTED,
    NOT_TESTED,
    INCONCLUSIVE,
    ERROR
}

enum class ObdTestCategory {
    HARDWARE,
    ADAPTER,
    PROTOCOL,
    ECU,
    DIAGNOSTIC
}

/**
 * A configurable, read-only test definition. Commands which can change
 * vehicle state (for example Mode 04 clear-DTC) must not be recommended
 * as automatic connection checks.
 */
data class ObdCapabilityTest(
    val id: String,
    val title: String,
    val command: String,
    val category: ObdTestCategory,
    val recommended: Boolean = false,
    val timeoutMs: Long = 3_000L,
    val delayAfterMs: Long = 100L,
    val retries: Int = 1,
    val repeatCount: Int = 1,
    val enabled: Boolean = true,
    val readOnly: Boolean = true
) {
    init {
        require(id.isNotBlank()) { "Test id must not be blank" }
        require(title.isNotBlank()) { "Test title must not be blank" }
        require(command.isNotBlank()) { "Test command must not be blank" }
        require(timeoutMs in 100L..60_000L) { "Timeout must be between 100 and 60000 ms" }
        require(delayAfterMs in 0L..10_000L) { "Inter-command delay must be between 0 and 10000 ms" }
        require(retries in 0..5) { "Retries must be between 0 and 5" }
        require(repeatCount in 1..10) { "Repeat count must be between 1 and 10" }
    }
}

data class ObdCapabilityResult(
    val testId: String,
    val support: CapabilitySupport,
    val rawResponse: String = "",
    val latencyMs: Long? = null,
    val detail: String = "",
    val testedAtEpochMs: Long = System.currentTimeMillis()
)

data class ObdCapabilityProfile(
    val adapterIdentity: String,
    val linkLevel: ObdLinkLevel,
    val detectedProtocol: String? = null,
    val results: List<ObdCapabilityResult> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = createdAtEpochMs
) {
    /** Only a responding ECU can authorize vehicle diagnostic features. */
    val ecuCommunicationVerified: Boolean
        get() = linkLevel == ObdLinkLevel.ECU_RESPONDING

    fun supportFor(testId: String): CapabilitySupport =
        results.lastOrNull { it.testId == testId }?.support ?: CapabilitySupport.NOT_TESTED

    fun canRunVehicleDiagnostic(testId: String): Boolean =
        ecuCommunicationVerified && supportFor(testId) == CapabilitySupport.SUPPORTED
}

/**
 * Conservative, read-only first-connection checks. These definitions do not
 * execute commands; the transport layer must enforce the timeout and parse
 * actual adapter/ECU responses before assigning a support result.
 */
object RecommendedObdCapabilityTests {
    val defaults: List<ObdCapabilityTest> = listOf(
        ObdCapabilityTest(
            id = "adapter-identification",
            title = "Identify ELM-compatible adapter",
            command = "ATI",
            category = ObdTestCategory.ADAPTER,
            recommended = true,
            timeoutMs = 3_000L
        ),
        ObdCapabilityTest(
            id = "current-protocol",
            title = "Read current protocol",
            command = "ATDP",
            category = ObdTestCategory.PROTOCOL,
            recommended = true,
            timeoutMs = 3_000L
        ),
        ObdCapabilityTest(
            id = "supported-pids-01-00",
            title = "Read supported standard PIDs",
            command = "0100",
            category = ObdTestCategory.ECU,
            recommended = true,
            timeoutMs = 5_000L
        ),
        ObdCapabilityTest(
            id = "confirmed-dtc",
            title = "Read confirmed diagnostic trouble codes",
            command = "03",
            category = ObdTestCategory.DIAGNOSTIC,
            recommended = true,
            timeoutMs = 5_000L
        )
    )
}
