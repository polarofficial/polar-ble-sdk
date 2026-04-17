package com.polar.androidcommunications.common.ble

class AtomicSet<Any : kotlin.Any> {

    private val items: MutableList<Any> = mutableListOf()

    fun interface CompareFunction<Any> {
        fun compare(`object`: Any): Boolean
    }

    @Synchronized
    fun clear() {
        items.clear()
    }

    @Synchronized
    fun add(`object`: Any?): Boolean {
        if (`object` != null && !items.contains(`object`)) {
            items.add(`object`)
            return true
        }
        return false
    }

    @Synchronized
    fun <T: Any> remove(`object`: T?) {
        if (`object` != null) {
            items.remove(`object`)
        }
    }

    @Synchronized
    fun <T: Any> accessAll(`object`: (kotlin.Any) -> Unit) {
        for (i in items.size - 1 downTo 0) {
            `object`(items[i])
        }
    }

    @Synchronized
    fun fetch(compareFunction: CompareFunction<Any?>): Any? {
        for (i in items.size - 1 downTo 0) {
            if (compareFunction.compare(items[i])) {
                return items[i]
            }
        }
        return null
    }

    @Synchronized
    fun objects(): Set<Any> {
        return HashSet(items)
    }

    @Synchronized
    fun size(): Int {
        return items.size
    }

    @Synchronized
    fun contains(`object`: Any): Boolean {
        return items.contains(`object`)
    }
}