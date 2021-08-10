package com.polar.androidcommunications.api.ble.model.polar

import org.junit.Assert
import org.junit.Test

class PolarAdvDataUtilityTest {

    @Test
    fun `getPolarModelNameFromAdvLocalName() one word`() {
        // Arrange
        val advName = "Polar OneWord1234 12345678"
        val modelNameExpected = "OneWord1234"

        // Act
        val modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advName)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getPolarModelNameFromAdvLocalName() two word`() {
        // Arrange
        val advName = " Polar two woRds 12345678 "
        val modelNameExpected = "two woRds"

        // Act
        val modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advName)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getPolarModelNameFromAdvLocalName() multi word`() {
        // Arrange
        val advName = "Polar Some m x name 12345678"
        val modelNameExpected = "Some m x name"

        // Act
        val modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advName)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getPolarModelNameFromAdvLocalName() no Polar prefix`() {
        // Arrange
        val advNameMultiWords = "Some m x name Polar 12345678"
        val modelNameExpected = ""

        // Act
        val modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advNameMultiWords)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `getPolarModelNameFromAdvLocalName() device id missing`() {
        // Arrange
        val advNameMultiWords = "Polar noId "
        val modelNameExpected = ""

        // Act
        val modelName = PolarAdvDataUtility.getPolarModelNameFromAdvLocalName(advNameMultiWords)

        // Assert
        Assert.assertEquals(modelNameExpected, modelName)
    }

    @Test
    fun `isPolarDevice() with Polar prefix`() {
        // Arrange
        val advName = " Polar Some m x name 12345678"

        // Act
        val isPolarDevice = PolarAdvDataUtility.isPolarDevice(advName)

        // Assert
        Assert.assertTrue(isPolarDevice)
    }

    @Test
    fun `isPolarDevice() no Polar prefix`() {
        // Arrange
        val advName = "Some m x name Polar 12345678"

        // Act
        val isPolarDevice = PolarAdvDataUtility.isPolarDevice(advName)

        // Assert
        Assert.assertFalse(isPolarDevice)
    }

    @Test
    fun `isPolarDevice() only prefix`() {
        // Arrange
        val advName = "Polar"

        // Act
        val isPolarDevice = PolarAdvDataUtility.isPolarDevice(advName)

        // Assert
        Assert.assertFalse(isPolarDevice)
    }
}