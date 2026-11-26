package com.polar.polarsensordatacollector.ui.physicalconfig

import android.content.Intent
import android.content.SharedPreferences
import android.os.Looper
import android.widget.Button
import android.widget.EditText
import android.widget.NumberPicker
import android.widget.RadioGroup
import android.widget.Spinner
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.di.PolarBleSdkModule
import com.polar.polarsensordatacollector.ui.landing.ONLINE_OFFLINE_KEY_DEVICE_ID
import com.polar.sdk.api.model.PolarFirstTimeUseConfig
import com.polar.sdk.api.model.PolarPhysicalConfiguration
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
class PhysicalConfigActivityTest {

    private val deviceId = "dev1"
    private val api = mockk<BDBleApiImpl>(relaxed = true)

    @Before
    fun setUp() {
        // Robolectric has no AndroidKeyStore. Replace the encrypted storage that the Hilt graph creates.
        mockkStatic(MasterKeys::class, EncryptedSharedPreferences::class)
        every { MasterKeys.getOrCreate(any()) } returns "test-master-key"
        every { EncryptedSharedPreferences.create(any<String>(), any<String>(), any(), any(), any()) } returns mockk<SharedPreferences>(relaxed = true)
        mockkObject(PolarBleSdkModule)
        every { PolarBleSdkModule.providePolarBleSdkApi(any()) } returns api
    }

    private val controllers = mutableListOf<org.robolectric.android.controller.ActivityController<PhysicalConfigActivity>>()

    @After
    fun tearDown() {
        // Destroy the activities. A live activity can make later Compose tests wait for ever.
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        unmockkAll()
    }

    private fun config(sleepGoalMinutes: Int = 8 * 60 + 15, gender: PolarFirstTimeUseConfig.Gender = PolarFirstTimeUseConfig.Gender.FEMALE) =
        PolarPhysicalConfiguration(
            gender = gender,
            birthDate = LocalDate.of(1990, 5, 17),
            height = 172.5f,
            weight = 64.0f,
            maxHeartRate = 190,
            restingHeartRate = 55,
            vo2Max = 42,
            trainingBackground = PolarFirstTimeUseConfig.TRAINING_BACKGROUND_VALUES[1],
            typicalDay = PolarFirstTimeUseConfig.TypicalDay.MOSTLY_MOVING,
            sleepGoalMinutes = sleepGoalMinutes,
            deviceTime = "2024-01-01T00:00:00Z"
        )

    private fun idle() = shadowOf(Looper.getMainLooper()).idle()

    private fun launch(withDeviceId: Boolean = true): PhysicalConfigActivity {
        val intent = Intent(ApplicationProvider.getApplicationContext(), PhysicalConfigActivity::class.java)
        if (withDeviceId) intent.putExtra(ONLINE_OFFLINE_KEY_DEVICE_ID, deviceId)
        val controller = Robolectric.buildActivity(PhysicalConfigActivity::class.java, intent)
        controllers.add(controller)
        val activity = controller.create().start().resume().get()
        awaitContent(activity)
        return activity
    }

    private fun awaitContent(activity: AppCompatActivity) {
        val deadline = System.currentTimeMillis() + 5_000
        while (activity.findViewById<TextView>(R.id.textViewBirthday) == null) {
            idle()
            check(System.currentTimeMillis() < deadline) { "Content not set" }
            Thread.sleep(10)
        }
        idle()
    }

    private fun awaitToast(text: String) {
        val deadline = System.currentTimeMillis() + 5_000
        while (ShadowToast.getTextOfLatestToast() != text) {
            idle()
            check(System.currentTimeMillis() < deadline) { "Toast '$text' not shown. Last: ${ShadowToast.getTextOfLatestToast()}" }
            Thread.sleep(10)
        }
    }

    @Test
    fun `on create shows device values when device returns configuration`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config()

        // Act
        val activity = launch()

