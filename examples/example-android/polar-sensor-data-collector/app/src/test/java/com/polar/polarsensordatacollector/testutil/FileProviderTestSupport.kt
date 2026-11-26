package com.polar.polarsensordatacollector.testutil

import androidx.core.content.FileProvider

/** Clears the static FileProvider cache. Each Robolectric test has its own data directory. */
object FileProviderTestSupport {
    fun reset() {
        val cache = FileProvider::class.java.getDeclaredField("sCache").apply { isAccessible = true }
        (cache.get(null) as MutableMap<*, *>).clear()
    }
}
