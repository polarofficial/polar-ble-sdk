package com.polar.polarsensordatacollector.ui.offlinerectrigger

import android.os.Bundle
import androidx.navigation.NavController
import androidx.navigation.NavDirections
import androidx.navigation.Navigation
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.polar.polarsensordatacollector.R
import com.polar.polarsensordatacollector.testutil.HiltTestSupport
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflineRecTriggerFragmentTest {

    private val navController = mockk<NavController>(relaxed = true)

    @Before
    fun setUp() {
        HiltTestSupport.install()
    }

    @After
    fun tearDown() {
        HiltTestSupport.uninstall()
    }

    private fun launch(): OfflineRecTriggerFragment {
        val fragment = OfflineRecTriggerFragment()
        // Set the NavController before onViewCreated runs.
        fragment.viewLifecycleOwnerLiveData.observeForever { owner ->
            if (owner != null) Navigation.setViewNavController(fragment.requireView(), navController)
        }
        HiltTestSupport.host(fragment, Bundle().apply { putString("deviceIdRecTriggerArgument", "dev1") })
        HiltTestSupport.awaitUntil { fragment.view != null }
        return fragment
    }

    @Test
    fun `tabs show status and setup when fragment is created`() {
        // Act
        val fragment = launch()

        // Assert
        val tabs = fragment.requireView().findViewById<TabLayout>(R.id.tab_layout_offline_trigger)
        assertEquals(2, tabs.tabCount)
        assertEquals("STATUS", tabs.getTabAt(0)?.text)
        assertEquals("SETUP", tabs.getTabAt(1)?.text)
        assertEquals(2, fragment.requireView().findViewById<ViewPager2>(R.id.pager_offline_trigger).adapter?.itemCount)
    }

    @Test
    fun `fragment navigates home when device is not connected`() {
        // Act
        launch()

        // Assert
        HiltTestSupport.awaitUntil { runCatching { verify { navController.navigate(any<NavDirections>()) } }.isSuccess }
    }

    @Test
    fun `adapter throws unknown fragment when position is invalid`() {
        // Arrange
        val adapter = OfflineRecTriggerAdapter(launch(), "dev1")

        // Act
        val result = runCatching { adapter.createFragment(5) }

        // Assert
        assertEquals("Unknown fragment", result.exceptionOrNull()?.message)
    }
}
