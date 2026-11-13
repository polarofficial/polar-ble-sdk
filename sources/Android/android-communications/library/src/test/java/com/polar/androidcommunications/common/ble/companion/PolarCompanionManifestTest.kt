// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class PolarCompanionManifestTest {

    @Test
    fun `library manifests do not register a companion service`() {
        val manifests = File("src").walkTopDown()
            .filter { it.isFile && it.name == "AndroidManifest.xml" }
            .toList()
        assertTrue("Expected at least the main library manifest", manifests.isNotEmpty())

        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        for (manifest in manifests) {
            val document = factory.newDocumentBuilder().parse(manifest)
            val services = document.getElementsByTagName("service")
            for (index in 0 until services.length) {
                val service = services.item(index) as Element
                assertFalse(
                    "SDK service must be explicitly registered by the host app: $manifest",
                    service.getAttributeNS(ANDROID_NAMESPACE, "name") ==
                        "com.polar.androidcommunications.common.ble.companion.PolarCompanionDeviceService"
                )
                val actions = service.getElementsByTagName("action")
                for (actionIndex in 0 until actions.length) {
                    val action = actions.item(actionIndex) as Element
                    assertFalse(
                        "Library must not add a companion service, even under a new name: $manifest",
                        action.getAttributeNS(ANDROID_NAMESPACE, "name") ==
                            "android.companion.CompanionDeviceService"
                    )
                }
            }
        }
    }

    private companion object {
        const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
    }
}
