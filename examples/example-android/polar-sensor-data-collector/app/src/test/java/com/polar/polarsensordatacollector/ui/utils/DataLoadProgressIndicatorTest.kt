package com.polar.polarsensordatacollector.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DataLoadProgressIndicatorTest {

    @Test
    fun `format bytes returns zero bytes when zero`() {
        assertEquals("0 B", formatBytes(0L))
    }

    @Test
    fun `format bytes returns bytes when below one kilobyte`() {
        assertEquals("1023 B", formatBytes(1023L))
    }

    @Test
    fun `format bytes returns kilobytes when below one megabyte`() {
        assertEquals("1.0 KB", formatBytes(1024L))
        assertEquals("1.5 KB", formatBytes(1536L))
    }

    @Test
    fun `format bytes returns megabytes when below one gigabyte`() {
        assertEquals("1.00 MB", formatBytes(1024L * 1024))
    }

    @Test
    fun `format bytes returns gigabytes when one gigabyte or more`() {
        assertEquals("2.00 GB", formatBytes(2L * 1024 * 1024 * 1024))
    }

    @Test
    fun `data load progress has null path by default`() {
        assertNull(DataLoadProgress(1L, 2L, 50).path)
    }
}
