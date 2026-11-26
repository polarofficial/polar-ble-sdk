package com.polar.polarsensordatacollector.ui.graph

import android.os.Looper
import androidx.fragment.app.DialogFragment
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class GraphFragmentsTest {

    @Before
    fun setUp() {
        HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun showAndCheck(fragment: DialogFragment) {
        // Arrange
        fragment.showsDialog = false

        // Act
        val activity = HiltTestSupport.host(fragment)
        shadowOf(Looper.getMainLooper()).idle()

        // Assert
        assertTrue(fragment.isAdded)
        assertNotNull(fragment.view)
        assertEquals(R.style.Theme_GraphDialog, fragment.theme)

        // Act
        fragment.dismissAllowingStateLoss()
        activity.supportFragmentManager.executePendingTransactions()
        shadowOf(Looper.getMainLooper()).idle()

        // Assert
        assertFalse(fragment.isAdded)
    }

    @Test
    fun `hr graph fragment shows and dismisses dialog when shown`() {
        showAndCheck(HrGraphFragment())
    }

    @Test
    fun `acc graph fragment shows and dismisses dialog when shown`() {
        showAndCheck(AccGraphFragment())
    }

    @Test
    fun `ecg graph fragment shows and dismisses dialog when shown`() {
        showAndCheck(EcgGraphFragment())
    }
}
