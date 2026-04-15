package com.polar.androidcommunications.common.ble

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AtomicSetTest {

    private lateinit var sut: AtomicSet<String>

    @Before
    fun setUp() {
        sut = AtomicSet()
    }

    @Test
    fun add_whenItemIsNew_returnsTrueAndIncreasesSize() {
        // Act
        val result = sut.add("item1")

        // Assert
        assertTrue(result)
        assertEquals(1, sut.size())
    }

    @Test
    fun add_whenItemAlreadyExists_returnsFalseAndSizeUnchanged() {
        // Arrange
        sut.add("item1")

        // Act
        val result = sut.add("item1")

        // Assert
        assertFalse(result)
        assertEquals(1, sut.size())
    }

    @Test
    fun add_whenItemIsNull_returnsFalseAndSizeUnchanged() {
        // Act
        val result = sut.add(null)

        // Assert
        assertFalse(result)
        assertEquals(0, sut.size())
    }

    @Test
    fun add_whenMultipleDistinctItems_addsAll() {
        // Act
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Assert
        assertEquals(3, sut.size())
    }

    @Test
    fun remove_whenItemExists_removesItemAndDecreasesSize() {
        // Arrange
        sut.add("item1")

        // Act
        sut.remove("item1")

        // Assert
        assertEquals(0, sut.size())
        assertFalse(sut.contains("item1"))
    }

    @Test
    fun remove_whenItemDoesNotExist_doesNotChangeSize() {
        // Arrange
        sut.add("item1")

        // Act
        sut.remove("nonExistent")

        // Assert
        assertEquals(1, sut.size())
    }

    @Test
    fun remove_whenItemIsNull_doesNotChangeSize() {
        // Arrange
        sut.add("item1")

        // Act
        sut.remove(null)

        // Assert
        assertEquals(1, sut.size())
    }

    // endregion

    // region clear

    @Test
    fun clear_whenItemsExist_removesAllItems() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Act
        sut.clear()

        // Assert
        assertEquals(0, sut.size())
    }

    @Test
    fun clear_whenEmpty_remainsEmpty() {
        // Act
        sut.clear()

        // Assert
        assertEquals(0, sut.size())
    }

    // endregion

    // region contains

    @Test
    fun contains_whenItemExists_returnsTrue() {
        // Arrange
        sut.add("item1")

        // Act
        val result = sut.contains("item1")

        // Assert
        assertTrue(result)
    }

    @Test
    fun contains_whenItemDoesNotExist_returnsFalse() {
        // Act
        val result = sut.contains("nonExistent")

        // Assert
        assertFalse(result)
    }

    @Test
    fun contains_whenSetIsEmpty_returnsFalse() {
        // Act
        val result = sut.contains("item1")

        // Assert
        assertFalse(result)
    }

    @Test
    fun contains_whenItemRemovedAfterAdding_returnsFalse() {
        // Arrange
        sut.add("item1")
        sut.remove("item1")

        // Act
        val result = sut.contains("item1")

        // Assert
        assertFalse(result)
    }

    @Test
    fun size_whenEmpty_returnsZero() {
        // Act & Assert
        assertEquals(0, sut.size())
    }

    @Test
    fun size_afterAddingItems_returnsCorrectCount() {
        // Arrange
        sut.add("item1")
        sut.add("item2")

        // Act & Assert
        assertEquals(2, sut.size())
    }

    @Test
    fun size_afterClear_returnsZero() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.clear()

        // Act & Assert
        assertEquals(0, sut.size())
    }

    @Test
    fun objects_whenItemsExist_returnsSetOfAllItems() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Act
        val result = sut.objects()

        // Assert
        assertEquals(3, result.size)
        assertTrue(result.contains("item1"))
        assertTrue(result.contains("item2"))
        assertTrue(result.contains("item3"))
    }

    @Test
    fun objects_whenEmpty_returnsEmptySet() {
        // Act
        val result = sut.objects()

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun objects_returnsSnapshot_notAffectedBySubsequentRemove() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        val snapshot = sut.objects()

        // Act
        sut.remove("item1")

        // Assert
        assertEquals(2, snapshot.size)
        assertTrue(snapshot.contains("item1"))
    }

    @Test
    fun accessAll_whenItemsExist_visitsAllItems() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")
        val visited = mutableListOf<Any>()

        // Act
        sut.accessAll<String> { visited.add(it) }

        // Assert
        assertEquals(3, visited.size)
        assertTrue(visited.contains("item1"))
        assertTrue(visited.contains("item2"))
        assertTrue(visited.contains("item3"))
    }

    @Test
    fun accessAll_whenEmpty_doesNotInvokeCallback() {
        // Arrange
        var callCount = 0

        // Act
        sut.accessAll<String> { callCount++ }

        // Assert
        assertEquals(0, callCount)
    }

    @Test
    fun fetch_whenMatchingItemExists_returnsItem() {
        // Arrange
        sut.add("item1")
        sut.add("item2")

        // Act
        val result = sut.fetch { it == "item1" }

        // Assert
        assertEquals("item1", result)
    }

    @Test
    fun fetch_whenNoMatchingItem_returnsNull() {
        // Arrange
        sut.add("item1")
        sut.add("item2")

        // Act
        val result = sut.fetch { it == "nonExistent" }

        // Assert
        assertNull(result)
    }

    @Test
    fun fetch_whenEmpty_returnsNull() {
        // Act
        val result = sut.fetch { it == "item1" }

        // Assert
        assertNull(result)
    }

    @Test
    fun fetch_whenMultipleMatchingItems_returnsOneOfThem() {
        // Arrange
        sut.add("item1")
        sut.add("item2")
        sut.add("item3")

        // Act
        val result = sut.fetch { (it as String).startsWith("item") }

        // Assert
        assertTrue((result as String).startsWith("item"))
    }
}

