// Copyright © 2022 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api.model

import androidx.annotation.Size
import javax.crypto.spec.SecretKeySpec

/**
 * Polar recording secret is used to encrypt the recording.
 */
class PolarRecordingSecret(
    /**
     * Secret key of size 16 bytes. Supported encryption is AES_128
     */
    @Size(16) key: ByteArray
) {
    val secret: SecretKeySpec = SecretKeySpec(key, "AES")

    init {
        require(key.size == 16) { "key must be size of 16 bytes (128bits), was ${key.size}" }
    }
}