package com.polar.androidcommunications.api.ble

import kotlin.experimental.and

class BleLogger {
    interface BleLoggerInterface {
        fun d(tag: String, msg: String)
        fun e(tag: String, msg: String)
        fun w(tag: String, msg: String)
        fun i(tag: String, msg: String)
        fun d_hex(tag: String, msg: String, data: ByteArray)
    }

    private var bleLoggerInterface: BleLoggerInterface? = null
    private val mutex = Any()

    companion object {

        @JvmStatic
        fun byteArrayToHex(a: ByteArray): String {
            val sb = StringBuilder(a.size * 2)
            for (b: Byte in a) {
                sb.append(String.format("%02X ", b and 0xff.toByte()))
            }
            return sb.toString()
        }

        private val instance = BleLogger()

        @JvmStatic
        fun setLoggerInterface(loggerInterface: BleLoggerInterface?) {
            synchronized(instance.mutex) { instance.bleLoggerInterface = loggerInterface }
        }

        @JvmStatic
        fun d_hex(tag: String, msg: String, data: ByteArray) {
            synchronized(instance.mutex) {
                instance.bleLoggerInterface?.d(tag, msg + " HEX: " + byteArrayToHex(data))
            }
        }

        @JvmStatic
        fun d(tag: String, msg: String) {
            synchronized(instance.mutex) {
                instance.bleLoggerInterface?.d(tag, msg)
            }
        }

        @JvmStatic
        fun e(tag: String, msg: String) {
            synchronized(instance.mutex) {
                instance.bleLoggerInterface?.e(tag, msg)
            }
        }

        @JvmStatic
        fun w(tag: String, msg: String) {
            synchronized(instance.mutex) {
                instance.bleLoggerInterface?.w(tag, msg)
            }
        }

        @JvmStatic
        fun i(tag: String, msg: String) {
            synchronized(instance.mutex) {
                instance.bleLoggerInterface?.i(tag, msg)
            }
        }
    }
}