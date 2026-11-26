package com.polar.polarsensordatacollector.ui.utils

import com.google.gson.Gson
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonUtilsTest {

    private val gson = Gson()

    private fun clean(json: String) = JsonParser.parseString(JsonUtils.cleanProtoJson(json, gson)).asJsonObject

    @Test
    fun `clean proto json removes proto bookkeeping keys`() {
        // Arrange
        val json = """{"unknownFields":{},"memoizedSize":1,"memoizedHashCode":2,"bitField0_":3,"name":"a"}"""

        // Act
        val result = clean(json)

        // Assert
        assertEquals(setOf("name"), result.keySet())
    }

    @Test
    fun `clean proto json removes trailing underscore from keys`() {
        // Act
        val result = clean("""{"value_":5}""")

        // Assert
        assertEquals(5, result.get("value").asInt)
        assertFalse(result.has("value_"))
    }

    @Test
    fun `clean proto json keeps existing key when underscore key collides`() {
        // Act
        val result = clean("""{"value":1,"value_":2}""")

        // Assert
        assertEquals(1, result.get("value").asInt)
        assertFalse(result.has("value_"))
    }

    @Test
    fun `clean proto json cleans nested objects and arrays`() {
        // Arrange
        val json = """{"items_":[{"id_":1,"memoizedSize":4},{"id_":2}],"child":{"unknownFields":{},"x_":"y"}}"""

        // Act
        val result = clean(json)

        // Assert
        val items = result.getAsJsonArray("items")
        assertEquals(2, items.size())
        assertEquals(1, items[0].asJsonObject.get("id").asInt)
        assertFalse(items[0].asJsonObject.has("memoizedSize"))
        val child = result.getAsJsonObject("child")
        assertEquals("y", child.get("x").asString)
        assertFalse(child.has("unknownFields"))
    }

    @Test
    fun `clean proto json keeps plain json unchanged`() {
        // Act
        val result = clean("""{"a":1,"b":[1,2],"c":{"d":"e"}}""")

        // Assert
        assertTrue(result.has("a") && result.has("b") && result.has("c"))
        assertEquals(2, result.getAsJsonArray("b").size())
    }
}
