package com.polar.polarsensordatacollector.crypto

import javax.crypto.SecretKey

interface SecretKeyManager {
    fun generateKey(alias: String)
    fun getSecretKey(alias: String): SecretKey?
    fun hasKey(alias: String): Boolean
    fun removeKey(alias: String)
}