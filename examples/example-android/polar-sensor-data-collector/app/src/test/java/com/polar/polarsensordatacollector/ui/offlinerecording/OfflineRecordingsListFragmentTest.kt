package com.polar.polarsensordatacollector.ui.offlinerecording

import android.os.Bundle
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class OfflineRecordingsListFragmentTest {

    @Before
    fun setUp() {
        HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun findCompose(view: View): ComposeView? {
        if (view is ComposeView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) findCompose(view.getChildAt(i))?.let { return it }
        }
        return null
    }

    @Test
    fun `on create view shows compose content when fragment is hosted`() {
        // Arrange
        val fragment = OfflineRecordingsListFragment()
        val args = Bundle().apply { putString("deviceIdFragmentArgument", "DEV1") }

        // Act
        val activity = HiltTestSupport.host(fragment, args)
        shadowOf(Looper.getMainLooper()).idle()

        // Assert
        assertNotNull(findCompose(activity.window.decorView))
        assertTrue(fragment.isResumed)
    }
}
