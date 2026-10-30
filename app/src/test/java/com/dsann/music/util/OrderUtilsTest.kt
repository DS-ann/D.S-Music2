package com.dsann.music.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class OrderUtilsTest {
    @Test
    fun movesItemForwardWithoutChangingOtherRelativeOrder() {
        assertEquals(
            listOf("a", "c", "d", "b"),
            OrderUtils.move(listOf("a", "b", "c", "d"), 1, 3)
        )
    }

    @Test
    fun movesItemBackwardWithoutChangingOtherRelativeOrder() {
        assertEquals(
            listOf("c", "a", "b", "d"),
            OrderUtils.move(listOf("a", "b", "c", "d"), 2, 0)
        )
    }

    @Test
    fun invalidAndSameIndicesAreNoOps() {
        val items = listOf("a", "b", "c")
        assertSame(items, OrderUtils.move(items, -1, 1))
        assertSame(items, OrderUtils.move(items, 1, 3))
        assertSame(items, OrderUtils.move(items, 1, 1))
    }

    @Test
    fun currentIndexFollowsMovedItem() {
        assertEquals(3, OrderUtils.moveIndex(1, 1, 3))
        assertEquals(0, OrderUtils.moveIndex(3, 1, 3))
        assertEquals(2, OrderUtils.moveIndex(2, 1, 3))
    }

    @Test
    fun currentIndexShiftsWhenOtherItemCrossesIt() {
        assertEquals(3, OrderUtils.moveIndex(2, 4, 1))
        assertEquals(1, OrderUtils.moveIndex(4, 4, 1))
        assertEquals(1, OrderUtils.moveIndex(2, 0, 3))
        assertEquals(3, OrderUtils.moveIndex(2, 3, 0))
    }

    @Test
    fun negativeCurrentIndexAndNoOpMovesArePreserved() {
        assertEquals(-1, OrderUtils.moveIndex(-1, 0, 2))
        assertEquals(2, OrderUtils.moveIndex(2, 1, 1))
    }
}
