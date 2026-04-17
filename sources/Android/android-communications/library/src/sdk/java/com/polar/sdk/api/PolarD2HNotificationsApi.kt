// Copyright © 2026 Polar Electro Oy. All rights reserved.
package com.polar.sdk.api

import kotlinx.coroutines.flow.Flow

/**
 * Enum representing all possible device-to-host notification types.
 * Device to host notifications are used to inform client apps of important device state
 * changes, such as negotiating data syncing.
 */
enum class PolarDeviceToHostNotification(val value: Int) {
    /** (Not used currently for anything. Reserved for future use.) */
    FILESYSTEM_MODIFIED(0),

    /** Used to inform host about internal test data. */
    INTERNAL_TEST_EVENT(1),

    /** Used to inform host when the device is ready to communicate again after reporting WAIT_FOR_IDLING error. */
    IDLING(2),

    /** Used to inform host about device's battery status. */
    BATTERY_STATUS(3),

    /** Used to inform host about user's inactivity. */
    INACTIVITY_ALERT(4),

    /** Used to inform host about training session status. */
    TRAINING_SESSION_STATUS(5),

    /** Used by device to request host to sync. This happens for example when user presses "Sync" button in the device. */
    SYNC_REQUIRED(7),

    /** Used by device to inform result of START_AUTOSYNC synchronization. */
    AUTOSYNC_STATUS(8),

    /** Used to send responses to Polar Notification Service notifications. */
    PNS_DH_NOTIFICATION_RESPONSE(9),

    /** Used for Polar Notification Service settings */
    PNS_SETTINGS(10),

    /** Used to request mobile device to start GPS measurement. Parameter PbPftpStartGPSMeasurement */
    START_GPS_MEASUREMENT(11),

    /** Used to request mobile device to stop GPS measurement. No parameters */
    STOP_GPS_MEASUREMENT(12),

    /** Used to keep mobile running in background. No parameters */
    KEEP_BACKGROUND_ALIVE(13),

    /** Polar shell is to transfer any test related data from device to host */
    POLAR_SHELL_DH_DATA(14),

    /** Request information from media player */
    MEDIA_CONTROL_REQUEST_DH(15),

    /** Send command for media player */
    MEDIA_CONTROL_COMMAND_DH(16),

    /** Used for informing host when device wants to receive media control data */
    MEDIA_CONTROL_ENABLED(17),

    /** Generic REST API event */
    REST_API_EVENT(18),

    /** Used to inform host about exercise status */
    EXERCISE_STATUS(19);

    companion object {
        fun fromValue(value: Int): PolarDeviceToHostNotification? {
            return values().find { it.value == value }
        }
    }
}

/**
 * Data class representing a received device-to-host notification.
 *
 * @property notificationType The type of notification
 * @property parameters Raw parameter data as ByteArray
 * @property parsedParameters Optional parsed parameter object (if parsing was successful)
 */
data class PolarD2HNotificationData(
    val notificationType: PolarDeviceToHostNotification,
    val parameters: ByteArray,
    val parsedParameters: Any?
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as PolarD2HNotificationData

        if (notificationType != other.notificationType) return false
        if (!parameters.contentEquals(other.parameters)) return false
        if (parsedParameters != other.parsedParameters) return false

        return true
    }

    override fun hashCode(): Int {
        var result = notificationType.hashCode()
        result = 31 * result + parameters.contentHashCode()
        result = 31 * result + (parsedParameters?.hashCode() ?: 0)
        return result
    }
}

/**
 * API for receiving device-to-host notifications from Polar devices.
 *
 * Device to host notifications are used to inform client apps of important device state
 * changes, such as negotiating data syncing, battery status updates, and training session changes.
 */
interface PolarDeviceToHostNotificationsApi {
    /**
    * Streams received device-to-host notifications endlessly.
     * Only cancellation or terminal operators stop the stream.
     *
     * @param identifier Polar device ID or BT address
     * @return [Flow] stream of [PolarD2HNotificationData]
     * Emits values after successfully received notifications.
     * onError, see [BlePsFtpException], [BleGattException]
     */
    fun observeDeviceToHostNotifications(identifier: String): Flow<PolarD2HNotificationData>
}
