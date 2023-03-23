package com.polar.androidcommunications.api.ble.model.gatt.client.pmd

import com.polar.androidcommunications.testrules.BleLoggerTestRule
import org.junit.Assert
import org.junit.Rule
import org.junit.Test

internal class PmdSecretTest {
    @Rule
    @JvmField
    val bleLoggerTestRule = BleLoggerTestRule()

    private val key16bytes: ByteArray = byteArrayOf(0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D, 0x0E, 0xFF.toByte())

    @Test
    fun `test strategy NONE serialization`() {
        //Arrange
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.NONE, key = byteArrayOf())

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(3, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.NONE.numVal.toByte(), serialized[2])
    }

    @Test
    fun `test strategy XOR serialization`() {
        //Arrange
        val expectedKey = byteArrayOf(0xFF.toByte())
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.XOR, key = expectedKey)

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(1 + 1 + 1 + 1, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.XOR.numVal.toByte(), serialized[2])
        Assert.assertArrayEquals(expectedKey, serialized.drop(3).toByteArray())
    }

    @Test
    fun `test strategy AES128 serialization`() {
        //Arrange
        val expectedKey = key16bytes.reversed().toByteArray()
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES128, key = expectedKey)

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(1 + 1 + 1 + 16, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.AES128.numVal.toByte(), serialized[2])
        Assert.assertArrayEquals(expectedKey, serialized.drop(3).toByteArray())
    }

    @Test
    fun `test strategy AES256 serialization`() {
        //Arrange
        val expectedKey = key16bytes + key16bytes.reversed()
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES256, key = expectedKey)

        //Act
        val serialized = pmdSecret.serializeToPmdSettings()

        //Assert
        Assert.assertEquals(1 + 1 + 1 + 32, serialized.size)
        Assert.assertEquals(PmdSetting.PmdSettingType.SECURITY.numVal.toByte(), serialized[0])
        Assert.assertEquals(1.toByte(), serialized[1])
        Assert.assertEquals(PmdSecret.SecurityStrategy.AES256.numVal.toByte(), serialized[2])
        Assert.assertArrayEquals(expectedKey, serialized.drop(3).toByteArray())
    }

    @Test
    fun `test decryption strategy NONE`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
        )

        val expectedDecryptedData = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
        )

        val key = byteArrayOf()
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.NONE, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }

    @Test
    fun `test decryption strategy XOR`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
        )

        val expectedDecryptedData = byteArrayOf(
            0x55.toByte(), 0x54.toByte(), 0x57.toByte(), 0x56.toByte(), 0x51.toByte(), 0x50.toByte(), 0x53.toByte(), 0x52.toByte(), 0x5D.toByte(), 0x5C.toByte(), 0x5F.toByte(), 0x5E.toByte(), 0x59.toByte(), 0x58.toByte(), 0x5B.toByte(), 0xAA.toByte(),
        )

        val key = byteArrayOf(0x55)
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.XOR, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }

    @Test
    fun `test decryption strategy AE128`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte()
        )

        val expectedDecryptedData = byteArrayOf(
            0x60.toByte(), 0x08.toByte(), 0x6b.toByte(), 0xda.toByte(), 0x00.toByte(), 0xdb.toByte(), 0x42.toByte(), 0x62.toByte(), 0x34.toByte(), 0x60.toByte(), 0x27.toByte(), 0x43.toByte(), 0x71.toByte(), 0xa7.toByte(), 0x53.toByte(), 0x68.toByte(),
            0x60.toByte(), 0x08.toByte(), 0x6b.toByte(), 0xda.toByte(), 0x00.toByte(), 0xdb.toByte(), 0x42.toByte(), 0x62.toByte(), 0x34.toByte(), 0x60.toByte(), 0x27.toByte(), 0x43.toByte(), 0x71.toByte(), 0xa7.toByte(), 0x53.toByte(), 0x68.toByte(),
            0x6f.toByte(), 0x5e.toByte(), 0x05.toByte(), 0x8b.toByte(), 0x37.toByte(), 0xdd.toByte(), 0xd1.toByte(), 0xed.toByte(), 0x0e.toByte(), 0xf2.toByte(), 0x89.toByte(), 0xef.toByte(), 0xf8.toByte(), 0xb2.toByte(), 0x85.toByte(), 0x54.toByte(),
        )

        val key = key16bytes
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES128, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }

    @Test
    fun `test decryption strategy AES256`() {
        //Arrange
        val chipper = byteArrayOf(
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0x00.toByte(), 0x01.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte(),
            0xFF.toByte(), 0xFF.toByte(), 0x02.toByte(), 0x03.toByte(), 0x04.toByte(), 0x05.toByte(), 0x06.toByte(), 0x07.toByte(), 0x08.toByte(), 0x09.toByte(), 0x0A.toByte(), 0x0B.toByte(), 0x0C.toByte(), 0x0D.toByte(), 0x0E.toByte(), 0xFF.toByte()
        )

        val expectedDecryptedData = byteArrayOf(
            0xc8.toByte(), 0x0d.toByte(), 0x56.toByte(), 0xbb.toByte(), 0x97.toByte(), 0x7a.toByte(), 0x42.toByte(), 0x5f.toByte(), 0x5a.toByte(), 0xa1.toByte(), 0xcd.toByte(), 0xfc.toByte(), 0x24.toByte(), 0xa2.toByte(), 0x78.toByte(), 0x12.toByte(),
            0xc8.toByte(), 0x0d.toByte(), 0x56.toByte(), 0xbb.toByte(), 0x97.toByte(), 0x7a.toByte(), 0x42.toByte(), 0x5f.toByte(), 0x5a.toByte(), 0xa1.toByte(), 0xcd.toByte(), 0xfc.toByte(), 0x24.toByte(), 0xa2.toByte(), 0x78.toByte(), 0x12.toByte(),
            0x30.toByte(), 0x04.toByte(), 0xb9.toByte(), 0x9f.toByte(), 0x6f.toByte(), 0xfa.toByte(), 0x3b.toByte(), 0xb7.toByte(), 0x73.toByte(), 0xb1.toByte(), 0x75.toByte(), 0xa5.toByte(), 0x23.toByte(), 0x5d.toByte(), 0xcb.toByte(), 0x93.toByte(),
        )

        val key = key16bytes + key16bytes
        val pmdSecret = PmdSecret(strategy = PmdSecret.SecurityStrategy.AES256, key = key)

        //Act
        val decryptedData = pmdSecret.decryptArray(chipper)

        //Assert
        Assert.assertArrayEquals(expectedDecryptedData, decryptedData)
    }
}