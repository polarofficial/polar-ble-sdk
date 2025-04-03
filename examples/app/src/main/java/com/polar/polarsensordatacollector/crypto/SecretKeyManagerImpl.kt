package com.polar.polarsensordatacollector.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject

class SecretKeyManagerImpl @Inject constructor(@ApplicationContext private val appContext: Context) : SecretKeyManager {
    companion object {
        private const val TAG = "SecretKeyManagerImpl"
    }

    private val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
    private val sharedPreferences = EncryptedSharedPreferences.create(
        "PreferencesFilename",
        masterKeyAlias,
        appContext,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    override fun generateKey(alias: String) {
        if (!hasKey(alias)) {
            val keygen = KeyGenerator.getInstance("AES")
            keygen.init(128)
            val key: SecretKey = keygen.generateKey()

            val saveThis: String = Base64.encodeToString(key.encoded, Base64.DEFAULT)

            sharedPreferences
                .edit()
                .putString(alias, saveThis)
                .apply()
            Log.d(TAG, "Generate key. First 8 bytes of the key are ${key.encoded.take(8).joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }}")

        } else {
            Log.d(TAG, "Generate key. Key already exist for alias $alias")
        }
    }

    override fun getSecretKey(alias: String): SecretKey? {
        val value = sharedPreferences.getString(alias, "")
        val array: ByteArray = Base64.decode(value, Base64.DEFAULT)
        return if (array.isNotEmpty()) {
            Log.d(TAG, "Get secret key for alias $alias: First 8 bytes of the key are ${array.take(8).joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }}")
            SecretKeySpec(array, "AES")
        } else {
            Log.d(TAG, "Get secret key for alias $alias: no getSecretKey")
            null
        }
    }

    override fun hasKey(alias: String): Boolean {
        return sharedPreferences.contains(alias)
    }

    override fun removeKey(alias: String) {
        Log.d(TAG, "Remove secret key for alias $alias")
        sharedPreferences
            .edit()
            .remove(alias)
            .apply()
    }
}