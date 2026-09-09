package com.polar.androidcommunications.enpoints.ble.bluedroid.host

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanRecord
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import com.polar.androidcommunications.testrules.BleLoggerTestRule
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 * Unit tests for the bug-fix logic added to BDScanCallback:
 *
 *  1. onScanFailed does NOT call scanStartError immediately
 *  2. onScanFailed reschedules a scan after the initial 1 s backoff
 *  3. Consecutive failures double the backoff delay (exponential back-off)
 *  4. onScanResult resets the failure counter so the next failure starts at 1 s again
 *  5. An explicit stopScan after onScanFailed cancels the pending recovery
 *  6. Every startScan invocation (including ones followed by onScanFailed) counts
 *     toward the frequency-throttle pool
 *  7. scanStartError fires after MAX_SCAN_FAILURES consecutive failures
 */
@OptIn(ExperimentalCoroutinesApi::class)
internal class BDScanCallbackTest {

    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    // ── mocks ──────────────────────────────────────────────────────────────
    @MockK private lateinit var mockContext: Context
    @MockK private lateinit var mockBluetoothAdapter: BluetoothAdapter
    @MockK private lateinit var mockBtLeScanner: BluetoothLeScanner
    @MockK private lateinit var mockCallbackInterface: BDScanCallback.BDScanCallbackInterface
    @MockK private lateinit var mockScanResult: ScanResult
    @MockK private lateinit var mockScanRecord: ScanRecord
    @MockK private lateinit var mockBluetoothDevice: BluetoothDevice

    // ── virtual clock & SUT ───────────────────────────────────────────────
    private lateinit var testScope: TestScope
    private lateinit var sut: BDScanCallback

    /** Latest ScanCallback passed by the SUT to BluetoothLeScanner.startScan(). */
    private val capturedCallback = slot<ScanCallback>()

    /** Tracks how many times startScan() has been called on the mock scanner. */
    private var startScanCallCount = 0

    // ── set-up / tear-down ────────────────────────────────────────────────
    @Before
    fun setUp() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        testScope = TestScope()
        startScanCallCount = 0

