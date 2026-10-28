// Copyright © 2026 Polar Electro Oy. All rights reserved.

package com.polar.polarbgsync.internal

/**
 * Decides which SDK API-logger messages the app forwards to logcat. The SDK formats messages as
 * "<tag>/<message>" and prefixes errors with "Error: ". Only metadata is allowed (companion
 * messages, 24/7 samples range/file-name lines and SDK errors) and the SDK session-monitor lifecycle lines; other SDK output is dropped.
 */
internal fun shouldLogSdkMessage(message: String): Boolean =
    message.contains("companion", ignoreCase = true) ||
        message.startsWith("Error: ") ||
        message.startsWith("PolarAutomaticSamplesUtils/read247HrSamples: from ") ||
        message.startsWith("PolarAutomaticSamplesUtils/read247PPiSamples: from ") ||
        message.startsWith("PolarAutomaticSamplesUtils/Sending GET request for file: ") ||
        message.startsWith("BDBleApiImpl/Ignoring replayed SESSION_CLOSED") ||
        message.startsWith("BDBleApiImpl/No active sessions remaining") ||
        message.startsWith("BDBleApiImpl/SESSION_OPEN callback skipped") ||
        message.startsWith("BDBleApiImpl/SESSION_CLOSED callback skipped")
