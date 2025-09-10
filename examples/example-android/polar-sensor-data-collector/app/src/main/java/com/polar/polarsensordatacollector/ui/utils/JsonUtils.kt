package com.polar.polarsensordatacollector.ui.utils

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser

object JsonUtils {
    // This is needed because Protobuf Lite does not support official JSON serialization and Gson serialization alone causes lots of clutter.
    fun cleanProtoJson(jsonData: String, gson: Gson): String {
        val jsonTree = JsonParser.parseString(jsonData)

        val elementStack = mutableListOf<Pair<JsonElement, JsonElement?>>()
        elementStack.add(jsonTree to null)

        while (elementStack.isNotEmpty()) {
            val (element, _) = elementStack.removeAt(elementStack.size - 1)

            if (element.isJsonObject) {
                val obj = element.asJsonObject

                obj.remove("unknownFields")
                val keysToRemove = obj.keySet().filter {
                    it.contains("memoized", ignoreCase = true) || it.startsWith("bitField")
                }
                keysToRemove.forEach { obj.remove(it) }

                val keysWithUnderscore = obj.keySet().filter { it.endsWith("_") }
                for (key in keysWithUnderscore) {
                    val value = obj.remove(key)
                    if (value != null) {
                        val newKey = key.removeSuffix("_")
                        if (!obj.has(newKey)) {
                            obj.add(newKey, value)
                        }
                    }
                }

                for ((_, value) in obj.entrySet()) {
                    elementStack.add(value to obj)
                }
            } else if (element.isJsonArray) {
                val arr = element.asJsonArray
                for (i in 0 until arr.size()) {
                    elementStack.add(arr[i] to arr)
                }
            }
        }

        return gson.toJson(jsonTree)
    }
}