package com.polar.sdk.api.model.utils

import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.sdk.impl.utils.PolarOfflineRecordingUtils
import com.polar.sdk.api.model.PolarOfflineRecordingEntry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import org.junit.Test
import java.nio.charset.StandardCharsets
import java.util.*

class PolarOfflineRecordingUtilsTest {

    private val mockClient = mockk<BlePsFtpClient>()

    @Test
    fun `listOfflineRecordingsV1 merges split REC files`() {
        val sampleEntries = listOf(
            Pair("/U/0/20250730/R/101010/ACC0.REC", 500120L),
            Pair("/U/0/20250730/R/101010/ACC1.REC", 500103L),
            Pair("/U/0/20250730/R/101010/ACC2.REC", 102325L),
            Pair("/U/0/20250730/R/101010/HR0.REC", 500000L),
            Pair("/U/0/20250730/R/101010/HR1.REC", 500050L),
            Pair("/U/0/20250730/R/101010/PPG0.REC", 300L)
        )

        val fetchRecursively: (BlePsFtpClient, String, (String) -> Boolean) -> Flowable<Pair<String, Long>> =
            { _, _, _ -> Flowable.fromIterable(sampleEntries) }

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        PolarOfflineRecordingUtils.listOfflineRecordingsV1(mockClient, fetchRecursively)
            .doOnNext { emitted.add(it) }
            .test()
            .awaitDone(1, java.util.concurrent.TimeUnit.SECONDS)

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L + 102325L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L + 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.size == 1)
        assert(ppgEntries[0].size == 300L)
        assert(ppgEntries[0].path.endsWith(".REC"))

        emitted.forEach { assert(it.date != null) }
    }

    @Test
    fun `listOfflineRecordingsV2 merges split REC files`() {
        val pmdTxtContent = """
            500120 /U/0/20250730/R/101010/ACC0.REC
            500103 /U/0/20250730/R/101010/ACC1.REC
            102325 /U/0/20250730/R/101010/ACC2.REC
            500000 /U/0/20250730/R/101010/HR0.REC
            500050 /U/0/20250730/R/101010/HR1.REC
            300 /U/0/20250730/R/101010/PPG0.REC
        """.trimIndent().toByteArray(StandardCharsets.UTF_8)

        val mockGetFile = mockk<(BlePsFtpClient, String) -> Single<ByteArray>>()
        every { mockGetFile(mockClient, "/PMDFILES.TXT") } returns Single.just(pmdTxtContent)

        val emitted = mutableListOf<PolarOfflineRecordingEntry>()
        PolarOfflineRecordingUtils.listOfflineRecordingsV2(mockClient, mockGetFile)
            .doOnSuccess { emitted.addAll(it) }
            .test()
            .awaitDone(1, java.util.concurrent.TimeUnit.SECONDS)

        val accEntries = emitted.filter { it.path.contains("ACC") }
        val hrEntries = emitted.filter { it.path.contains("HR") }
        val ppgEntries = emitted.filter { it.path.contains("PPG") }

        assert(accEntries.size == 1)
        assert(accEntries[0].size == 500120L + 500103L + 102325L)
        assert(accEntries[0].path.endsWith(".REC"))

        assert(hrEntries.size == 1)
        assert(hrEntries[0].size == 500000L + 500050L)
        assert(hrEntries[0].path.endsWith(".REC"))

        assert(ppgEntries.size == 1)
        assert(ppgEntries[0].size == 300L)
        assert(ppgEntries[0].path.endsWith(".REC"))

        emitted.forEach { assert(it.date != null) }

        verify { mockGetFile(mockClient, "/PMDFILES.TXT") }
    }
}