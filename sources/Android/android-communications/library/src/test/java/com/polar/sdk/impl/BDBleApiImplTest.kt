import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.content.Context
import android.content.IntentFilter
import android.os.ParcelUuid
import com.polar.androidcommunications.api.ble.model.BleDeviceSession
import com.polar.androidcommunications.api.ble.model.advertisement.BleAdvertisementContent
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpClient
import com.polar.androidcommunications.api.ble.model.gatt.client.psftp.BlePsFtpUtils
import com.polar.androidcommunications.enpoints.ble.bluedroid.host.BDScanCallback
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarBleSdkInstanceException
import com.polar.sdk.impl.BDBleApiImpl
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
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert
import org.junit.Before
import org.junit.Test
import protocol.PftpRequest
import java.io.ByteArrayOutputStream
import java.time.LocalDateTime
import java.util.TimeZone
import java.util.concurrent.atomic.AtomicInteger

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
}