package com.polar.sdk.api.model

/**
 * Status for firmware update.
 *
 * @param details, extra details, if any
 */
sealed class FirmwareUpdateStatus(open val details: String = "") {
    data class FetchingFwUpdatePackage(override val details: String = "") : FirmwareUpdateStatus(details)
    data class PreparingDeviceForFwUpdate(override val details: String = "") : FirmwareUpdateStatus(details)
    data class WritingFwUpdatePackage(override val details: String = "") : FirmwareUpdateStatus(details)
    data class FinalizingFwUpdate(override val details: String = "") : FirmwareUpdateStatus(details)
    data class FwUpdateCompletedSuccessfully(override val details: String = "") : FirmwareUpdateStatus(details)
    data class FwUpdateNotAvailable(override val details: String = "") : FirmwareUpdateStatus(details)
    data class FwUpdateFailed(override val details: String = "") : FirmwareUpdateStatus(details)
}

data class PolarFirmwareVersionInfo(
        val deviceFwVersion: String,
        val deviceModelName: String,
        val deviceHardwareCode: String
)