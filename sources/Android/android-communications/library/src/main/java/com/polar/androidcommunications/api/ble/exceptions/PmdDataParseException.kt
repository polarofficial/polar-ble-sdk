package com.polar.androidcommunications.api.ble.exceptions

/**
 * Exception thrown when PMD (Polar Measurement Data) frame parsing fails due to invalid
 * or unexpected device data.
 *
 * Instead of crashing the application, the SDK throws this exception to allow SDK users
 * to capture and report data validity issues gracefully.
 *
 * **SDK users should capture and collect these unexpected errors** via their preferred
 * error reporting or analytics tool rather than solely relying on crash reports.
 * When a stream terminates with this exception it can be restarted if desired.
 *
 * The [message] includes:
 * - the data type that failed to parse (e.g. "ACC", "ECG")
 * - the frame type
 * - a human-readable description of what is invalid
 * - the raw bytes of the violating data **unless the data may contain personally
 *   identifiable information** (e.g. GNSS location) or sensitive health information
 *   (e.g. PPI / offline HR), in which case the raw bytes are omitted.
 */
class PmdDataParseException(message: String) : Exception(message) {
    companion object {
        /**
         * Formats a [ByteArray] as a space-separated uppercase hex string suitable for
         * inclusion in error messages.
         *
         * **Do not call this with data that may contain personally identifiable or sensitive
         * health information** (e.g. GNSS coordinates, HR/PPI samples).
         */
        fun ByteArray.toHex(): String = joinToString(" ") { "%02X".format(it) }
    }
}