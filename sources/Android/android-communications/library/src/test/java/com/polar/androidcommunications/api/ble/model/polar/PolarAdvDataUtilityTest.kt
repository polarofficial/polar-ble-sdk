package com.polar.androidcommunications.api.ble.model.polar

import org.junit.Assert
import org.junit.Test

class PolarAdvDataUtilityTest {

    @Test
    fun `getDeviceModelNameFromAdvLocalName() one word`() {
        // Arrange
        val advName = "Polar OneWord1234 12345678"
        val modelNameExpected = "OneWord1234"

        // Act
        val modelName = PolarAdvDataUtility.getDeviceModelNameFromAdvLocalName(advName)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getDeviceModelNameFromAdvLocalName() two word`() {
        // Arrange
        val advName = " Polar two woRds 12345678 "
        val modelNameExpected = "two woRds"

        // Act
        val modelName = PolarAdvDataUtility.getDeviceModelNameFromAdvLocalName(advName)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getDeviceModelNameFromAdvLocalName() multi word`() {
        // Arrange
        val advName = "Polar Some m x name 12345678"
        val modelNameExpected = "Some m x name"

        // Act
        val modelName = PolarAdvDataUtility.getDeviceModelNameFromAdvLocalName(advName)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getDeviceModelNameFromAdvLocalName() no Polar prefix`() {
        // Arrange
        val advNameMultiWords = "Some m x name Polar 12345678"
        val modelNameExpected = ""

        // Act
        val modelName = PolarAdvDataUtility.getDeviceModelNameFromAdvLocalName(advNameMultiWords)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getDeviceModelNameFromAdvLocalName() device id missing`() {
        // Arrange
        val advNameMultiWords = "Polar noId "
        val modelNameExpected = ""

        // Act
        val modelName = PolarAdvDataUtility.getDeviceModelNameFromAdvLocalName(advNameMultiWords)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `isValidDevice() with Polar prefix`() {
        // Arrange
        val advName = " Polar Some m x name 12345678"

        // Act
        val isValidDevice = PolarAdvDataUtility.isValidDevice(advName)

        // Assert
        Assert.assertTrue(isValidDevice)
    }

    @Test
    fun `isValidDevice() no Polar prefix`() {
        // Arrange
        val advName = "Some m x name Polar 12345678"

        // Act
        val isValidDevice = PolarAdvDataUtility.isValidDevice(advName)

        // Assert
        Assert.assertFalse(isValidDevice)
    }

    @Test
    fun `isValidDevice() only prefix`() {
        // Arrange
        val advName = "Polar"

        // Act
        val isValidDevice = PolarAdvDataUtility.isValidDevice(advName)

        // Assert
        Assert.assertFalse(isValidDevice)
    }
}