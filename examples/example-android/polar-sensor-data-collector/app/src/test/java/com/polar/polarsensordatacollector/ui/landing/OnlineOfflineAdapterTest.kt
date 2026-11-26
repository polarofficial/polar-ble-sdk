package com.polar.polarsensordatacollector.ui.landing

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.polar.polarsensordatacollector.ui.activity.ActivityRecordingFragment
import com.polar.polarsensordatacollector.ui.devicesettings.DeviceSettingsFragment
import com.polar.polarsensordatacollector.ui.exercisev2.ExerciseV2Fragment
import com.polar.polarsensordatacollector.ui.h10exercise.H10ExerciseFragment
import com.polar.polarsensordatacollector.ui.logging.LoggingFragment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OnlineOfflineAdapterTest {

    private lateinit var adapter: OnlineOfflineAdapter

    @Before
    fun setUp() {
        val activity = Robolectric.buildActivity(FragmentActivity::class.java).setup().get()
        val host = Fragment()
        activity.supportFragmentManager.beginTransaction().add(host, "host").commitNow()
        adapter = OnlineOfflineAdapter(host)
    }

    private fun titles() = adapter.items.map { it.first }

    @Test
    fun `add offline recording fragment inserts first when adapter empty`() {
        // Act
        adapter.addOfflineRecordingFragment("ID1")

        // Assert
        assertEquals(listOf("OFFLINE"), titles())
        assertEquals("ID1", adapter.items[0].second.arguments?.getString(ONLINE_OFFLINE_KEY_DEVICE_ID))
    }

    @Test
    fun `add online recording fragment inserts at front when other tabs exist`() {
        // Arrange
        adapter.addOfflineRecordingFragment("ID1")

        // Act
        adapter.addOnlineRecordingFragment("ID1")

        // Assert
        assertEquals(listOf("ONLINE", "OFFLINE"), titles())
        assertTrue(adapter.items[0].second is OnlineRecFragment)
    }

    @Test
    fun `add offline recording fragment inserts after online when online exists`() {
        // Arrange
        adapter.addOnlineRecordingFragment("ID1")
        adapter.addLoggingFragment("ID1")

        // Act
        adapter.addOfflineRecordingFragment("ID1")

        // Assert
        assertEquals(listOf("ONLINE", "OFFLINE", "LOGGING"), titles())
    }

    @Test
    fun `add fragments append in order when all types added`() {
        // Act
        adapter.addOnlineRecordingFragment("ID1")
        adapter.addOfflineRecordingFragment("ID1")
        adapter.addDeviceSettingsFragment("ID1")
        adapter.addLoggingFragment("ID1")
        adapter.addActivityFragment("ID1")
        adapter.addH10ExerciseFragment("ID1")
        adapter.addExerciseV2Fragment("ID1")

        // Assert
        assertEquals(
            listOf("ONLINE", "OFFLINE", "SETTINGS", "LOGGING", "LOAD", "H10 EXERCISE", "EXERCISE"),
            titles()
        )
        assertEquals(7, adapter.itemCount)
        assertTrue(adapter.items[2].second is DeviceSettingsFragment)
        assertTrue(adapter.items[3].second is LoggingFragment)
        assertTrue(adapter.items[4].second is ActivityRecordingFragment)
        assertTrue(adapter.items[5].second is H10ExerciseFragment)
        assertTrue(adapter.items[6].second is ExerciseV2Fragment)
        assertTrue(adapter.hasExerciseV2Fragment())
    }

    @Test
    fun `add fragments ignore duplicates when type already added`() {
        // Arrange
        adapter.addOnlineRecordingFragment("ID1")
        adapter.addOfflineRecordingFragment("ID1")
        adapter.addDeviceSettingsFragment("ID1")
        adapter.addLoggingFragment("ID1")
        adapter.addActivityFragment("ID1")
        adapter.addH10ExerciseFragment("ID1")
        adapter.addExerciseV2Fragment("ID1")

        // Act
        adapter.addOnlineRecordingFragment("ID1")
        adapter.addOfflineRecordingFragment("ID1")
        adapter.addDeviceSettingsFragment("ID1")
        adapter.addLoggingFragment("ID1")
        adapter.addActivityFragment("ID1")
        adapter.addH10ExerciseFragment("ID1")
        adapter.addExerciseV2Fragment("ID1")

        // Assert
        assertEquals(7, adapter.itemCount)
    }

    @Test
    fun `has exercise v 2 fragment returns false when not added`() {
        // Assert
        assertFalse(adapter.hasExerciseV2Fragment())
    }

    @Test
    fun `create fragment returns item fragment when position valid`() {
        // Arrange
        adapter.addLoggingFragment("ID1")

        // Act
        val fragment = adapter.createFragment(0)

        // Assert
        assertSame(adapter.items[0].second, fragment)
    }

    @Test
    fun `create fragment throws when position invalid`() {
        // Act
        try {
            adapter.createFragment(3)
            fail("Expected exception")
        } catch (e: Exception) {
            // Assert
            assertEquals("Unknown fragment", e.message)
        }
    }

    @Test
    fun `get item id and contains item match fragment hash when item exists`() {
        // Arrange
        adapter.addLoggingFragment("ID1")
        adapter.addDeviceSettingsFragment("ID1")

        // Act
        val id0 = adapter.getItemId(0)
        val id1 = adapter.getItemId(1)

        // Assert
        assertNotEquals(id0, id1)
        assertTrue(adapter.containsItem(id0))
        assertFalse(adapter.containsItem(-1L))
    }

    @Test
    fun `remove fragments clears items when called`() {
        // Arrange
        adapter.addLoggingFragment("ID1")

        // Act
        adapter.removeFragments()

        // Assert
        assertEquals(0, adapter.itemCount)
    }
}
