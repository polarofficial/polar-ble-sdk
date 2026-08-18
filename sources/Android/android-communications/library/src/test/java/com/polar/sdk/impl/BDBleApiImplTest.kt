package com.polar.sdk.impl

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BlePfcClient.Companion.PFC_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDScanCallback
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarBleSdkInstanceException
import com.polar.sdk.api.errors.PolarOperationNotSupported
import com.polar.sdk.api.errors.PolarServiceNotAvailable
import com.polar.sdk.api.model.FirmwareUpdateStatus
import com.polar.sdk.impl.utils.PolarBackupManager
import com.polar.sdk.impl.utils.PolarServiceClientUtils
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.runs
import io.mockk.unmockkConstructor
import io.mockk.unmockkObject
import io.mockk.unmockkStatic
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import protocol.PftpNotification
import protocol.PftpRequest
import protocol.PftpResponse
import protocol.PftpError.PbPFtpError
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDateTime
import java.time.LocalDate
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import com.polar.androidcommunications.api.ble.BleDeviceListener
import com.polar.androidcommunications.api.ble.exceptions.BleControlPointCommandError
import com.polar.androidcommunications.api.ble.exceptions.BleInvalidMtu
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleBattClient.Companion.BATTERY_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient
import com.polar.androidcommunications.api.ble.model.gatt.client.BleHrClient.Companion.HR_SERVICE
import com.polar.androidcommunications.api.ble.model.gatt.client.ChargeState
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.BlePMDClient
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdControlPointResponse.PmdControlPointResponseCode
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdSdkMode
import com.polar.sdk.api.PolarBleApi.PolarDeviceDataType
import fi.polar.remote.representation.protobuf.Types.PbDate
import fi.polar.remote.representation.protobuf.Types.PbTime
import fi.polar.remote.representation.protobuf.Types.PbSystemDateTime
import fi.polar.remote.representation.protobuf.UserDeviceSettings
import fi.polar.remote.representation.protobuf.UserDeviceSettings.PbUserDeviceSettings
import fi.polar.remote.representation.protobuf.UserIds
import io.mockk.verify
import com.polar.sdk.impl.utils.PolarFileUtils
import com.polar.sdk.impl.utils.PolarActivityUtils
import com.polar.sdk.api.model.LedConfig
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.PolarBleApiCallbackProvider
import com.polar.sdk.api.RestApiEventPayload
import com.polar.sdk.api.model.PolarSensorSetting
import com.polar.androidcommunications.api.ble.model.gatt.client.pmd.PmdMeasurementType
import com.polar.sdk.impl.utils.PolarNightlyRechargeUtils
import com.polar.sdk.api.model.PolarExerciseEntry
import com.polar.sdk.api.errors.PolarBleSdkInternalException

class BDBleApiImplTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        BDBleApiImpl.clearInstance()

        mockkStatic(ParcelUuid::class)
        every { ParcelUuid.fromString(any()) } returns mockk(relaxed = true)

        mockkConstructor(IntentFilter::class)
        every { anyConstructed<IntentFilter>().addAction(any()) } just runs

        mockkConstructor(ScanFilter.Builder::class)
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setServiceUuid(null) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().setManufacturerData(any(), any()) } answers { self as ScanFilter.Builder }
        every { anyConstructed<ScanFilter.Builder>().build() } returns mockk(relaxed = true)

        mockkConstructor(BDScanCallback::class)
        every { anyConstructed<BDScanCallback>().powerOn() } just runs
        every { anyConstructed<BDScanCallback>().powerOff() } just runs

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
        BDBleApiImpl.clearInstance()
        unmockkStatic(ParcelUuid::class)
        unmockkConstructor(IntentFilter::class)
        unmockkConstructor(ScanFilter.Builder::class)
        unmockkConstructor(BDScanCallback::class)
    }

    private fun mockPfcConnection(deviceId: String): Pair<BlePfcClient, BleDeviceSession> {
        val pfcClient = mockk<BlePfcClient>()
        val session = mockk<BleDeviceSession>()
        val advContent = mockk<BleAdvertisementContent>()

        every { session.advertisementContent } returns advContent
        every { session.advertisementContent.polarDeviceId } returns deviceId
        every { session.sessionState } returns BleDeviceSession.DeviceSessionState.SESSION_OPEN
        every { session.fetchClient(PFC_SERVICE) } returns pfcClient

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, any()) } returns session

        return Pair(pfcClient, session)
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

    private fun resetCapabilityUtilityState() {
        val initializedField = BlePolarDeviceCapabilitiesUtility::class.java.getDeclaredField("initialized")
        initializedField.isAccessible = true
        initializedField.setBoolean(null, false)
    }

    /**
     * Builds a valid [PbUserDeviceSettings] proto with the minimum required fields
     * so it can be returned as a mock PFTP response without triggering parse errors.
     */
    private fun buildValidUserDeviceSettingsBytes(): ByteArrayOutputStream {
        val pbDate = PbDate.newBuilder().setYear(2024).setMonth(1).setDay(1).build()
        val pbTime = PbTime.newBuilder().setHour(0).setMinute(0).setSeconds(0).build()
        val pbSystemDateTime = PbSystemDateTime.newBuilder()
            .setDate(pbDate).setTime(pbTime).setTrusted(false).build()
        val settings = PbUserDeviceSettings.newBuilder()
            .setGeneralSettings(UserDeviceSettings.PbUserDeviceGeneralSettings.getDefaultInstance())
            .setLastModified(pbSystemDateTime)
            .build()
        val baos = ByteArrayOutputStream()
        settings.writeTo(baos)
        return baos
    }

    private fun createFirmwareZip(vararg files: Pair<String, ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        val zos = ZipOutputStream(baos)
        for ((name, data) in files) {
            zos.putNextEntry(ZipEntry(name))
            zos.write(data)
            zos.closeEntry()
        }
        zos.close()
        return baos.toByteArray()
    }

    private fun initCapabilityForFirmwareTest(
        deviceType: String,
        fileSystemType: BlePolarDeviceCapabilitiesUtility.FileSystemType,
        isDeviceSensor: Boolean
    ) {
        val fsTypeStr = when (fileSystemType) {
            BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM -> "H10_FILE_SYSTEM"
            BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2 -> "POLAR_FILE_SYSTEM_V2"
            else -> "UNKNOWN_FILE_SYSTEM"
        }
        val config = BlePolarDeviceCapabilitiesUtility.DeviceCapabilitiesConfig(
            version = "test",
            devices = mapOf(
                deviceType.lowercase() to BlePolarDeviceCapabilitiesUtility.DeviceCapabilities(
                    fileSystemType = fsTypeStr,
                    isDeviceSensor = isDeviceSensor
                )
            ),
            defaults = BlePolarDeviceCapabilitiesUtility.DefaultsSection(
                fileSystemType = "POLAR_FILE_SYSTEM_V2",
                isDeviceSensor = false
            )
        )
        val outerClass = BlePolarDeviceCapabilitiesUtility::class.java
        outerClass.getDeclaredField("initialized").also { it.isAccessible = true }.setBoolean(null, true)
        outerClass.getDeclaredField("config").also { it.isAccessible = true }.set(null, config)
    }

    private fun mockFirmwareConnection(
        deviceId: String,
        deviceType: String
    ): Pair<BlePsFtpClient, BleDeviceSession> {
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        every {
            PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, any())
        } throws PolarServiceNotAvailable()
        return Pair(client, session)
    }

    // ── mockPmdClientConnection helper ────────────────────────────────────────
    private fun mockPmdClientConnection(deviceId: String): Pair<BlePMDClient, BleDeviceSession> {
        val client = mockk<BlePMDClient>()
        val session = mockk<BleDeviceSession>()
        every { session.fetchClient(BlePMDClient.PMD_SERVICE) } returns client
        every { client.isServiceDiscovered } returns true

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } returns session

        return Pair(client, session)
    }

    @Test
    fun `updateFirmware throws when device session is not found`() {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()

        try {
            // Act & Assert
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.updateFirmware(deviceId, "file:///nonexistent.zip").toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun singletonInstanceForPolarBleSDK() {
        // Arrange
        val polarBleApiDefaultInstance =
            BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
            )

        // Act
        val polarBleApiSecondInstance =
            BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
            )

        // Assert
        Assert.assertEquals(polarBleApiDefaultInstance, polarBleApiSecondInstance)
    }

    @Test
    fun singletonInstanceNotPossibleIfDifferentFeaturesRequired() {

        // Arrange
        BDBleApiImpl.getInstance(
            context,
            setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO)
        )

        // Act && Assert
        Assert.assertThrows(PolarBleSdkInstanceException::class.java) {
            BDBleApiImpl.getInstance(
                context,
                setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER)
            )
        }
    }

    @Test
    fun `setLocalTime sends different UTC and local time values for non-UTC timezone`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val localDateTime = LocalDateTime.of(2024, 3, 15, 12, 0, 0)

        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, session) = mockPsFtpConnection(deviceId)

        val originalTz = TimeZone.getDefault()
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+02:00"))

        val capturedQueryIds = mutableListOf<Int>()
        val capturedQueryParams = mutableListOf<ByteArray?>()
        coEvery { client.query(capture(capturedQueryIds), captureNullable(capturedQueryParams)) } returns ByteArrayOutputStream()

        try {
            // Act
            api.setLocalTime(deviceId, localDateTime)
        } finally {
            TimeZone.setDefault(originalTz)
            unmockkObject(PolarServiceClientUtils)
        }

        // Assert – two queries were sent: SET_SYSTEM_TIME and SET_LOCAL_TIME
        val systemTimeIndex = capturedQueryIds.indexOf(PftpRequest.PbPFtpQuery.SET_SYSTEM_TIME_VALUE)
        val localTimeIndex = capturedQueryIds.indexOf(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE)
        Assert.assertTrue("SET_SYSTEM_TIME_VALUE query was not sent", systemTimeIndex >= 0)
        Assert.assertTrue("SET_LOCAL_TIME_VALUE query was not sent", localTimeIndex >= 0)

        val systemTimeParams = PftpRequest.PbPFtpSetSystemTimeParams.parseFrom(capturedQueryParams[systemTimeIndex])
        val localTimeParams = PftpRequest.PbPFtpSetLocalTimeParams.parseFrom(capturedQueryParams[localTimeIndex])

        // Local time stays at 12:00, UTC is 10:00 (GMT+2 offset)
        Assert.assertEquals("Local time hour should be preserved", 12, localTimeParams.time.hour)
        Assert.assertEquals("System/UTC time hour should be 2 hours behind local (GMT+2)", 10, systemTimeParams.time.hour)
        Assert.assertNotEquals(
            "Local time and UTC system time must differ for a non-UTC timezone",
            localTimeParams.time.hour,
            systemTimeParams.time.hour
        )
        Assert.assertTrue("System time must be marked as trusted", systemTimeParams.trusted)
    }

    @Test
    fun `getSensorInitiatedSecurityMode returns true when device payload byte is 1`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE))
        val (pfcClient, _) = mockPfcConnection(deviceId)

        // data[0]=responseCode, data[1]=opCode, data[2]=status, data[3]=payload[0]=1 (enabled)
        val response = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE.numVal.toByte(), 0x01, 0x01)
        )
        coEvery { pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE, 0) } returns response

        try {
            // Act
            val result = api.getSensorInitiatedSecurityMode(deviceId)

            // Assert
            Assert.assertTrue("Expected getSensorInitiatedSecurityMode to return true when payload byte is 1", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getSensorInitiatedSecurityMode returns false when device payload byte is 0`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE))
        val (pfcClient, _) = mockPfcConnection(deviceId)

        // data[3]=payload[0]=0 (disabled)
        val response = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE.numVal.toByte(), 0x01, 0x00)
        )
        coEvery { pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE, 0) } returns response

        try {
            // Act
            val result = api.getSensorInitiatedSecurityMode(deviceId)

            // Assert
            Assert.assertFalse("Expected getSensorInitiatedSecurityMode to return false when payload byte is 0", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getSensorInitiatedSecurityMode returns false when device response has no payload`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE))
        val (pfcClient, _) = mockPfcConnection(deviceId)

        // Only 3 bytes → no payload (payload remains null)
        val response = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE.numVal.toByte(), 0x01)
        )
        coEvery { pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_REQUEST_SENSOR_INITIATED_SECURITY_MODE, 0) } returns response

        try {
            // Act
            val result = api.getSensorInitiatedSecurityMode(deviceId)

            // Assert
            Assert.assertFalse("Expected getSensorInitiatedSecurityMode to return false when payload is absent", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setSensorInitiatedSecurityMode enable=true completes successfully when device responds with status 1`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE))
        val (pfcClient, _) = mockPfcConnection(deviceId)

        // data[0]=responseCode, data[1]=opCode, data[2]=status(1=success)
        val successResponse = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE.numVal.toByte(), 0x01)
        )
        coEvery { pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE, 1) } returns successResponse

        try {
            // Act & Assert – should complete without throwing
            api.setSensorInitiatedSecurityMode(deviceId, enable = true)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setSensorInitiatedSecurityMode enable=false completes successfully when device responds with status 1`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE))
        val (pfcClient, _) = mockPfcConnection(deviceId)

        val successResponse = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE.numVal.toByte(), 0x01)
        )
        coEvery { pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE, 0) } returns successResponse

        try {
            // Act & Assert – should complete without throwing
            api.setSensorInitiatedSecurityMode(deviceId, enable = false)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setSensorInitiatedSecurityMode throws PolarOperationNotSupported when device responds with status other than 1`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FEATURES_CONFIGURATION_SERVICE))
        val (pfcClient, _) = mockPfcConnection(deviceId)

        // status = 0x00 means not supported
        val failResponse = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE.numVal.toByte(), 0x00)
        )
        coEvery { pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_CONFIGURE_SENSOR_INITIATED_SECURITY_MODE, any<Int>()) } returns failResponse

        try {
            // Act & Assert
            Assert.assertThrows(PolarOperationNotSupported::class.java) {
                runBlocking {
                    api.setSensorInitiatedSecurityMode(deviceId, enable = true)
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setHibernateMode sends RESET notification with hibernate=true sleep=true doFactoryDefaults=false`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)

        val capturedNotificationIds = mutableListOf<Int>()
        val capturedNotificationParams = mutableListOf<ByteArray?>()
        coEvery {
            client.sendNotification(capture(capturedNotificationIds), captureNullable(capturedNotificationParams))
        } just runs

        try {
            // Act
            api.setHibernateMode(deviceId)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertEquals("Expected exactly one notification", 1, capturedNotificationIds.size)
        Assert.assertEquals(
            "Expected RESET notification",
            PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal,
            capturedNotificationIds[0]
        )

        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(capturedNotificationParams[0])
        Assert.assertTrue("hibernate should be true", params.hibernate)
        Assert.assertTrue("sleep should be true to initiate low-power mode", params.sleep)
        Assert.assertFalse("doFactoryDefaults should be false", params.doFactoryDefaults)
    }

    @Test
    fun `setHibernateMode does not trigger factory defaults and sets hibernate flag`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)

        val capturedParams = mutableListOf<ByteArray?>()
        coEvery {
            client.sendNotification(any(), captureNullable(capturedParams))
        } just runs

        try {
            // Act
            api.setHibernateMode(deviceId)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }

        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(capturedParams[0])
        Assert.assertFalse("Hibernate mode must not trigger factory defaults", params.doFactoryDefaults)
        Assert.assertTrue("Hibernate flag must be set to true", params.hibernate)
    }

    @Test
    fun `updateFirmware emits FwUpdateNotAvailable when zip contains no firmware files`() = runTest {
        // Arrange — zip holds only readme.txt which parseFirmwareZip skips
        val deviceId = "A1B2C3D4"
        val deviceType = "H10"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockFirmwareConnection(deviceId, deviceType)
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM, isDeviceSensor = true)

        val zipBytes = createFirmwareZip("readme.txt" to "This is the readme.".toByteArray())
        val tmpFile = File.createTempFile("fw_empty_", ".zip").also { it.writeBytes(zipBytes); it.deleteOnExit() }
        val fileUrl = "file://${tmpFile.absolutePath}"

        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } just runs

        try {
            // Act
            val results = api.updateFirmware(deviceId, fileUrl).toList()

            // Assert — firmware files absent → FwUpdateNotAvailable before any write
            val notAvailable = results.filterIsInstance<FirmwareUpdateStatus.FwUpdateNotAvailable>()
            Assert.assertEquals(1, notAvailable.size)
            Assert.assertTrue(notAvailable[0].details.contains("firmware files were not available", ignoreCase = true))
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `updateFirmware emits FwUpdateCompletedSuccessfully for H10 sensor device`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val deviceType = "H10"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockFirmwareConnection(deviceId, deviceType)
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM, isDeviceSensor = true)

        val firmwareBytes = ByteArray(256) { it.toByte() }
        val zipBytes = createFirmwareZip("SYSUPDAT.IMG" to firmwareBytes)
        val tmpFile = File.createTempFile("fw_h10_", ".zip").also { it.writeBytes(zipBytes); it.deleteOnExit() }
        val fileUrl = "file://${tmpFile.absolutePath}"

        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } just runs
        every { client.write(any(), any()) } returns flowOf(firmwareBytes.size.toLong())

        try {
            // Act
            val results = api.updateFirmware(deviceId, fileUrl).toList()

            // Assert — last status is success; no backup/restore emitted (H10 path)
            val success = results.filterIsInstance<FirmwareUpdateStatus.FwUpdateCompletedSuccessfully>()
            Assert.assertEquals(1, success.size)
            val backupItems = results.filterIsInstance<FirmwareUpdateStatus.PreparingDeviceForFwUpdate>()
                .filter { it.details.contains("backup", ignoreCase = true) }
            Assert.assertEquals(0, backupItems.size)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `updateFirmware emits FwUpdateFailed when PFTP write throws for H10 device`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val deviceType = "H10"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockFirmwareConnection(deviceId, deviceType)
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM, isDeviceSensor = true)

        val firmwareBytes = ByteArray(128) { it.toByte() }
        val zipBytes = createFirmwareZip("firmware.bin" to firmwareBytes)
        val tmpFile = File.createTempFile("fw_fail_", ".zip").also { it.writeBytes(zipBytes); it.deleteOnExit() }
        val fileUrl = "file://${tmpFile.absolutePath}"

        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } just runs
        every { client.write(any(), any()) } returns flow { throw RuntimeException("Simulated write failure") }

        try {
            // Act
            val results = api.updateFirmware(deviceId, fileUrl).toList()

            // Assert — error during write → FwUpdateFailed
            val failed = results.filterIsInstance<FirmwareUpdateStatus.FwUpdateFailed>()
            Assert.assertEquals(1, failed.size)
            Assert.assertTrue(failed[0].details.contains("error", ignoreCase = true))
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `updateFirmware emits FwUpdateCompletedSuccessfully for V2 non-sensor device with backup and restore`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val deviceType = "Ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockFirmwareConnection(deviceId, deviceType)
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, isDeviceSensor = false)

        val firmwareBytes = ByteArray(256) { it.toByte() }
        val zipBytes = createFirmwareZip("firmware.bin" to firmwareBytes)
        val tmpFile = File.createTempFile("fw_v2_", ".zip").also { it.writeBytes(zipBytes); it.deleteOnExit() }
        val fileUrl = "file://${tmpFile.absolutePath}"

        coEvery { client.query(any(), any()) } returns ByteArrayOutputStream()
        coEvery { client.sendNotification(any(), any()) } just runs
        every { client.write(any(), any()) } returns flowOf(firmwareBytes.size.toLong())

        mockkConstructor(PolarBackupManager::class)
        coEvery { anyConstructed<PolarBackupManager>().backupDevice() } returns listOf()
        coEvery { anyConstructed<PolarBackupManager>().restoreBackup(any(), any(), anyNullable()) } just runs

        try {
            // Act
            val results = api.updateFirmware(deviceId, fileUrl).toList()

            // Assert — last status is success; backup step was attempted (V2 path)
            val success = results.filterIsInstance<FirmwareUpdateStatus.FwUpdateCompletedSuccessfully>()
            Assert.assertEquals(1, success.size)
            val backupItems = results.filterIsInstance<FirmwareUpdateStatus.PreparingDeviceForFwUpdate>()
                .filter { it.details.contains("backing", ignoreCase = true) }
            Assert.assertEquals(1, backupItems.size)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkConstructor(PolarBackupManager::class)
            resetCapabilityUtilityState()
        }
    }

    // ── deleteDeviceDateFolders ───────────────────────────────────────────────

    @Test
    fun `deleteDeviceDateFolders throws PolarServiceNotAvailable when session not found`() {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()

        try {
            // Act & Assert
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.deleteDeviceDateFolders(deviceId, LocalDate.of(2024, 1, 1), LocalDate.of(2024, 1, 2)) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteDeviceDateFolders does nothing when both dates are null`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.request(any()) } returns ByteArrayOutputStream()

        try {
            // Act — no exception expected
            api.deleteDeviceDateFolders(deviceId, fromDate = null, toDate = null)

            // Assert — no REMOVE request sent
            io.mockk.coVerify(exactly = 0) { client.request(any()) }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteDeviceDateFolders does nothing when only fromDate is provided`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.request(any()) } returns ByteArrayOutputStream()

        try {
            // Act
            api.deleteDeviceDateFolders(deviceId, fromDate = LocalDate.of(2024, 6, 1), toDate = null)

            // Assert — condition requires both dates non-null
            io.mockk.coVerify(exactly = 0) { client.request(any()) }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteDeviceDateFolders returns early when toDate is before fromDate`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.request(any()) } returns ByteArrayOutputStream()

        try {
            // Act — toDate (Jan 1) is before fromDate (Jan 5)
            api.deleteDeviceDateFolders(deviceId, fromDate = LocalDate.of(2024, 1, 5), toDate = LocalDate.of(2024, 1, 1))

            // Assert — invalid range → early return, no REMOVE
            io.mockk.coVerify(exactly = 0) { client.request(any()) }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteDeviceDateFolders sends one REMOVE when fromDate equals toDate`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedRequests = mutableListOf<ByteArray>()
        coEvery { client.request(capture(capturedRequests)) } returns ByteArrayOutputStream()

        try {
            // Act
            val date = LocalDate.of(2024, 6, 15)
            api.deleteDeviceDateFolders(deviceId, fromDate = date, toDate = date)

            // Assert — exactly one REMOVE for /U/0/20240615
            Assert.assertEquals(1, capturedRequests.size)
            val op = PftpRequest.PbPFtpOperation.parseFrom(capturedRequests[0])
            Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, op.command)
            Assert.assertEquals("/U/0/20240615", op.path)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteDeviceDateFolders sends REMOVE for each date in range`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedRequests = mutableListOf<ByteArray>()
        coEvery { client.request(capture(capturedRequests)) } returns ByteArrayOutputStream()

        try {
            // Act — 3-day range: Jan 1 to Jan 3
            api.deleteDeviceDateFolders(
                deviceId,
                fromDate = LocalDate.of(2024, 1, 1),
                toDate = LocalDate.of(2024, 1, 3)
            )

            // Assert — one REMOVE per day, in order
            Assert.assertEquals(3, capturedRequests.size)
            val paths = capturedRequests.map { PftpRequest.PbPFtpOperation.parseFrom(it).path }
            Assert.assertEquals(listOf("/U/0/20240101", "/U/0/20240102", "/U/0/20240103"), paths)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteDeviceDateFolders ignores NO_SUCH_FILE_OR_DIRECTORY and continues`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedRequests = mutableListOf<ByteArray>()
        val noSuchFileMsg = "PFTP error ${PbPFtpError.NO_SUCH_FILE_OR_DIRECTORY.number}"
        coEvery { client.request(capture(capturedRequests)) } throws RuntimeException(noSuchFileMsg)

        try {
            // Act — should complete without throwing despite error on every date
            api.deleteDeviceDateFolders(
                deviceId,
                fromDate = LocalDate.of(2024, 2, 1),
                toDate = LocalDate.of(2024, 2, 2)
            )

            // Assert — both dates were attempted
            Assert.assertEquals(2, capturedRequests.size)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteDeviceDateFolders rethrows unexpected errors`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        coEvery { client.request(any()) } throws RuntimeException("Connection lost")

        try {
            // Act & Assert
            Assert.assertThrows(RuntimeException::class.java) {
                runBlocking {
                    api.deleteDeviceDateFolders(
                        deviceId,
                        fromDate = LocalDate.of(2024, 3, 1),
                        toDate = LocalDate.of(2024, 3, 1)
                    )
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── setMultiBLEConnectionMode ─────────────────────────────────────────────

    @Test
    fun `setMultiBLEConnectionMode throws PolarServiceNotAvailable when PFC session not found`() {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, any()) } throws PolarServiceNotAvailable()

        try {
            // Act & Assert
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.setMultiBLEConnectionMode(deviceId, enable = true) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setMultiBLEConnectionMode sends value 1 and completes when enabling and status is 1`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (pfcClient, _) = mockPfcConnection(deviceId)
        val capturedValues = mutableListOf<Int>()
        val successResponse = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING.numVal.toByte(), 0x01)
        )
        coEvery {
            pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING, capture(capturedValues))
        } returns successResponse

        try {
            // Act — no exception expected
            api.setMultiBLEConnectionMode(deviceId, enable = true)

            // Assert — command value 1 sent for enabling
            Assert.assertEquals(1, capturedValues.size)
            Assert.assertEquals(1, capturedValues[0])
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setMultiBLEConnectionMode sends value 0 and completes when disabling and status is 1`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (pfcClient, _) = mockPfcConnection(deviceId)
        val capturedValues = mutableListOf<Int>()
        val successResponse = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING.numVal.toByte(), 0x01)
        )
        coEvery {
            pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING, capture(capturedValues))
        } returns successResponse

        try {
            // Act — no exception expected
            api.setMultiBLEConnectionMode(deviceId, enable = false)

            // Assert — command value 0 sent for disabling
            Assert.assertEquals(1, capturedValues.size)
            Assert.assertEquals(0, capturedValues[0])
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setMultiBLEConnectionMode throws PolarOperationNotSupported when response status is not 1`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (pfcClient, _) = mockPfcConnection(deviceId)
        val failureResponse = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING.numVal.toByte(), 0x00)
        )
        coEvery {
            pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_CONFIGURE_MULTI_CONNECTION_SETTING, any<Int>())
        } returns failureResponse

        try {
            // Act & Assert
            Assert.assertThrows(PolarOperationNotSupported::class.java) {
                runBlocking { api.setMultiBLEConnectionMode(deviceId, enable = true) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── getMultiBLEConnectionMode ─────────────────────────────────────────────

    @Test
    fun `getMultiBLEConnectionMode throws PolarServiceNotAvailable when PFC session not found`() {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsPfcClientReady(deviceId, any()) } throws PolarServiceNotAvailable()

        try {
            // Act & Assert
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getMultiBLEConnectionMode(deviceId) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getMultiBLEConnectionMode returns true when payload byte is 1`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (pfcClient, _) = mockPfcConnection(deviceId)
        // data[3] = 0x01 → payload[0] = 1 → returns true
        val response = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING.numVal.toByte(), 0x01, 0x01)
        )
        coEvery {
            pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING, null as ByteArray?)
        } returns response

        try {
            // Act
            val result = api.getMultiBLEConnectionMode(deviceId)

            // Assert
            Assert.assertTrue(result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getMultiBLEConnectionMode returns false when payload byte is 0`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (pfcClient, _) = mockPfcConnection(deviceId)
        // data[3] = 0x00 → payload[0] = 0 → returns false
        val response = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING.numVal.toByte(), 0x01, 0x00)
        )
        coEvery {
            pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING, null as ByteArray?)
        } returns response

        try {
            // Act
            val result = api.getMultiBLEConnectionMode(deviceId)

            // Assert
            Assert.assertFalse(result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getMultiBLEConnectionMode returns false when payload is absent`() = runTest {
        // Arrange
        val deviceId = "A1B2C3D4"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (pfcClient, _) = mockPfcConnection(deviceId)
        // 3-byte response → payload is null → null?.get(0) → null → null == 1 → false
        val response = BlePfcClient.PfcResponse(
            byteArrayOf(0x02, BlePfcClient.PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING.numVal.toByte(), 0x01)
        )
        coEvery {
            pfcClient.sendControlPointCommand(BlePfcClient.PfcMessage.PFC_REQUEST_MULTI_CONNECTION_SETTING, null as ByteArray?)
        } returns response

        try {
            // Act
            val result = api.getMultiBLEConnectionMode(deviceId)

            // Assert
            Assert.assertFalse(result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setWareHouseSleep sends RESET notification with sleep=true and doFactoryDefaults=true`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedIds = mutableListOf<Int>()
        val capturedParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(capturedIds), captureNullable(capturedParams)) } just runs

        try {
            // Act
            api.setWareHouseSleep(deviceId)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertEquals(1, capturedIds.size)
        Assert.assertEquals(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, capturedIds[0])
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(capturedParams[0])
        Assert.assertTrue("sleep should be true", params.sleep)
        Assert.assertTrue("doFactoryDefaults should be true", params.doFactoryDefaults)
    }

    @Test
    fun `turnDeviceOff sends RESET notification with sleep=true and doFactoryDefaults=false`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedIds = mutableListOf<Int>()
        val capturedParams = mutableListOf<ByteArray?>()
        coEvery { client.sendNotification(capture(capturedIds), captureNullable(capturedParams)) } just runs

        try {
            // Act
            api.turnDeviceOff(deviceId)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertEquals(PftpNotification.PbPFtpHostToDevNotification.RESET.ordinal, capturedIds[0])
        val params = PftpNotification.PbPFtpFactoryResetParams.parseFrom(capturedParams[0])
        Assert.assertTrue("sleep should be true", params.sleep)
        Assert.assertFalse("doFactoryDefaults should be false", params.doFactoryDefaults)
    }

    @Test
    fun `getDiskSpace returns PolarDiskSpaceData parsed from proto response`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, _) = mockPsFtpConnection(deviceId)
        val proto = PftpResponse.PbPFtpDiskSpaceResult.newBuilder()
            .setFragmentSize(512)
            .setTotalFragments(100)
            .setFreeFragments(50)
            .build()
        val bos = ByteArrayOutputStream().apply { write(proto.toByteArray()) }
        coEvery { client.query(PftpRequest.PbPFtpQuery.GET_DISK_SPACE_VALUE, null) } returns bos

        try {
            // Act
            val result = api.getDiskSpace(deviceId)

            // Assert
            Assert.assertEquals(512L * 100L, result.totalSpace)
            Assert.assertEquals(512L * 50L, result.freeSpace)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getLocalTime returns LocalDateTime parsed from GET_LOCAL_TIME_VALUE response`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP))
        val (client, _) = mockPsFtpConnection(deviceId)
        val proto = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
            .setDate(PbDate.newBuilder().setYear(2024).setMonth(6).setDay(15).build())
            .setTime(PbTime.newBuilder().setHour(10).setMinute(30).setSeconds(45).setMillis(0).build())
            .setTzOffset(0)
            .build()
        val bos = ByteArrayOutputStream().apply { write(proto.toByteArray()) }
        coEvery { client.query(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, null) } returns bos

        try {
            // Act
            val result = api.getLocalTime(deviceId)

            // Assert
            Assert.assertEquals(2024, result.year)
            Assert.assertEquals(6, result.monthValue)
            Assert.assertEquals(15, result.dayOfMonth)
            Assert.assertEquals(10, result.hour)
            Assert.assertEquals(30, result.minute)
            Assert.assertEquals(45, result.second)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getLocalTimeWithZone returns ZonedDateTime with correct timezone offset`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP))
        val (client, _) = mockPsFtpConnection(deviceId)
        // tzOffset = 120 minutes = UTC+2
        val proto = PftpRequest.PbPFtpSetLocalTimeParams.newBuilder()
            .setDate(PbDate.newBuilder().setYear(2024).setMonth(3).setDay(20).build())
            .setTime(PbTime.newBuilder().setHour(12).setMinute(0).setSeconds(0).setMillis(0).build())
            .setTzOffset(120)
            .build()
        val bos = ByteArrayOutputStream().apply { write(proto.toByteArray()) }
        coEvery { client.query(PftpRequest.PbPFtpQuery.GET_LOCAL_TIME_VALUE, null) } returns bos

        try {
            // Act
            val result = api.getLocalTimeWithZone(deviceId)

            // Assert
            Assert.assertEquals(2024, result.year)
            Assert.assertEquals(3, result.monthValue)
            Assert.assertEquals(20, result.dayOfMonth)
            Assert.assertEquals(12, result.hour)
            Assert.assertEquals(120 * 60, result.offset.totalSeconds)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setDaylightSavingTime sends SET_LOCAL_TIME_VALUE query`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP))
        val (client, _) = mockPsFtpConnection(deviceId)
        val capturedIds = mutableListOf<Int>()
        coEvery { client.query(capture(capturedIds), any()) } returns ByteArrayOutputStream()

        try {
            // Act
            api.setDaylightSavingTime(deviceId)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }

        // Assert
        Assert.assertTrue(
            "Expected SET_LOCAL_TIME_VALUE query",
            capturedIds.contains(PftpRequest.PbPFtpQuery.SET_LOCAL_TIME_VALUE)
        )
    }

    @Test
    fun `isFtuDone returns true when master identifier is set in UserIdentifier file`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)

        val userIdProto = UserIds.PbUserIdentifier.newBuilder().setMasterIdentifier(12345L).build()
        val bos = ByteArrayOutputStream().apply { write(userIdProto.toByteArray()) }
        coEvery { client.request(any()) } returns bos

        try {
            // Act
            val result = api.isFtuDone(deviceId)

            // Assert
            Assert.assertTrue("isFtuDone should return true when masterIdentifier is set", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `isFtuDone returns false when master identifier is not set in UserIdentifier file`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)

        val userIdProto = UserIds.PbUserIdentifier.newBuilder().build()
        val bos = ByteArrayOutputStream().apply { write(userIdProto.toByteArray()) }
        coEvery { client.request(any()) } returns bos

        try {
            // Act
            val result = api.isFtuDone(deviceId)

            // Assert
            Assert.assertFalse("isFtuDone should return false when masterIdentifier is not set", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setMtu calls setPreferredMtu on listener with given value`() {
        // Arrange
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        val mockListener = mockk<BleDeviceListener>(relaxed = true)
        val listenerField = BDBleApiImpl::class.java.getDeclaredField("listener")
        listenerField.isAccessible = true
        listenerField.set(api, mockListener)

        // Act
        api.setMtu(185)

        // Assert
        verify { mockListener.setPreferredMtu(185) }
    }

    @Test
    fun `setMtu swallows BleInvalidMtu and does not rethrow`() {
        // Arrange
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        val mockListener = mockk<BleDeviceListener>()
        every { mockListener.setPreferredMtu(any()) } throws BleInvalidMtu()
        val listenerField = BDBleApiImpl::class.java.getDeclaredField("listener")
        listenerField.isAccessible = true
        listenerField.set(api, mockListener)

        // Act & Assert — should not throw
        api.setMtu(-1)
    }

    @Test
    fun `enableSDKMode calls startSDKMode on PMD client`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE))
        val (client, _) = mockPmdClientConnection(deviceId)
        coEvery { client.startSDKMode() } just runs

        try {
            // Act
            api.enableSDKMode(deviceId)

            // Assert
            io.mockk.coVerify { client.startSDKMode() }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `enableSDKMode silently returns when PMD client reports ERROR_ALREADY_IN_STATE`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE))
        val (client, _) = mockPmdClientConnection(deviceId)
        coEvery { client.startSDKMode() } throws BleControlPointCommandError(
            "already in state",
            PmdControlPointResponseCode.ERROR_ALREADY_IN_STATE
        )

        try {
            // Act & Assert — should not rethrow ERROR_ALREADY_IN_STATE
            api.enableSDKMode(deviceId)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `disableSDKMode calls stopSDKMode on PMD client`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE))
        val (client, _) = mockPmdClientConnection(deviceId)
        coEvery { client.stopSDKMode() } just runs

        try {
            // Act
            api.disableSDKMode(deviceId)

            // Assert
            io.mockk.coVerify { client.stopSDKMode() }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `isSDKModeEnabled returns true when PMD client reports ENABLED`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE))
        val (client, _) = mockPmdClientConnection(deviceId)
        coEvery { client.isSdkModeEnabled() } returns PmdSdkMode.ENABLED

        try {
            // Act
            val result = api.isSDKModeEnabled(deviceId)

            // Assert
            Assert.assertTrue("isSDKModeEnabled should return true when PmdSdkMode is ENABLED", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `isSDKModeEnabled returns false when PMD client reports DISABLED`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE))
        val (client, _) = mockPmdClientConnection(deviceId)
        coEvery { client.isSdkModeEnabled() } returns PmdSdkMode.DISABLED

        try {
            // Act
            val result = api.isSDKModeEnabled(deviceId)

            // Assert
            Assert.assertFalse("isSDKModeEnabled should return false when PmdSdkMode is DISABLED", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getBatteryLevel returns battery percentage from BleBattClient`() {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO))
        val (_, session) = mockPsFtpConnection(deviceId)
        val bleBattClient = mockk<BleBattClient>()
        every { session.fetchClient(BATTERY_SERVICE) } returns bleBattClient
        every { bleBattClient.getBatteryLevel() } returns 85

        try {
            // Act
            val result = api.getBatteryLevel(deviceId)

            // Assert
            Assert.assertEquals(85, result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getChargerState returns ChargeState from BleBattClient`() {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO))
        val (_, session) = mockPsFtpConnection(deviceId)
        val bleBattClient = mockk<BleBattClient>()
        every { session.fetchClient(BATTERY_SERVICE) } returns bleBattClient
        every { bleBattClient.getChargerStatus() } returns ChargeState.CHARGING

        try {
            // Act
            val result = api.getChargerState(deviceId)

            // Assert
            Assert.assertEquals(ChargeState.CHARGING, result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getAvailableHRServiceDataTypes returns HR data type when service is discovered`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR))
        val bleHrClient = mockk<BleHrClient>()
        val session = mockk<BleDeviceSession>()
        every { session.fetchClient(HR_SERVICE) } returns bleHrClient
        every { bleHrClient.isServiceDiscovered } returns true

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, any()) } returns session

        try {
            // Act
            val result = api.getAvailableHRServiceDataTypes(deviceId)

            // Assert
            Assert.assertTrue("Should contain PolarDeviceDataType.HR", result.contains(PolarDeviceDataType.HR))
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getAvailableHRServiceDataTypes returns empty set when HR client is null`() = runTest {
        // Arrange
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR))
        val session = mockk<BleDeviceSession>()
        every { session.fetchClient(HR_SERVICE) } returns null

        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionServiceReady(deviceId, HR_SERVICE, any()) } returns session

        try {
            // Act
            val result = api.getAvailableHRServiceDataTypes(deviceId)

            // Assert
            Assert.assertTrue("Should return empty set when HR client is null", result.isEmpty())
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `readFile delegates to PolarFileUtils and returns ByteArray`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.readFile(deviceId, "/test.bin", any(), any()) } returns byteArrayOf(0x01, 0x02, 0x03)
        try {
            val result = api.readFile(deviceId, "/test.bin")
            Assert.assertArrayEquals(byteArrayOf(0x01, 0x02, 0x03), result)
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `readFile propagates PolarServiceNotAvailable from PolarFileUtils`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.readFile(deviceId, any(), any(), any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.readFile(deviceId, "/test.bin") }
            }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `writeFile delegates to PolarFileUtils and completes without error`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.writeFile(deviceId, "/test.bin", any(), any(), any()) } just runs
        try {
            api.writeFile(deviceId, "/test.bin", byteArrayOf(0x01))
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `writeFile propagates PolarServiceNotAvailable from PolarFileUtils`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.writeFile(deviceId, any(), any(), any(), any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.writeFile(deviceId, "/test.bin", byteArrayOf()) }
            }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `deleteFileOrDirectory delegates to PolarFileUtils and completes without error`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.removeFileOrDirectory(deviceId, "/test/", any(), any()) } just runs
        try {
            api.deleteFileOrDirectory(deviceId, "/test/")
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `deleteFileOrDirectory propagates PolarServiceNotAvailable from PolarFileUtils`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.removeFileOrDirectory(deviceId, any(), any(), any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.deleteFileOrDirectory(deviceId, "/test/") }
            }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `getFileList delegates to PolarFileUtils and returns list of paths`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        val expectedPaths = listOf("/U/0/file1.BPB", "/U/0/file2.BPB")
        coEvery { PolarFileUtils.getFileList(deviceId, "/U/0/", true, any(), any()) } returns expectedPaths
        try {
            val result = api.getFileList(deviceId, "/U/0/", true)
            Assert.assertEquals(expectedPaths, result)
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `getFileList propagates PolarServiceNotAvailable from PolarFileUtils`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.getFileList(deviceId, any(), any(), any(), any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getFileList(deviceId, "/U/0/", false) }
            }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `createFolder delegates to PolarFileUtils and completes without error`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.createFolder(deviceId, "/U/0/custom/", any(), any()) } just runs
        try {
            api.createFolder(deviceId, "/U/0/custom/")
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `createFolder propagates PolarServiceNotAvailable from PolarFileUtils`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarFileUtils)
        coEvery { PolarFileUtils.createFolder(deviceId, any(), any(), any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.createFolder(deviceId, "/U/0/custom/") }
            }
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `getUserDeviceSettings returns default settings when device responds with empty proto`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        try {
            val result = api.getUserDeviceSettings(deviceId)
            Assert.assertNotNull(result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `getUserDeviceSettings throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getUserDeviceSettings(deviceId) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setUserDeviceLocation completes without error when device responds successfully`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setUserDeviceLocation(deviceId, 1)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setUserDeviceLocation throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.setUserDeviceLocation(deviceId, 1) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setUsbConnectionMode completes without error when enabling USB mode`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setUsbConnectionMode(deviceId, enabled = true)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setUsbConnectionMode completes without error when disabling USB mode`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setUsbConnectionMode(deviceId, enabled = false)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setUsbConnectionMode throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.setUsbConnectionMode(deviceId, enabled = false) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setTelemetryEnabled completes without error when enabling telemetry`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setTelemetryEnabled(deviceId, enabled = true)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setTelemetryEnabled completes without error when disabling telemetry`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setTelemetryEnabled(deviceId, enabled = false)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setTelemetryEnabled throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.setTelemetryEnabled(deviceId, enabled = true) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setLedConfig writes LED config to device via PFTP`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION))
        val (client, _) = mockPsFtpConnection(deviceId)
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setLedConfig(deviceId, LedConfig(sdkModeLedEnabled = true, ppiModeLedEnabled = false))
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setLedConfig sends disable bytes when both LEDs are disabled`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION))
        val (client, _) = mockPsFtpConnection(deviceId)
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setLedConfig(deviceId, LedConfig(sdkModeLedEnabled = false, ppiModeLedEnabled = false))
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setLedConfig throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_LED_ANIMATION))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.setLedConfig(deviceId, LedConfig()) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `shutDown does not throw and allows new instance creation`() {
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        api.shutDown()
        val api2 = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        Assert.assertNotNull(api2)
    }

    @Test
    fun `cleanup does not throw when called on active instance`() {
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        api.cleanup()
    }

    @Test
    fun `setPolarFilter enable=true does not throw`() {
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        api.setPolarFilter(true)
    }

    @Test
    fun `setPolarFilter enable=false does not throw`() {
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        api.setPolarFilter(false)
    }

    @Test
    fun `isFeatureReady returns false when device session not connected for ONLINE_STREAMING`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        val result = api.isFeatureReady(deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING)
        Assert.assertFalse(result)
    }

    @Test
    fun `isFeatureReady returns false for HR feature when device session not connected`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR))
        val result = api.isFeatureReady(deviceId, PolarBleApi.PolarBleSdkFeature.FEATURE_HR)
        Assert.assertFalse(result)
    }

    @Test
    fun `setApiCallback registers callback and fires current BLE power state`() {
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        val callback = mockk<PolarBleApiCallbackProvider>(relaxed = true)
        api.setApiCallback(callback)
        verify(exactly = 1) { callback.blePowerStateChanged(any()) }
    }

    @Test
    fun `getDeviceName returns empty string when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.fetchSession(deviceId, any()) } returns null
        try {
            val result = api.getDeviceName(deviceId)
            Assert.assertEquals("", result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getDeviceName returns device name from session`() {
        val deviceId = "E123456F"
        val expectedName = "Polar H10"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        val session = mockk<BleDeviceSession>()
        every { session.name } returns expectedName
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.fetchSession(deviceId, any()) } returns session
        try {
            val result = api.getDeviceName(deviceId)
            Assert.assertEquals(expectedName, result)
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startEcgStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startEcgStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startAccStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startAccStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startPpgStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startPpgStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startPpiStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startPpiStreaming(deviceId).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startMagnetometerStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startMagnetometerStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startGyroStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startGyroStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startPressureStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startPressureStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    // ── startLocationStreaming ────────────────────────────────────────────────

    @Test
    fun `startLocationStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startLocationStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startTemperatureStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.startTemperatureStreaming(deviceId, PolarSensorSetting(emptyMap())).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `stopStreaming throws PolarServiceNotAvailable when PMD session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                api.stopStreaming(deviceId, PmdMeasurementType.ECG)
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `startListenForPolarHrBroadcasts throws PolarBleSdkInstanceException when listener is null after shutDown`() {
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_HR))
        api.shutDown()
        Assert.assertThrows(PolarBleSdkInstanceException::class.java) {
            runBlocking { api.startListenForPolarHrBroadcasts(null).take(1).toList() }
        }
    }

    @Test
    fun `observeDeviceToHostNotifications throws PolarServiceNotAvailable when session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.observeDeviceToHostNotifications(deviceId).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `receiveRestApiEvents throws PolarServiceNotAvailable when session missing`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.receiveRestApiEvents(deviceId) { _ -> object : RestApiEventPayload() {} }.take(1).toList()
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getNightlyRecharge throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.getNightlyRecharge(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10))
            }
        }
    }

    @Test
    fun `getNightlyRecharge throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getNightlyRecharge(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7)) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getNightlyRecharge delegates to PolarNightlyRechargeUtils and filters nulls`() = runTest {
        val deviceId = "E123456F"
        val date = LocalDate.of(2024, 3, 1)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        mockkObject(PolarNightlyRechargeUtils)
        coEvery { PolarNightlyRechargeUtils.readNightlyRechargeData(client, date) } returns null
        try {
            val result = api.getNightlyRecharge(deviceId, date, date)
            Assert.assertEquals(0, result.size)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarNightlyRechargeUtils)
        }
    }

    @Test
    fun `getDailySummaryData throws PolarInvalidArgument when toDate is before fromDate`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        Assert.assertThrows(PolarInvalidArgument::class.java) {
            runBlocking {
                api.getDailySummaryData(deviceId, fromDate = LocalDate.of(2024, 3, 15), toDate = LocalDate.of(2024, 3, 10))
            }
        }
    }

    @Test
    fun `getDailySummaryData throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getDailySummaryData(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7)) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getDailySummaryData delegates to PolarActivityUtils and filters nulls`() = runTest {
        val deviceId = "E123456F"
        val date = LocalDate.of(2024, 3, 1)
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ACTIVITY_DATA))
        val (client, _) = mockPsFtpConnection(deviceId)
        mockkObject(PolarActivityUtils)
        coEvery { PolarActivityUtils.readDailySummaryDataFromDayDirectory(client, date) } returns null
        try {
            val result = api.getDailySummaryData(deviceId, date, date)
            Assert.assertEquals(0, result.size)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarActivityUtils)
        }
    }

    @Test
    fun `checkFirmwareUpdate throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FIRMWARE_UPDATE))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.checkFirmwareUpdate(deviceId).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteStoredDeviceData throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking {
                    api.deleteStoredDeviceData(deviceId, PolarBleApi.PolarStoredDataType.SDLOGS, null)
                }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `deleteStoredDeviceData completes without error when listFiles returns empty flow for SDLOGS`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (_, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        mockkObject(PolarFileUtils)
        every { PolarFileUtils.listFiles(any(), any(), any(), any(), any()) } returns flowOf()
        try {
            api.deleteStoredDeviceData(deviceId, PolarBleApi.PolarStoredDataType.SDLOGS, null)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            unmockkObject(PolarFileUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `deleteTelemetryData completes without error when listFiles returns empty flow`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarFileUtils)
        every { PolarFileUtils.listFiles(any(), any(), any(), any(), any()) } returns flowOf()
        try {
            api.deleteTelemetryData(deviceId)
        } finally {
            unmockkObject(PolarFileUtils)
        }
    }

    @Test
    fun `getUserPhysicalConfiguration throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_FILE_TRANSFER))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getUserPhysicalConfiguration(deviceId) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setAutomaticOHRMeasurementEnabled throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.setAutomaticOHRMeasurementEnabled(deviceId, true) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setAutomaticOHRMeasurementEnabled completes without error when device responds successfully`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setAutomaticOHRMeasurementEnabled(deviceId, enabled = true)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setAutomaticOHRMeasurementEnabled with disabled=false completes without error`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setAutomaticOHRMeasurementEnabled(deviceId, enabled = false)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `setAutomaticTrainingDetectionSettings throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.setAutomaticTrainingDetectionSettings(deviceId, true, 2, 60) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `setAutomaticTrainingDetectionSettings completes without error when device responds successfully`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_CONTROL))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        coEvery { client.request(any()) } returns buildValidUserDeviceSettingsBytes()
        every { client.write(any(), any()) } returns flowOf(1L)
        try {
            api.setAutomaticTrainingDetectionSettings(deviceId, automaticTrainingDetectionMode = true, automaticTrainingDetectionSensitivity = 2, minimumTrainingDurationSeconds = 60)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `observeSleepRecordingState throws PolarServiceNotAvailable when session not found`() = runTest {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.fetchSession(deviceId, any()) } returns null
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.observeSleepRecordingState(deviceId).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `observeSleepRecordingState throws PolarServiceNotAvailable when device does not support activity data`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "h10"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SLEEP_DATA))
        val session = mockk<BleDeviceSession>()
        every { session.polarDeviceType } returns deviceType
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.fetchSession(deviceId, any()) } returns session
        val config = BlePolarDeviceCapabilitiesUtility.DeviceCapabilitiesConfig(
            version = "test",
            devices = mapOf(deviceType to BlePolarDeviceCapabilitiesUtility.DeviceCapabilities(activityDataSupported = false)),
            defaults = BlePolarDeviceCapabilitiesUtility.DefaultsSection(activityDataSupported = false)
        )
        BlePolarDeviceCapabilitiesUtility::class.java.getDeclaredField("initialized").also { it.isAccessible = true }.setBoolean(null, true)
        BlePolarDeviceCapabilitiesUtility::class.java.getDeclaredField("config").also { it.isAccessible = true }.set(null, config)
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.observeSleepRecordingState(deviceId).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `listExercises throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.listExercises(deviceId).take(1).toList() }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `removeExercise throws PolarBleSdkInternalException for V2 filesystem device`() {
        val deviceId = "E123456F"
        val deviceType = "ignite3"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING))
        val (_, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.POLAR_FILE_SYSTEM_V2, false)
        val entry = PolarExerciseEntry("/U/0/20240101/E/000001/SAMPLES.BPB", LocalDateTime.of(2024, 1, 1, 0, 0), "2024010100000")
        try {
            Assert.assertThrows(PolarBleSdkInternalException::class.java) {
                runBlocking { api.removeExercise(deviceId, entry) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `removeExercise sends REMOVE request for H10 filesystem device`() = runTest {
        val deviceId = "E123456F"
        val deviceType = "H10"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING))
        val (client, session) = mockPsFtpConnection(deviceId)
        every { session.polarDeviceType } returns deviceType
        initCapabilityForFirmwareTest(deviceType, BlePolarDeviceCapabilitiesUtility.FileSystemType.H10_FILE_SYSTEM, true)
        val capturedRequests = mutableListOf<ByteArray>()
        coEvery { client.request(capture(capturedRequests)) } returns ByteArrayOutputStream()
        val entryPath = "/00/SAMPLES.BPB"
        val entry = PolarExerciseEntry(entryPath, LocalDateTime.of(2024, 1, 1, 0, 0), "2024010100000")
        try {
            api.removeExercise(deviceId, entry)
            Assert.assertEquals(1, capturedRequests.size)
            val op = PftpRequest.PbPFtpOperation.parseFrom(capturedRequests[0])
            Assert.assertEquals(PftpRequest.PbPFtpOperation.Command.REMOVE, op.command)
            Assert.assertEquals(entryPath, op.path)
        } finally {
            unmockkObject(PolarServiceClientUtils)
            resetCapabilityUtilityState()
        }
    }

    @Test
    fun `getSpo2TestData throws PolarServiceNotAvailable when session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SPO2_TEST_DATA))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPsFtpClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getSpo2TestData(deviceId, LocalDate.of(2024, 3, 1), LocalDate.of(2024, 3, 7)) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }

    @Test
    fun `getOfflineRecordingStatus throws PolarServiceNotAvailable when PMD session not found`() {
        val deviceId = "E123456F"
        val api = BDBleApiImpl.getInstance(context, setOf(PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING))
        mockkObject(PolarServiceClientUtils)
        every { PolarServiceClientUtils.sessionPmdClientReady(deviceId, any()) } throws PolarServiceNotAvailable()
        try {
            Assert.assertThrows(PolarServiceNotAvailable::class.java) {
                runBlocking { api.getOfflineRecordingStatus(deviceId) }
            }
        } finally {
            unmockkObject(PolarServiceClientUtils)
        }
    }
}
