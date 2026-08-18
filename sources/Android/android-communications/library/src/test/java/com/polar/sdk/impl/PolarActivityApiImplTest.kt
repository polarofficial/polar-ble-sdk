// Copyright (c) 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarDeviceNotFound
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.PolarSkinTemperatureResult
import com.polar.sdk.api.model.activity.Polar247HrSamplesData
import com.polar.sdk.api.model.activity.Polar247PPiSamplesData
import com.polar.sdk.api.model.activity.PolarActiveTimeData
import com.polar.sdk.api.model.activity.PolarActivitySamplesDayData
import com.polar.sdk.api.model.activity.PolarDailySummaryData
import com.polar.sdk.api.model.sleep.PolarNightlyRechargeData
import com.polar.sdk.impl.utils.CaloriesType
import com.polar.sdk.impl.utils.PolarActivityUtils
import com.polar.sdk.impl.utils.PolarAutomaticSamplesUtils
import com.polar.sdk.impl.utils.PolarNightlyRechargeUtils
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import com.polar.sdk.impl.utils.PolarSkinTemperatureUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkAll
import io.mockk.unmockkObject
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

internal class PolarActivityApiImplTest {

    private val deviceId = "A1B2C3"
    private val from = LocalDate.of(2024, 1, 1)
    private val to   = LocalDate.of(2024, 1, 3)
    private lateinit var context: Context

    @Before
    fun setUp() {
        mockkObject(PolarActivityUtils)
        mockkObject(PolarAutomaticSamplesUtils)
        mockkObject(PolarNightlyRechargeUtils)
        mockkObject(PolarSkinTemperatureUtils)

        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(null) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        val bluetoothAdapter = mockk<BluetoothAdapter>(relaxed = true)
        val bluetoothManager = mockk<BluetoothManager>(relaxed = true)
        every { bluetoothManager.adapter } returns bluetoothAdapter

        context = mockk(relaxed = true)
        every { context.applicationContext } returns context
        every { context.getSystemService(Context.BLUETOOTH_SERVICE) } returns bluetoothManager
        every { context.registerReceiver(any(), any<IntentFilter>()) } returns null
    }

    @After
    fun tearDown() {
        unmockkAll()
        BDBleApiImpl.clearInstance()
    }

