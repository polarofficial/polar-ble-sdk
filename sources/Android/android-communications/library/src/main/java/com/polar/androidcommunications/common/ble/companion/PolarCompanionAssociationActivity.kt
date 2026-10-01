// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.androidcommunications.common.ble.companion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.os.Bundle
import com.polar.androidcommunications.api.ble.BleLogger

/**
 * Invisible trampoline [Activity], declared in the SDK's own manifest, that launches the
 * Companion Device Manager consent chooser without requiring the host app to register its own
 * `ActivityResultLauncher`.
 */
class PolarCompanionAssociationActivity : Activity() {

    companion object {
        private const val TAG = "PolarCompanionAssociationActivity"
        private const val EXTRA_INTENT_SENDER = "com.polar.androidcommunications.EXTRA_INTENT_SENDER"
        private const val REQUEST_CODE = 8341

        internal fun createIntent(context: Context, intentSender: IntentSender): Intent =
            Intent(context, PolarCompanionAssociationActivity::class.java)
                .putExtra(EXTRA_INTENT_SENDER, intentSender)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val intentSender = intent?.getParcelableExtra<IntentSender>(EXTRA_INTENT_SENDER)
        if (intentSender == null) {
            BleLogger.e(TAG, "Missing IntentSender extra, finishing")
            finish()
            return
        }
        try {
            @Suppress("DEPRECATION")
            startIntentSenderForResult(intentSender, REQUEST_CODE, null, 0, 0, 0)
        } catch (e: IntentSender.SendIntentException) {
            BleLogger.e(TAG, "Failed to launch companion association chooser: ${e.message}")
            PolarCompanionAssociationResultBus.notifyAssociationFlowFinished()
            finish()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE) {
            PolarCompanionAssociationResultBus.notifyAssociationFlowFinished()
        }
        finish()
    }
}


