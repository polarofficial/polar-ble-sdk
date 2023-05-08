package com.polar.sdk.api.model

import protocol.PftpResponse

/**
 * Disk space data in bytes.
 */
data class PolarDiskSpaceData(
    val totalSpace: Long,
    val freeSpace: Long
) {
    companion object {
        fun fromProto(proto: PftpResponse.PbPFtpDiskSpaceResult): PolarDiskSpaceData {
            return PolarDiskSpaceData(
                totalSpace = proto.fragmentSize * proto.totalFragments,
                freeSpace = proto.fragmentSize * proto.freeFragments
            )
        }
    }
}