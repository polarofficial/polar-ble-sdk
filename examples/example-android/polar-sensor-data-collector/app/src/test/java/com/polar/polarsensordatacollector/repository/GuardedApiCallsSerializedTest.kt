package com.polar.polarsensordatacollector.repository

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class GuardedApiCallsSerializedTest {

    private val guardedCalls = listOf(
        "api.getCalories(",
        "api.getSkinTemperature(",
        "api.getActiveTime("
    )

    @Test
    fun repository_serializesGuardedCalls_whenSdkGuardsThem() {
        // Arrange
        val source = File("src/main/java/com/polar/polarsensordatacollector/repository/PolarDeviceRepository.kt")
            .readLines()

        // Act
        val unserialized = source.filter { line ->
            guardedCalls.any { line.contains(it) } && !line.contains("apiCallSerializer.serialize")
        }

        // Assert
        assertTrue("Calls without the serializer: $unserialized", unserialized.isEmpty())
    }
}
