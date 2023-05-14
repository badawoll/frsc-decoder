package com.badawoll

import org.junit.jupiter.api.Test

import org.junit.jupiter.api.Assertions.*

class FRSCContainerTest {

    @Test
    fun size() {
        val dataMap = mutableMapOf<Int, String>()
        dataMap[0] = "test"
        dataMap[1] = "test2"
        dataMap[5] = "test3"

        val container = FRSCContainer(dataMap)
        assertEquals(3, container.size)

        container[2] = "test4"
        assertEquals(4, container.size)

        assertFalse(container.isEmpty)
    }

    @Test
    fun dump() {
        val dataMap = mapOf(
            0 to "test",
            1 to "test2",
            5 to "test3"
        )
        val container = FRSCContainer(dataMap)
        val bytes = container.dump()

        val newContainer = FRSCContainer(bytes)
        assertEquals(3, newContainer.size)
        assertEquals("test", newContainer[0])
        assertEquals("test2", newContainer[1])
        assertEquals("test3", newContainer[5])
    }
}