    private fun mockBleConnection(deviceId: String): Pair<BlePsFtpClient, BleDeviceListener> {
        val client     = mockk<BlePsFtpClient>()
        val listener   = mockk<BleDeviceListener>()
        val session    = mockk<BleDeviceSession>()
        val sessions   = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() }    returns session
        every { session.advertisementContent }  returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType }       returns "Polar360"
        every { session.sessionState }          returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered }    returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)
        return Pair(client, listener)
    }

    private fun mockNoSession(): BleDeviceListener {
        val listener = mockk<BleDeviceListener>()
        val sessions = mockk<Set<BleDeviceSession>>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns false
        return listener
    }

    private fun mockNoClient(deviceId: String): BleDeviceListener {
        val listener   = mockk<BleDeviceListener>()
        val session    = mockk<BleDeviceSession>()
        val sessions   = mockk<Set<BleDeviceSession>>()
        val advContent = mockk<BleAdvertisementContent>()
        every { listener.deviceSessions() } returns sessions
        every { sessions.iterator().hasNext() } returns true
        every { sessions.iterator().next() }    returns session
        every { session.advertisementContent }  returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.polarDeviceType }       returns "Polar360"
        every { session.sessionState }          returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns null
        every { session.fetchClient(any()) }    returns null
        return listener
    }

    private fun mockPsFtpConnection(deviceId: String): Pair<BlePsFtpClient, BleDeviceSession> {
        val client = mockk<BlePsFtpClient>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(BlePsFtpUtils.RFC77_PFTP_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true
        every { client.getNotificationAtomicInteger(any()) } returns AtomicInteger(0)

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } returns session

        return Pair(client, session)
    }

    @Test
    fun `get247HrSamples returns list from utility`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        val expected = listOf(mockk<Polar247HrSamplesData>())
        coEvery { PolarAutomaticSamplesUtils.read247HrSamples(client, from, to) } returns expected
        val result = api.get247HrSamples(deviceId, from, to)
        assertEquals(expected, result)
    }

    @Test
    fun `get247HrSamples throws PolarDeviceNotFound when no session`() = runTest {
        val api = PolarActivityApiImpl(mockNoSession())
        try {
            api.get247HrSamples(deviceId, from, to)
            assert(false) { "Expected PolarDeviceNotFound" }
        } catch (e: PolarDeviceNotFound) { /* expected */ }
    }

    @Test
    fun `get247HrSamples throws PolarServiceNotAvailable when no client`() = runTest {
        val api = PolarActivityApiImpl(mockNoClient(deviceId))
        try {
            api.get247HrSamples(deviceId, from, to)
            assert(false) { "Expected PolarServiceNotAvailable" }
        } catch (e: PolarServiceNotAvailable) { /* expected */ }
    }


    @Test
    fun `get247PPiSamples returns list from utility`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        val expected = listOf(mockk<Polar247PPiSamplesData>())
        coEvery { PolarAutomaticSamplesUtils.read247PPiSamples(client, from, to) } returns expected
        val result = api.get247PPiSamples(deviceId, from, to)
        assertEquals(expected, result)
    }

    @Test
    fun `get247PPiSamples throws PolarServiceNotAvailable when no client`() = runTest {
        val api = PolarActivityApiImpl(mockNoClient(deviceId))
        try {
            api.get247PPiSamples(deviceId, from, to)
            assert(false) { "Expected PolarServiceNotAvailable" }
        } catch (e: PolarServiceNotAvailable) { /* expected */ }
    }


    @Test
    fun `getNightlyRecharge returns entries for dates that have data`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        val rechargeData = mockk<PolarNightlyRechargeData>()
        coEvery { PolarNightlyRechargeUtils.readNightlyRechargeData(client, LocalDate.of(2024, 1, 1)) } returns rechargeData
        coEvery { PolarNightlyRechargeUtils.readNightlyRechargeData(client, LocalDate.of(2024, 1, 2)) } returns null
        coEvery { PolarNightlyRechargeUtils.readNightlyRechargeData(client, LocalDate.of(2024, 1, 3)) } returns rechargeData
        val result = api.getNightlyRecharge(deviceId, from, to)
        assertEquals(2, result.size)
    }

    @Test
    fun `getNightlyRecharge returns empty list when all dates have no data`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        coEvery { PolarNightlyRechargeUtils.readNightlyRechargeData(client, any()) } returns null
        val result = api.getNightlyRecharge(deviceId, from, to)
        assertTrue(result.isEmpty())
    }


    @Test
    fun `getSteps returns one entry per date with correct step count`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        coEvery { PolarActivityUtils.readStepsFromDayDirectory(client, any()) } returns 1000
        val result = api.getSteps(deviceId, from, to)
        assertEquals(3, result.size)
        result.forEach { assertEquals(1000, it.steps) }
    }

    @Test
    fun `getSteps single-day range returns one entry with correct date and count`() = runTest {
        val singleDay = LocalDate.of(2024, 3, 15)
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        coEvery { PolarActivityUtils.readStepsFromDayDirectory(client, singleDay) } returns 5432
        val result = api.getSteps(deviceId, singleDay, singleDay)
        assertEquals(1, result.size)
        assertEquals(singleDay, result[0].date)
        assertEquals(5432, result[0].steps)
    }


    @Test
    fun `getDistance returns one entry per date with correct distance`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        coEvery { PolarActivityUtils.readDistanceFromDayDirectory(client, any()) } returns 3.14f
        val result = api.getDistance(deviceId, from, to)
        assertEquals(3, result.size)
        result.forEach { assertEquals(3.14f, it.distanceMeters) }
    }


    @Test
    fun `getCalories returns one entry per date for ACTIVITY type`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        coEvery { PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, any(), CaloriesType.ACTIVITY) } returns 500
        val result = api.getCalories(deviceId, from, to, CaloriesType.ACTIVITY)
        assertEquals(3, result.size)
        result.forEach { assertEquals(500, it.calories) }
    }

    @Test
    fun `getCalories passes TRAINING CaloriesType to utility`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        coEvery { PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, any(), CaloriesType.TRAINING) } returns 200
        val result = api.getCalories(deviceId, from, from, CaloriesType.TRAINING)
        assertEquals(1, result.size)
        assertEquals(200, result[0].calories)
    }


    @Test
    fun `getActiveTime returns one entry per date`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        val activeData = PolarActiveTimeData(date = from)
        coEvery { PolarActivityUtils.readActiveTimeFromDayDirectory(client, any()) } returns activeData
        val result = api.getActiveTime(deviceId, from, to)
        assertEquals(3, result.size)
    }


    @Test
    fun `getActivitySampleData returns one entry per date`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        val dayData = mockk<PolarActivitySamplesDayData>()
        coEvery { PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, any()) } returns dayData
        val result = api.getActivitySampleData(deviceId, from, to)
        assertEquals(3, result.size)
    }


    @Test
    fun `getDailySummaryData skips null results and returns only present data`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        val summaryData = mockk<PolarDailySummaryData>()
        coEvery { PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, LocalDate.of(2024, 1, 1)) } returns summaryData
        coEvery { PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, LocalDate.of(2024, 1, 2)) } returns null
        coEvery { PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, LocalDate.of(2024, 1, 3)) } returns summaryData
        val result = api.getDailySummaryData(deviceId, from, to)
        assertEquals(2, result.size)
    }


    @Test
    fun `getSkinTemperature returns entries only for dates with temperature data`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        val tempResult = mockk<PolarSkinTemperatureResult>()
        coEvery { PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(client, LocalDate.of(2024, 1, 1)) } returns tempResult
        coEvery { PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(client, LocalDate.of(2024, 1, 2)) } returns null
        coEvery { PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(client, LocalDate.of(2024, 1, 3)) } returns tempResult
        val result = api.getSkinTemperature(deviceId, from, to)
        assertEquals(2, result.size)
    }

    @Test
    fun `getSkinTemperature returns empty list when no data available`() = runTest {
        val (client, listener) = mockBleConnection(deviceId)
        val api = PolarActivityApiImpl(listener)
        coEvery { PolarSkinTemperatureUtils.readSkinTemperatureDataFromDayDirectory(client, any()) } returns null
        val result = api.getSkinTemperature(deviceId, from, to)
        assertTrue(result.isEmpty())
    }

    // ── get247HrSamples ───────────────────────────────────────────────────────

    @Test
    fun `get247HrSamples throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.get247HrSamples(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10))
            }
        }
    }

    @Test
    fun `get247HrSamples delegates to PolarAutomaticSamplesUtils and returns list`() = runTest {
        val deviceId = "E123456F"
        val fromDate = LocalDate.of(2024, 3, 1)
        val toDate = LocalDate.of(2024, 3, 7)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        mockkObject(PolarAutomaticSamplesUtils)
        coEvery { PolarAutomaticSamplesUtils.read247HrSamples(client, fromDate, toDate) } returns emptyList()
        try {
            val result = api.get247HrSamples(deviceId, fromDate, toDate)
            Assert.assertEquals(emptyList<Polar247HrSamplesData>(), result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarAutomaticSamplesUtils)
        }
    }

    @Test
    fun `get247HrSamples throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.get247HrSamples(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7))
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── get247PPiSamples ──────────────────────────────────────────────────────

    @Test
    fun `get247PPiSamples throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.get247PPiSamples(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10))
            }
        }
    }

    @Test
    fun `get247PPiSamples delegates to PolarAutomaticSamplesUtils and returns list`() = runTest {
        val deviceId = "E123456F"
        val fromDate = LocalDate.of(2024, 3, 1)
        val toDate = LocalDate.of(2024, 3, 7)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        mockkObject(PolarAutomaticSamplesUtils)
        coEvery { PolarAutomaticSamplesUtils.read247PPiSamples(client, fromDate, toDate) } returns emptyList()
        try {
            val result = api.get247PPiSamples(deviceId, fromDate, toDate)
            Assert.assertEquals(emptyList<Polar247PPiSamplesData>(), result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarAutomaticSamplesUtils)
        }
    }

    @Test
    fun `get247PPiSamples throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.get247PPiSamples(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7))
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── getSteps ──────────────────────────────────────────────────────────────

    @Test
    fun `getSteps throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.getSteps(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10))
            }
        }
    }

    @Test
    fun `getSteps delegates to PolarActivityUtils and returns steps for each date`() = runTest {
        val deviceId = "E123456F"
        val date = LocalDate.of(2024, 3, 1)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        mockkObject(PolarActivityUtils)
        coEvery { PolarActivityUtils.readStepsFromDayDirectory(client, date) } returns 500
        try {
            val result = api.getSteps(deviceId, date, date)
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(500, result[0].steps)
            Assert.assertEquals(date, result[0].date)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarActivityUtils)
        }
    }

    @Test
    fun `getSteps throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.getSteps(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7))
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── getCalories ───────────────────────────────────────────────────────────

    @Test
    fun `getCalories throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.getCalories(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10), caloriesType = CaloriesType.ACTIVITY)
            }
        }
    }

    @Test
    fun `getCalories delegates to PolarActivityUtils and returns calories for each date`() = runTest {
        val deviceId = "E123456F"
        val date = LocalDate.of(2024, 3, 1)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        mockkObject(PolarActivityUtils)
        coEvery { PolarActivityUtils.readSpecificCaloriesFromDayDirectory(client, date, CaloriesType.ACTIVITY) } returns 2000
        try {
            val result = api.getCalories(deviceId, date, date, CaloriesType.ACTIVITY)
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(2000, result[0].calories)
            Assert.assertEquals(date, result[0].date)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarActivityUtils)
        }
    }

    @Test
    fun `getCalories throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.getCalories(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7), CaloriesType.BMR)
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── getDistance ───────────────────────────────────────────────────────────

    @Test
    fun `getDistance throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.getDistance(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10))
            }
        }
    }

    @Test
    fun `getDistance delegates to PolarActivityUtils and returns distance for each date`() = runTest {
        val deviceId = "E123456F"
        val date = LocalDate.of(2024, 3, 1)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        mockkObject(PolarActivityUtils)
        coEvery { PolarActivityUtils.readDistanceFromDayDirectory(client, date) } returns 5000f
        try {
            val result = api.getDistance(deviceId, date, date)
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(5000f, result[0].distanceMeters)
            Assert.assertEquals(date, result[0].date)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarActivityUtils)
        }
    }

    @Test
    fun `getDistance throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.getDistance(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7))
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── getActiveTime ─────────────────────────────────────────────────────────

    @Test
    fun `getActiveTime delegates to PolarActivityUtils and returns active time for each date`() = runTest {
        val deviceId = "E123456F"
        val date = LocalDate.of(2024, 3, 1)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        val expectedData = PolarActiveTimeData(date)
        mockkObject(PolarActivityUtils)
        coEvery { PolarActivityUtils.readActiveTimeFromDayDirectory(client, date) } returns expectedData
        try {
            val result = api.getActiveTime(deviceId, date, date)
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(expectedData, result[0])
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarActivityUtils)
        }
    }

    @Test
    fun `getActiveTime throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getActiveTime(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7)) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── getActivitySampleData ─────────────────────────────────────────────────

    @Test
    fun `getActivitySampleData throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.getActivitySampleData(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10))
            }
        }
    }

    @Test
    fun `getActivitySampleData delegates to PolarActivityUtils and returns data for each date`() = runTest {
        val deviceId = "E123456F"
        val date = LocalDate.of(2024, 3, 1)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        val expectedData = PolarActivitySamplesDayData()
        mockkObject(PolarActivityUtils)
        coEvery { PolarActivityUtils.readActivitySamplesDataFromDayDirectory(client, date) } returns expectedData
        try {
            val result = api.getActivitySampleData(deviceId, date, date)
            Assert.assertEquals(1, result.size)
            Assert.assertEquals(expectedData, result[0])
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarActivityUtils)
        }
    }

    @Test
    fun `getActivitySampleData throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.getActivitySampleData(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7))
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }
}
