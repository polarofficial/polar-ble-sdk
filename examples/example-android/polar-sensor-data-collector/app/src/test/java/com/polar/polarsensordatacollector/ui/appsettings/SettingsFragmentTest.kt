package com.polar.polarsensordatacollector.ui.appsettings

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreferenceCompat
import androidx.test.core.app.ApplicationProvider
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.FileProviderTestSupport
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
class SettingsFragmentTest {

    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val treeUri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3ADocuments")

    @Before
    fun setUp() {
        HiltTestSupport.install()
        PreferenceManager.getDefaultSharedPreferences(context).edit().clear().commit()
    }

    @After
    fun tearDown() {
        unmockkAll()
        HiltTestSupport.uninstall()
    }

    private fun launch(): Pair<SettingsFragment, androidx.appcompat.app.AppCompatActivity> {
        val fragment = SettingsFragment()
        val activity = HiltTestSupport.host(fragment)
        return fragment to activity
    }

    private fun SettingsFragment.locationPreference(): Preference = findPreference(getString(R.string.back_up_location))!!

    private fun SettingsFragment.enabledPreference(): SwitchPreferenceCompat = findPreference(getString(R.string.back_up_enabled))!!

    @Test
    fun `on create preferences shows all preferences`() {
        // Act
        val (fragment, _) = launch()

        // Assert
        assertNotNull(fragment.findPreference<Preference>(fragment.getString(R.string.back_up_location)))
        assertNotNull(fragment.findPreference<Preference>(fragment.getString(R.string.back_up_enabled)))
        assertNotNull(fragment.findPreference<Preference>("export_logs"))
    }

    @Test
    fun `on create preferences disables back up when stored location is not AV alid directory`() {
        // Arrange
        PreferenceManager.getDefaultSharedPreferences(context).edit()
            .putBoolean(context.getString(R.string.back_up_enabled), true)
            .putString(context.getString(R.string.back_up_location), treeUri.toString())
            .commit()

        // Act
        val (fragment, _) = launch()

        // Assert
        assertFalse(fragment.enabledPreference().isChecked)
    }

    @Test
    fun `on create preferences keeps not set summary when no location is stored`() {
        // Act
        val (fragment, _) = launch()

        // Assert
        assertEquals(context.getString(R.string.ext_storage_prefs_summary_not_set), fragment.locationPreference().summary.toString())
        assertFalse(fragment.enabledPreference().isChecked)
    }

    @Test
    fun `location preference opens directory picker when clicked`() {
        // Arrange
        val (fragment, activity) = launch()
        fragment.enabledPreference().isChecked = true

        // Act
        fragment.locationPreference().performClick()

        // Assert
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, shadowOf(activity).nextStartedActivity.action)
    }

    @Test
    fun `back up switch opens directory picker when enabled without location`() {
        // Arrange
        val (fragment, activity) = launch()

        // Act
        fragment.enabledPreference().performClick()

        // Assert
        val intent = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_OPEN_DOCUMENT_TREE, intent.action)
        assertNotNull(intent.getStringExtra(DocumentsContract.EXTRA_INITIAL_URI))
    }

    @Test
    fun `back up switch does not open directory picker when disabled`() {
        // Arrange
        val (fragment, activity) = launch()
        fragment.enabledPreference().isChecked = true
        while (shadowOf(activity).nextStartedActivity != null) {
            // Drain the started activities.
        }

        // Act
        fragment.enabledPreference().performClick()

        // Assert
        assertNull(shadowOf(activity).nextStartedActivity)
    }

    @Test
    fun `directory picker result stores selected location`() {
        // Arrange
        val (fragment, activity) = launch()
        fragment.enabledPreference().isChecked = true
        fragment.locationPreference().performClick()
        val request = shadowOf(activity).nextStartedActivity

        // Act
        shadowOf(activity).receiveResult(request, Activity.RESULT_OK, Intent().setData(treeUri))
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Assert
        val stored = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.back_up_location), "")
        assertEquals(treeUri.toString(), stored)
        assertEquals(context.getString(R.string.ext_storage_prefs_summary_not_set), fragment.locationPreference().summary.toString())
    }

    @Test
    fun `directory picker result keeps location when cancelled`() {
        // Arrange
        val (fragment, activity) = launch()
        fragment.enabledPreference().isChecked = true
        fragment.locationPreference().performClick()
        val request = shadowOf(activity).nextStartedActivity

        // Act
        shadowOf(activity).receiveResult(request, Activity.RESULT_CANCELED, null)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Assert
        val stored = PreferenceManager.getDefaultSharedPreferences(context).getString(context.getString(R.string.back_up_location), "")
        assertEquals("", stored)
    }

    private fun mockLogcat(process: () -> Process) {
        mockkConstructor(ProcessBuilder::class)
        every { anyConstructed<ProcessBuilder>().command(any<String>(), any<String>()) } answers { self as ProcessBuilder }
        every { anyConstructed<ProcessBuilder>().redirectErrorStream(true) } answers { self as ProcessBuilder }
        every { anyConstructed<ProcessBuilder>().start() } answers { process() }
    }

    @Test
    fun `export logs preference shares logs when logcat succeeds`() {
        // Arrange
        FileProviderTestSupport.reset()
        val process = mockk<Process>()
        every { process.inputStream } returns java.io.ByteArrayInputStream("log line".toByteArray())
        mockLogcat { process }
        val (fragment, activity) = launch()
        // The FileProvider root must contain the external files folder.
        mockkStatic(android.os.Environment::class)
        every { android.os.Environment.getExternalStorageDirectory() } returns context.getExternalFilesDir(null)!!.parentFile!!

        // Act
        fragment.findPreference<Preference>("export_logs")!!.performClick()

        // Assert
        val chooser = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        val sent = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, sent.action)
        assertEquals("text/plain", sent.type)
        assertNotNull(sent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
        assertEquals(null, ShadowToast.getLatestToast())
    }

    @Test
    fun `export logs preference shows error toast when logcat fails`() {
        // Arrange
        mockLogcat { throw java.io.IOException("no logcat") }
        val (fragment, activity) = launch()

        // Act
        fragment.findPreference<Preference>("export_logs")!!.performClick()

        // Assert
        assertEquals("Failed to dump logcat", ShadowToast.getTextOfLatestToast())
        assertEquals(null, shadowOf(activity).nextStartedActivity)
    }
}
