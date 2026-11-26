package com.polar.polarsensordatacollector.ui.landing

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class StreamFileUrisTest {

    private fun uri(type: String, name: String): Uri =
        Uri.parse("content://files/sensorDataLogs/$type/dev1/$name")

    @Test
    fun `find marker links returns marker and data files when session has marker`() {
        // Arrange
        val hr = uri("HR", "HR_2026-10-30T07:00:00_dev1.txt")
        val acc = uri("ACC", "ACC_2026-10-30T07:00:00_dev1.txt")
        val marker = uri("MARKER", "MARKER_2026-10-30T07:00:00_dev1.txt")

        // Act
        val links = StreamFileUris.findMarkerLinks(listOf(hr, marker, acc))

        // Assert
        assertEquals(marker, links?.first)
        assertEquals(listOf(hr, acc), links?.second)
    }

    @Test
    fun `find marker links returns null when session has no marker`() {
        // Arrange
        val hr = uri("HR", "HR_2026-10-30T07:00:00_dev1.txt")

        // Act
        val links = StreamFileUris.findMarkerLinks(listOf(hr))

        // Assert
        assertNull(links)
    }

    @Test
    fun `parse metadata returns null when file name has no timestamp`() {
        // Arrange
        val file = uri("HR", "HR_dev1.txt")

        // Act
        val metadata = StreamFileUris.parseMetadata(file)

        // Assert
        assertNull(metadata)
    }
}
