package com.polar.polarsensordatacollector.testutil

import android.content.SharedPreferences
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.compose.runtime.snapshots.Snapshot
import androidx.fragment.app.Fragment
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import com.polar.polarsensordatacollector.di.PolarBleSdkModule
import com.polar.polarsensordatacollector.ui.utils.DataViewer
import com.polar.sdk.impl.BDBleApiImpl
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.robolectric.Robolectric
import org.robolectric.android.controller.ActivityController
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowDialog

/**
 * Helps to test classes that use the real Hilt graph under Robolectric.
 * Call [install] in a @Before method and [uninstall] in an @After method.
 */
object HiltTestSupport {

    /** Replace the parts of the Hilt graph that need real hardware. Return the mock BLE API. */
    fun install(): BDBleApiImpl {
        // An old test can leave Dispatchers.Main on a stale looper. Bind it to the current looper.
        runCatching { Dispatchers.resetMain() }
        Dispatchers.setMain(Handler(Looper.getMainLooper()).asCoroutineDispatcher())
        val api = mockk<BDBleApiImpl>(relaxed = true)
        // Robolectric has no AndroidKeyStore. Replace the encrypted storage.
        mockkStatic(MasterKeys::class, EncryptedSharedPreferences::class)
        every { MasterKeys.getOrCreate(any()) } returns "test-master-key"
        every { EncryptedSharedPreferences.create(any<String>(), any<String>(), any(), any(), any()) } returns
            mockk<SharedPreferences>(relaxed = true)
        mockkObject(PolarBleSdkModule)
        every { PolarBleSdkModule.providePolarBleSdkApi(any()) } returns api
        return api
    }

    private val controllers = mutableListOf<ActivityController<DataViewer>>()

    fun uninstall() {
        // Destroy the hosted activities. A live activity can make later Compose tests wait for ever.
        ShadowDialog.getShownDialogs().forEach { runCatching { it.dismiss() } }
        ShadowDialog.reset()
        controllers.forEach { runCatching { it.pause().stop().destroy() } }
        controllers.clear()
        runCatching { Dispatchers.resetMain() }
        unmockkAll()
    }

    /** Add the fragment to a Hilt activity. Return the activity. */
    fun host(fragment: Fragment, args: Bundle? = null): DataViewer {
        if (args != null) fragment.arguments = args
        val controller = Robolectric.buildActivity(DataViewer::class.java)
        controllers.add(controller)
        val activity = controller.create().start().resume().get()
        shadowOf(Looper.getMainLooper()).idle()
        activity.supportFragmentManager.beginTransaction()
            .add(android.R.id.content, fragment, "under_test")
            .commitNow()
        shadowOf(Looper.getMainLooper()).idle()
        return activity
    }

    /** Idle the main looper until the condition is true. Fail after the timeout. */
    fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            // Compose starts its global snapshot observer only once for each sandbox. After an
            // earlier test, the observer can miss state writes. Send the apply notification by hand.
            Snapshot.sendApplyNotifications()
            shadowOf(Looper.getMainLooper()).idle()
            check(System.currentTimeMillis() < deadline) { "Condition not met in $timeoutMs ms" }
            Thread.sleep(10)
        }
    }
}