        // Assert
        assertEquals(R.id.radioButtonFemale, activity.findViewById<RadioGroup>(R.id.radioGroupSexual).checkedRadioButtonId)
        assertEquals("1990-05-17", activity.findViewById<TextView>(R.id.textViewBirthday).text.toString())
        assertEquals("172.5", activity.findViewById<EditText>(R.id.editTextHeight).text.toString())
        assertEquals("64.0", activity.findViewById<EditText>(R.id.editTextWeight).text.toString())
        assertEquals(190, activity.findViewById<Spinner>(R.id.spinnerMaxHeartRate).selectedItem)
        assertEquals(55, activity.findViewById<Spinner>(R.id.spinnerRestingHeartRate).selectedItem)
        assertEquals(42, activity.findViewById<NumberPicker>(R.id.numberPickerVO2max).value)
        assertEquals(PolarFirstTimeUseConfig.TypicalDay.MOSTLY_MOVING.ordinal, activity.findViewById<Spinner>(R.id.typicalDaySpinner).selectedItemPosition)
        assertEquals(8, activity.findViewById<NumberPicker>(R.id.sleepGoalPickerHours).value)
        assertEquals(15, activity.findViewById<NumberPicker>(R.id.sleepGoalPickerMinutes).value)
    }

    @Test
    fun `on create selects male when device reports male`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config(gender = PolarFirstTimeUseConfig.Gender.MALE)

        // Act
        val activity = launch()

        // Assert
        assertEquals(R.id.radioButtonMale, activity.findViewById<RadioGroup>(R.id.radioGroupSexual).checkedRadioButtonId)
    }

    @Test
    fun `on create hides sleep goal pickers when sleep goal is zero`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config(sleepGoalMinutes = 0)

        // Act
        val activity = launch()

        // Assert
        assertFalse(activity.findViewById<NumberPicker>(R.id.sleepGoalPickerHours).isShown)
        assertFalse(activity.findViewById<NumberPicker>(R.id.sleepGoalPickerMinutes).isShown)
        assertFalse(activity.findViewById<TextView>(R.id.sleep_goal_label).isShown)
    }

    @Test
    fun `on create keeps initial values when device returns no configuration`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } throws IllegalStateException("offline")

        // Act
        val activity = launch()

        // Assert
        assertEquals("", activity.findViewById<EditText>(R.id.editTextHeight).text.toString())
        assertEquals(-1, activity.findViewById<RadioGroup>(R.id.radioGroupSexual).checkedRadioButtonId)
        assertEquals(8, activity.findViewById<NumberPicker>(R.id.sleepGoalPickerHours).value)
        assertEquals(null, ShadowToast.getLatestToast())
    }

    @Test
    fun `on create uses default values when view model cannot load`() {
        // Act
        val activity = launch(withDeviceId = false)

        // Assert
        awaitToast(activity.getString(R.string.ftu_failed_to_get_user_physical_information_from_device_using_defaults))
        assertEquals("2000-01-01", activity.findViewById<TextView>(R.id.textViewBirthday).text.toString())
        assertEquals("185.0", activity.findViewById<EditText>(R.id.editTextHeight).text.toString())
        assertEquals(R.id.radioButtonMale, activity.findViewById<RadioGroup>(R.id.radioGroupSexual).checkedRadioButtonId)
    }

    @Test
    fun `hours picker disables minutes when eleven hours is selected`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config()
        val activity = launch()
        val hours = activity.findViewById<NumberPicker>(R.id.sleepGoalPickerHours)
        val minutes = activity.findViewById<NumberPicker>(R.id.sleepGoalPickerMinutes)
        val listenerField = NumberPicker::class.java.getDeclaredField("mOnValueChangeListener").apply { isAccessible = true }
        val listener = listenerField.get(hours) as NumberPicker.OnValueChangeListener

        // Act
        listener.onValueChange(hours, 8, 11)

        // Assert
        assertFalse(minutes.isEnabled)
        assertEquals(0, minutes.value)

        // Act
        listener.onValueChange(hours, 11, 9)

        // Assert
        assertTrue(minutes.isEnabled)
    }

    @Test
    fun `ok button sends configuration to device when clicked`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config()
        val sent = slot<PolarFirstTimeUseConfig>()
        coEvery { api.doFirstTimeUse(deviceId, capture(sent)) } returns Unit
        val activity = launch()

        // Act
        activity.findViewById<Button>(R.id.submit_physical_button).performClick()

        // Assert
        awaitToast(activity.getString(R.string.physical_data_sent_successfully))
        coVerify { api.doFirstTimeUse(deviceId, any()) }
        assertEquals(PolarFirstTimeUseConfig.Gender.FEMALE, sent.captured.gender)
        assertEquals(LocalDate.of(1990, 5, 17), sent.captured.birthDate)
        assertEquals(172.5f, sent.captured.height)
        assertEquals(190, sent.captured.maxHeartRate)
        assertEquals(42, sent.captured.vo2Max)
        assertEquals(8 * 60 + 15, sent.captured.sleepGoalMinutes)
    }

    @Test
    fun `ok button shows error when device rejects configuration`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config()
        coEvery { api.doFirstTimeUse(any(), any()) } throws IllegalStateException("rejected")
        val activity = launch()

        // Act
        activity.findViewById<Button>(R.id.submit_physical_button).performClick()

        // Assert
        awaitToast(activity.getString(R.string.error_sending_physical_data))
    }

    @Test
    fun `ok button shows error when no gender is selected`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config()
        val activity = launch()
        activity.findViewById<RadioGroup>(R.id.radioGroupSexual).clearCheck()

        // Act
        activity.findViewById<Button>(R.id.submit_physical_button).performClick()

        // Assert
        assertEquals("Failed to save user physical information. Error: Invalid gender selection", ShadowToast.getTextOfLatestToast())
        coVerify(exactly = 0) { api.doFirstTimeUse(any(), any()) }
    }

    @Test
    fun `ok button shows error when birthday is invalid`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config()
        val activity = launch()
        activity.findViewById<TextView>(R.id.textViewBirthday).text = "not a date"

        // Act
        activity.findViewById<Button>(R.id.submit_physical_button).performClick()

        // Assert
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith("Failed to save user physical information. Error:"))
    }

    @Test
    fun `ok button shows device id missing when intent has no device id`() {
        // Arrange
        val activity = launch(withDeviceId = false)
        awaitToast(activity.getString(R.string.ftu_failed_to_get_user_physical_information_from_device_using_defaults))

        // Act
        activity.findViewById<Button>(R.id.submit_physical_button).performClick()

        // Assert
        assertEquals(activity.getString(R.string.device_id_not_found), ShadowToast.getTextOfLatestToast())
        coVerify(exactly = 0) { api.doFirstTimeUse(any(), any()) }
    }

    @Test
    fun `birthday text opens date picker when clicked`() {
        // Arrange
        coEvery { api.getUserPhysicalConfiguration(deviceId) } returns config()
        val activity = launch()

        // Act
        activity.findViewById<TextView>(R.id.textViewBirthday).performClick()
        idle()

        // Assert
        assertNotNull(activity.supportFragmentManager.findFragmentByTag("MATERIAL_DATE_PICKER"))
    }
}
