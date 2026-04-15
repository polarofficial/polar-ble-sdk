package com.polar.androidcommunications.api.ble.model.polar

import android.content.Context
import android.content.res.AssetManager
import android.os.Environment
import com.google.gson.Gson
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.DeviceCapabilities
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.DeviceCapabilitiesConfig
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.DefaultsSection
import com.polar.androidcommunications.api.ble.model.polar.BlePolarDeviceCapabilitiesUtility.FileSystemType
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class BlePolarDeviceCapabilitiesUtilityTest {

    private lateinit var context: Context
    private lateinit var assets: AssetManager

    @Before
    fun resetCapabilitiesState() {
        context = mockk(relaxed = true)
        assets = mockk(relaxed = true)
        every { context.assets } returns assets

        mockkStatic(Environment::class)
        val docsDir = File(System.getProperty("java.io.tmpdir"), "BlePolarDeviceCapabilitiesUtilityTestDocs")
        docsDir.mkdirs()
        // Delete PolarConfig so the file is always recreated from the mock asset stream
        File(docsDir, "PolarConfig").deleteRecursively()
        every { Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS) } returns docsDir

        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "test-reset",
                devices = emptyMap(),
                defaults = DefaultsSection(
                    fileSystemType = "UNKNOWN_FILE_SYSTEM",
                    recordingSupported = false,
                    firmwareUpdateSupported = false,
                    isDeviceSensor = false,
                    activityDataSupported = false
                )
            )
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun getFileSystemType_whenResetDefaultsLoaded_returnsUnknown() {
        val result = BlePolarDeviceCapabilitiesUtility.getFileSystemType("h10")
        assertEquals(FileSystemType.UNKNOWN_FILE_SYSTEM, result)
    }

    @Test
    fun isRecordingSupported_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("h10"))
    }

    @Test
    fun isFirmwareUpdateSupported_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported("h10"))
    }

    @Test
    fun isDeviceSensor_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isDeviceSensor("h10"))
    }

    @Test
    fun isActivityDataSupported_whenResetDefaultsLoaded_returnsFalse() {
        assertFalse(BlePolarDeviceCapabilitiesUtility.isActivityDataSupported("h10"))
    }

    @Test
    fun getFileSystemType_whenInitializedWithKnownTypes_mapsCorrectly() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = mapOf(
                    "h10" to DeviceCapabilities(fileSystemType = "H10_FILE_SYSTEM"),
                    "ignite3" to DeviceCapabilities(fileSystemType = "POLAR_FILE_SYSTEM_V2"),
                    "mystery" to DeviceCapabilities(fileSystemType = "SOMETHING_ELSE")
                ),
                defaults = DefaultsSection(fileSystemType = "POLAR_FILE_SYSTEM_V2")
            )
        )

        assertEquals(FileSystemType.H10_FILE_SYSTEM, BlePolarDeviceCapabilitiesUtility.getFileSystemType("h10"))
        assertEquals(FileSystemType.POLAR_FILE_SYSTEM_V2, BlePolarDeviceCapabilitiesUtility.getFileSystemType("ignite3"))
        assertEquals(FileSystemType.UNKNOWN_FILE_SYSTEM, BlePolarDeviceCapabilitiesUtility.getFileSystemType("mystery"))
    }

    @Test
    fun getFileSystemType_whenDeviceMissing_usesDefault() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = emptyMap(),
                defaults = DefaultsSection(fileSystemType = "H10_FILE_SYSTEM")
            )
        )

        val result = BlePolarDeviceCapabilitiesUtility.getFileSystemType("unknown-device")
        assertEquals(FileSystemType.H10_FILE_SYSTEM, result)
    }

    @Test
    fun booleanFlags_whenDevicePresent_useDeviceValues() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = mapOf(
                    "h10" to DeviceCapabilities(
                        recordingSupported = true,
                        firmwareUpdateSupported = false,
                        isDeviceSensor = true,
                        activityDataSupported = false
                    )
                ),
                defaults = DefaultsSection(
                    recordingSupported = false,
                    firmwareUpdateSupported = true,
                    isDeviceSensor = false,
                    activityDataSupported = true
                )
            )
        )

        assertTrue(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("h10"))
        assertFalse(BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported("h10"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isDeviceSensor("h10"))
        assertFalse(BlePolarDeviceCapabilitiesUtility.isActivityDataSupported("h10"))
    }

    @Test
    fun booleanFlags_whenDeviceMissing_useDefaults() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = emptyMap(),
                defaults = DefaultsSection(
                    recordingSupported = true,
                    firmwareUpdateSupported = false,
                    isDeviceSensor = true,
                    activityDataSupported = true
                )
            )
        )

        assertTrue(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("missing"))
        assertFalse(BlePolarDeviceCapabilitiesUtility.isFirmwareUpdateSupported("missing"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isDeviceSensor("missing"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isActivityDataSupported("missing"))
    }

    @Test
    fun deviceTypeLookup_isCaseInsensitive() {
        resetStateAndInitialize(
            DeviceCapabilitiesConfig(
                version = "1.0",
                devices = mapOf(
                    "h10" to DeviceCapabilities(
                        fileSystemType = "H10_FILE_SYSTEM",
                        recordingSupported = true
                    )
                ),
                defaults = DefaultsSection(
                    fileSystemType = "POLAR_FILE_SYSTEM_V2",
                    recordingSupported = false
                )
            )
        )

        assertEquals(FileSystemType.H10_FILE_SYSTEM, BlePolarDeviceCapabilitiesUtility.getFileSystemType("H10"))
        assertTrue(BlePolarDeviceCapabilitiesUtility.isRecordingSupported("H10"))
    }

    private fun resetStateAndInitialize(config: DeviceCapabilitiesConfig) {
        val json = Gson().toJson(config)
        val utilityClass = BlePolarDeviceCapabilitiesUtility::class.java
        val initializedField = utilityClass.getDeclaredField("initialized")
        initializedField.isAccessible = true
        initializedField.setBoolean(null, false)

        // Delete PolarConfig so initialize() always writes + reads the fresh mock asset JSON
        val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        File(docsDir, "PolarConfig").deleteRecursively()

        // Return a fresh stream on every call — ByteArrayInputStream is exhausted after one read
        every { assets.open(any()) } answers { ByteArrayInputStream(json.toByteArray()) }
        BlePolarDeviceCapabilitiesUtility.initialize(context)
    }
}
