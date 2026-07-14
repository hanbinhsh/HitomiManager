package com.ice.hitomimanager

import com.ice.hitomimanager.domain.util.NaturalOrder
import org.junit.Assert.assertEquals
import org.junit.Test

class NaturalOrderTest {
    @Test
    fun numericSegmentsAreSortedByValue() {
        val input = listOf("page10.jpg", "page2.jpg", "page01.jpg", "page1.jpg")

        val sorted = input.sortedWith(NaturalOrder::compare)

        assertEquals(listOf("page01.jpg", "page1.jpg", "page2.jpg", "page10.jpg"), sorted)
    }

    @Test
    fun nestedPathsUseNaturalOrdering() {
        val input = listOf("chapter10/page1.jpg", "chapter2/page12.jpg", "chapter2/page3.jpg")

        val sorted = input.sortedWith(NaturalOrder::compare)

        assertEquals(
            listOf("chapter2/page3.jpg", "chapter2/page12.jpg", "chapter10/page1.jpg"),
            sorted
        )
    }
}
