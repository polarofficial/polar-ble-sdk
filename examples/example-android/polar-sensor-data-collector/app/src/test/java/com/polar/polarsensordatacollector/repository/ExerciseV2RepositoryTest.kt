package com.polar.polarsensordatacollector.repository

import com.polar.sdk.api.PolarOfflineExerciseV2Api
import com.polar.sdk.api.model.PolarExerciseData
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.model.PolarExerciseSession
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ExerciseV2RepositoryTest {

    private val id = "dev1"
    private val api = mockk<BDBleApiImpl>(relaxed = true)
    private val repository = ExerciseV2Repository(api, DeviceApiCallSerializer())

    private fun entry(): PolarExerciseEntry = mockk(relaxed = true)

    private fun failure(block: suspend () -> Unit): Throwable =
        assertThrows(Throwable::class.java) { runBlocking { block() } }

    @Test
    fun `start offline exercise v 2 returns api result`() = runBlocking {
        // Arrange
        val result = PolarOfflineExerciseV2Api.OfflineExerciseStartResult(PolarOfflineExerciseV2Api.StartResult.SUCCESS, "/d")
        coEvery { api.startOfflineExerciseV2(id, PolarExerciseSession.SportProfile.RUNNING) } returns result

        // Act and Assert
        assertSame(result, repository.startOfflineExerciseV2(id, PolarExerciseSession.SportProfile.RUNNING))
    }

    @Test
    fun `start offline exercise v 2 rethrows when api fails`() {
        // Arrange
        coEvery { api.startOfflineExerciseV2(any(), any()) } throws IllegalStateException("start")

        // Act and Assert
        assertEquals("start", failure { repository.startOfflineExerciseV2(id, PolarExerciseSession.SportProfile.RUNNING) }.message)
    }

    @Test
    fun `stop offline exercise v 2 calls api`() = runBlocking {
        // Act
        repository.stopOfflineExerciseV2(id)

        // Assert
        coVerify { api.stopOfflineExerciseV2(id) }
    }

    @Test
    fun `stop offline exercise v 2 rethrows when api fails`() {
        // Arrange
        coEvery { api.stopOfflineExerciseV2(id) } throws IllegalStateException("stop")

        // Act and Assert
        assertEquals("stop", failure { repository.stopOfflineExerciseV2(id) }.message)
    }

    @Test
    fun `get offline exercise status v 2 returns running flag`() = runBlocking {
        // Arrange
        coEvery { api.getOfflineExerciseStatusV2(id) } returns true

        // Act and Assert
        assertTrue(repository.getOfflineExerciseStatusV2(id))
    }

    @Test
    fun `get offline exercise status v 2 maps busy error`() {
        // Arrange
        coEvery { api.getOfflineExerciseStatusV2(id) } throws IllegalStateException("error 202")

        // Act and Assert
        assertEquals("Exercise running, system busy (202)", failure { repository.getOfflineExerciseStatusV2(id) }.message)
    }

    @Test
    fun `get offline exercise status v 2 rethrows other errors`() {
        // Arrange
        coEvery { api.getOfflineExerciseStatusV2(id) } throws IllegalStateException("other")

        // Act and Assert
        assertEquals("other", failure { repository.getOfflineExerciseStatusV2(id) }.message)
    }

    @Test
    fun `list offline exercises v 2 emits entries`() = runBlocking {
        // Arrange
        val a = entry()
        every { api.listOfflineExercisesV2(id, "/") } returns flowOf(a)

        // Act
        val result = repository.listOfflineExercisesV2(id, "/").toList()

        // Assert
        assertEquals(listOf(a), result)
    }

    @Test
    fun `list offline exercises v 2 maps busy error`() {
        // Arrange
        every { api.listOfflineExercisesV2(id, "/") } returns flow { throw IllegalStateException("SYSTEM_BUSY") }

        // Act and Assert
        assertEquals(
            "Exercise running, system busy (202)",
            failure { repository.listOfflineExercisesV2(id, "/").toList() }.message
        )
    }

    @Test
    fun `list offline exercises v 2 rethrows other errors`() {
        // Arrange
        every { api.listOfflineExercisesV2(id, "/") } returns flow { throw IllegalStateException("other") }

        // Act and Assert
        assertEquals("other", failure { repository.listOfflineExercisesV2(id, "/").toList() }.message)
    }

    @Test
    fun `fetch offline exercise v 2 returns data`() = runBlocking {
        // Arrange
        val e = entry()
        val data = mockk<PolarExerciseData>(relaxed = true)
        coEvery { api.fetchOfflineExerciseV2(id, e) } returns data

        // Act and Assert
        assertSame(data, repository.fetchOfflineExerciseV2(id, e))
    }

    @Test
    fun `fetch offline exercise v 2 maps busy error`() {
        // Arrange
        val e = entry()
        coEvery { api.fetchOfflineExerciseV2(id, e) } throws IllegalStateException("BUSY")

        // Act and Assert
        assertEquals("Exercise running, system busy (202)", failure { repository.fetchOfflineExerciseV2(id, e) }.message)
    }

    @Test
    fun `fetch offline exercise v 2 rethrows other errors`() {
        // Arrange
        val e = entry()
        coEvery { api.fetchOfflineExerciseV2(id, e) } throws IllegalStateException("other")

        // Act and Assert
        assertEquals("other", failure { repository.fetchOfflineExerciseV2(id, e) }.message)
    }

    @Test
    fun `remove offline exercise v 2 calls api`() = runBlocking {
        // Arrange
        val e = entry()

        // Act
        repository.removeOfflineExerciseV2(id, e)

        // Assert
        coVerify { api.removeOfflineExerciseV2(id, e) }
    }

    @Test
    fun `remove offline exercise v 2 maps busy error`() {
        // Arrange
        val e = entry()
        coEvery { api.removeOfflineExerciseV2(id, e) } throws IllegalStateException("Exercise running")

        // Act and Assert
        assertEquals("Exercise running, system busy (202)", failure { repository.removeOfflineExerciseV2(id, e) }.message)
    }

    @Test
    fun `remove offline exercise v 2 rethrows other errors`() {
        // Arrange
        val e = entry()
        coEvery { api.removeOfflineExerciseV2(id, e) } throws IllegalStateException("other")

        // Act and Assert
        assertEquals("other", failure { repository.removeOfflineExerciseV2(id, e) }.message)
    }

    @Test
    fun `is offline exercise v 2 supported returns support flag`() = runBlocking {
        // Arrange
        coEvery { api.isOfflineExerciseV2Supported(id) } returns true
        assertTrue(repository.isOfflineExerciseV2Supported(id))

        // Arrange
        coEvery { api.isOfflineExerciseV2Supported(id) } returns false

        // Act and Assert
        assertFalse(repository.isOfflineExerciseV2Supported(id))
    }

    @Test
    fun `is offline exercise v 2 supported rethrows when api fails`() {
        // Arrange
        coEvery { api.isOfflineExerciseV2Supported(id) } throws IllegalStateException("cap")

        // Act and Assert
        assertEquals("cap", failure { repository.isOfflineExerciseV2Supported(id) }.message)
    }
}
