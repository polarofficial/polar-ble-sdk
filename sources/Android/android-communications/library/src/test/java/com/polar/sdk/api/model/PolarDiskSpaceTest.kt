package com.polar.sdk.api.model

import io.mockk.every
import io.mockk.mockk
import junit.framework.TestCase.assertEquals
import org.junit.Test
import protocol.PftpResponse

class PolarDiskSpaceTest {

    @Test
    fun `PolarDiskSpaceData fromProto() should convert proto to PolarDiskSpaceData properly`() {
        // Arrange
        val proto = mockk<PftpResponse.PbPFtpDiskSpaceResult>()
        every { proto.fragmentSize } returns 512
        every { proto.totalFragments } returns 2048
        every { proto.freeFragments } returns 1024

        // Act
        val result = PolarDiskSpaceData.fromProto(proto)

        // Arrange
        assertEquals(1048576, result.totalSpace)
        assertEquals(524288, result.freeSpace)
    }
}