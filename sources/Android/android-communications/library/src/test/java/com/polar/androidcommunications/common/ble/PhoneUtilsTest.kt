package com.polar.androidcommunications.common.ble

import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

internal class PhoneUtilsTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    @Test
    fun `test phone with MTU negotiation problem`() {
        //Arrange, Act & Assert
        Assert.assertTrue(PhoneUtils.isMtuNegotiationBroken("MotorolA", "moto e30"))
        Assert.assertTrue(PhoneUtils.isMtuNegotiationBroken("MotorolA", "moto g(20)"))
    }

    @Test
    fun `test phone without MTU negotiation problem`() {
        //Arrange, Act & Assert
        Assert.assertFalse(PhoneUtils.isMtuNegotiationBroken("SomePhone", "Some model"))
    }
}