        // Mock Android framework stubs used in BDScanCallback's init block.
        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(android.bluetooth.le.ScanFilter.Builder::class)
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().setServiceUuid(any()) } answers { self as android.bluetooth.le.ScanFilter.Builder }
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().setManufacturerData(any<Int>(), any()) } answers { self as android.bluetooth.le.ScanFilter.Builder }
        every { anyConstructed<android.bluetooth.le.ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(ScanSettings.Builder::class)
        every { anyConstructed<ScanSettings.Builder>().setScanMode(any()) } answers { self as ScanSettings.Builder }
        every { anyConstructed<ScanSettings.Builder>().build() } returns mockk(relaxed = true)

        every { mockBluetoothAdapter.isEnabled } returns true
        every { mockBluetoothAdapter.bluetoothLeScanner } returns mockBtLeScanner
        every { mockCallbackInterface.isScanningNeeded() } returns true
        every { mockCallbackInterface.scanStartError(any()) } just runs
        every { mockCallbackInterface.deviceDiscovered(any(), any(), any(), any()) } just runs
        every { mockBtLeScanner.stopScan(any<ScanCallback>()) } just runs
        every { mockBtLeScanner.startScan(any(), any(), capture(capturedCallback)) } answers {
            startScanCallCount++
        }

        // Minimal ScanResult stub for onScanResult tests.
        every { mockScanResult.scanRecord } returns mockScanRecord
        every { mockScanRecord.bytes } returns byteArrayOf()
        every { mockScanResult.device } returns mockBluetoothDevice
        every { mockScanResult.rssi } returns -70
        every { mockScanResult.isConnectable } returns true

        sut = BDScanCallback(
            context = mockContext,
            bluetoothAdapter = mockBluetoothAdapter,
            scanCallbackInterface = mockCallbackInterface,
            scope = testScope
        )
        // Disable opportunistic scan to avoid an infinite coroutine loop during tests.
        sut.opportunistic = false
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    // ── helpers ───────────────────────────────────────────────────────────
    // All helpers are TestScope extension functions so they must be called
    // from within a runTest { } block (single call per test).

    /**
     * Start scanning. clientAdded() is synchronous so no coroutine advancement
     * is needed; this helper exists for readability.
     */
    private fun TestScope.startScanningAndFlush() {
        sut.clientAdded()
    }

    /**
     * Fire one SCAN_FAILED_APPLICATION_REGISTRATION_FAILED (errorCode 2) on the
     * captured callback and run only the IMMEDIATE coroutine work (stop, set IDLE,
     * arm the backoff timer) WITHOUT advancing past the delay.
     */
    private fun TestScope.fireOnScanFailed(errorCode: Int = 2) {
        capturedCallback.captured.onScanFailed(errorCode)
        runCurrent()   // runs backoff job up to delay(backoffMs) – does NOT fire the delay
    }

    /**
     * Advance virtual time by [delayMs] ms and run ALL resulting coroutine work,
     * including any throttle-delay jobs that become ready after the advancement.
     */
    private fun TestScope.advancePastBackoffAndFlush(delayMs: Long) {
        advanceTimeBy(delayMs)
        advanceUntilIdle()
    }

    // ─────────────────────────────────────────────────────────────────────
    // Tests
    // ─────────────────────────────────────────────────────────────────────

    // ── 1. Basic onScanFailed side-effects ────────────────────────────────

    @Test
    fun `onScanFailed calls stopScan but does NOT notify scanStartError on first failure`() = testScope.runTest {
        startScanningAndFlush()
        fireOnScanFailed()

        verify(atLeast = 1) { mockBtLeScanner.stopScan(any<ScanCallback>()) }
        verify(exactly = 0) { mockCallbackInterface.scanStartError(any()) }
    }

    @Test
    fun `scanStartError called only after MAX_SCAN_FAILURES consecutive failures`() = testScope.runTest {
        // MAX_SCAN_FAILURES = 5: scanStartError must fire exactly once on the 5th failure,
        // and not before.
        startScanningAndFlush()

        // Trigger 4 failures — scanStartError must NOT fire yet.
        repeat(4) {
            fireOnScanFailed()
            advancePastBackoffAndFlush(65_000)  // advance past max backoff to trigger restart
        }
        verify(exactly = 0) { mockCallbackInterface.scanStartError(any()) }

        // 5th failure — scanStartError MUST fire exactly once.
        fireOnScanFailed()
        verify(exactly = 1) { mockCallbackInterface.scanStartError(any()) }
    }

    @Test
    fun `scanStartError counter resets after MAX_SCAN_FAILURES so next episode fires once`() = testScope.runTest {
        startScanningAndFlush()

        repeat(5) {
            fireOnScanFailed()
            advancePastBackoffAndFlush(65_000)
        }
        verify(exactly = 1) { mockCallbackInterface.scanStartError(any()) }

        // 4 more failures after the reset — should still not fire a second time.
        repeat(4) {
            fireOnScanFailed()
            advancePastBackoffAndFlush(65_000)
        }
        verify(exactly = 1) { mockCallbackInterface.scanStartError(any()) }  // still 1

        // 5th failure in the new episode — fires for the second time.
        fireOnScanFailed()
        verify(exactly = 2) { mockCallbackInterface.scanStartError(any()) }
    }

    // ── 2. Recovery restart after first back-off ──────────────────────────

    @Test
    fun `onScanFailed reschedules scan after initial 1s backoff`() = testScope.runTest {
        sut.clientAdded()
        val countBeforeFailure = startScanCallCount

        capturedCallback.captured.onScanFailed(2)
        // Run the backoff job up to delay(1000) – do NOT advance past it.
        runCurrent()

        // Before the back-off elapses: no new startScan.
        advanceTimeBy(500)
        runCurrent()
        assertEquals("No startScan expected before 1 s back-off", countBeforeFailure, startScanCallCount)

        // After the full 1 s: scan must restart.
        advanceTimeBy(600)  // total 1.1 s
        runCurrent()
        assertEquals("startScan expected after 1 s back-off", countBeforeFailure + 1, startScanCallCount)
    }

    // ── 3. Exponential back-off escalation ────────────────────────────────

    @Test
    fun `consecutive onScanFailed failures double the backoff delay`() = testScope.runTest {
        sut.clientAdded()

        // First failure → 1 s back-off; let it fire so the scan restarts.
        capturedCallback.captured.onScanFailed(2)
        advanceUntilIdle()   // runs through delay(1000), scan restarts
        val countAfterFirstRecovery = startScanCallCount

        // Second failure → 2 s back-off; arm the timer but do NOT advance past it.
        capturedCallback.captured.onScanFailed(2)
        runCurrent()

        // Only 1.1 s elapses — must NOT restart yet.
        advanceTimeBy(1100)
        runCurrent()
        assertEquals(
            "No startScan expected before 2 s back-off expires",
            countAfterFirstRecovery,
            startScanCallCount
        )

        // Remaining ~0.9 s elapses (total ~2.1 s) → scan restarts.
        advanceTimeBy(1000)
        runCurrent()
        assertEquals(
            "startScan expected after 2 s back-off",
            countAfterFirstRecovery + 1,
            startScanCallCount
        )
    }

    // ── 4. onScanResult resets the failure counter ────────────────────────

    @Test
    fun `onScanResult resets failure counter so next failure uses 1s backoff`() = testScope.runTest {
        sut.clientAdded()

        // First failure → scanFailureCount = 1 (would make next back-off 2 s).
        capturedCallback.captured.onScanFailed(2)
        advanceUntilIdle()
        advanceTimeBy(1100)
        advanceUntilIdle()

        // Successful scan result resets scanFailureCount to 0.
        capturedCallback.captured.onScanResult(0, mockScanResult)
        val countAfterResult = startScanCallCount

        // Second failure — counter was reset, so back-off should be 1 s again.
        capturedCallback.captured.onScanFailed(2)
        runCurrent()
        advanceTimeBy(1100)  // only advance 1.1 s — should be enough for reset 1 s back-off
        runCurrent()
        assertEquals(
            "startScan expected after reset 1 s back-off",
            countAfterResult + 1,
            startScanCallCount
        )
    }

    // ── 5. Explicit stopScan cancels pending recovery ─────────────────────

    @Test
    fun `stopScan after onScanFailed cancels the pending backoff recovery`() = testScope.runTest {
        sut.clientAdded()
        capturedCallback.captured.onScanFailed(2)
        runCurrent()  // arm the back-off timer; do NOT fire it yet

        val countAfterFailure = startScanCallCount

        // Admin explicitly stops scanning before the back-off fires.
        sut.stopScan()
        runCurrent()

        // Advance well past the back-off window — no recovery must happen.
        advanceTimeBy(5000)
        runCurrent()

        assertEquals(
            "No new startScan expected after explicit stopScan",
            countAfterFailure,
            startScanCallCount
        )
    }

    // ── 6. Frequency-throttle counts every startScan attempt ─────────────

    @Test
    fun `every startScan invocation counts toward frequency throttle`() = testScope.runTest {
        // Accumulate 4 pool entries via stop/start cycles.
        repeat(4) {
            sut.stopScan()
            sut.startScan()
            advanceUntilIdle()
        }
        assertEquals("Expected exactly 4 startScan calls after 4 cycles", 4, startScanCallCount)

        // 5th start: pool has 4 within-window entries → throttle path — deferred.
        val countBefore5th = startScanCallCount
        sut.stopScan()
        sut.startScan()
        // Use runCurrent() so the throttle delay is armed but NOT fired yet.
        runCurrent()

        assertEquals(
            "5th startScan must be deferred when scan pool has >3 entries in 30 s",
            countBefore5th,
            startScanCallCount
        )
    }
}

