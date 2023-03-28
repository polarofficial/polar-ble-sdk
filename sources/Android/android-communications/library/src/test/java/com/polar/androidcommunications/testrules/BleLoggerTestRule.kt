package com.polar.androidcommunications.testrules

import com.polar.androidcommunications.api.ble.BleLogger
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

class BleLoggerTestRule : TestRule {

    private val bleLoggerInterface = object : BleLogger.BleLoggerInterface {
        override fun d(tag: String, msg: String) {
            println("$tag: $msg")
        }

        override fun e(tag: String, msg: String) {
            println("$tag: $msg")
        }

        override fun w(tag: String, msg: String) {
            println("$tag: $msg")
        }

        override fun i(tag: String, msg: String) {
            println("$tag: $msg")
        }

        override fun d_hex(tag: String, msg: String, data: ByteArray) {
            println("$tag/$msg hex: ${data.joinToString(" ") { "%02x".format(it) }}")
        }
    }

    override fun apply(base: Statement, description: Description?): Statement {
        return object : Statement() {
            @Throws(Throwable::class)
            override fun evaluate() {
                // Before test
                BleLogger.setLoggerInterface(bleLoggerInterface)
                try {
                    // Run tests
                    base.evaluate()
                } finally {
                    //After test
                    BleLogger.setLoggerInterface(null)
                }
            }
        }
    }
}
