package com.polar.sdk.api.model

data class Errorlog(
    val errorLog: ByteArray
) {
    companion object {
        const val ERRORLOG_FILENAME = "/ERRORLOG.BPB"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Errorlog

        return errorLog.contentEquals(other.errorLog)
    }

    override fun hashCode(): Int {
        return errorLog.contentHashCode()
    }
}