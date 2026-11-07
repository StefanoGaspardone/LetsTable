package com.backend.unit.utils

import com.backend.exceptions.InvalidSortException
import com.backend.utils.resolveSort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.data.domain.Sort

class SortResolverTest {

    private val allowedFields = setOf("name", "createdAt", "rating", "complexity")
    private val defaultField = "createdAt"

    private fun Sort.single(): Sort.Order = toList().single()

    @Nested
    @DisplayName("default")
    inner class DefaultTests {

        @Test
        fun `should sort by the default field descending when sort is null`() {
            val order = resolveSort(null, allowedFields, defaultField).single()

            assertEquals(defaultField, order.property)
            assertTrue(order.isDescending)
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   "])
        fun `should sort by the default field descending when sort is blank`(sort: String) {
            val order = resolveSort(sort, allowedFields, defaultField).single()

            assertEquals(defaultField, order.property)
            assertTrue(order.isDescending)
        }

        @Test
        fun `should reject a default field that is not allowed`() {
            assertThrows<InvalidSortException> { resolveSort(null, setOf("name"), "createdAt") }
        }
    }

    @Nested
    @DisplayName("direction")
    inner class DirectionTests {

        @Test
        fun `should parse ascending direction`() {
            assertTrue(resolveSort("name-asc", allowedFields, defaultField).single().isAscending)
        }

        @Test
        fun `should parse descending direction`() {
            assertTrue(resolveSort("rating-desc", allowedFields, defaultField).single().isDescending)
        }

        @ParameterizedTest
        @ValueSource(strings = ["complexity-ASC", "complexity-Asc", "complexity-aSc"])
        fun `should ignore the case of the direction`(sort: String) {
            assertTrue(resolveSort(sort, allowedFields, defaultField).single().isAscending)
        }

        @ParameterizedTest
        @ValueSource(strings = ["name", "name-", "name-invalidDirection", "name-up"])
        fun `should fall back to descending when the direction is missing or unknown`(sort: String) {
            val order = resolveSort(sort, allowedFields, defaultField).single()

            assertEquals("name", order.property)
            assertTrue(order.isDescending)
        }

        @Test
        fun `should ignore anything after the direction`() {
            assertTrue(resolveSort("name-asc-extra", allowedFields, defaultField).single().isAscending)
        }
    }

    @Nested
    @DisplayName("validation")
    inner class ValidationTests {

        @Test
        fun `should throw InvalidSortException when field is not in allowedFields`() {
            val exception = assertThrows<InvalidSortException> { resolveSort("unauthorizedField-asc", allowedFields, defaultField) }

            assertEquals("Invalid sort field: unauthorizedField", exception.message)
        }

        @Test
        fun `should treat field names as case-sensitive`() {
            assertThrows<InvalidSortException> { resolveSort("Name-asc", allowedFields, defaultField) }
        }

        @Test
        fun `should reject a sort with an empty field`() {
            assertThrows<InvalidSortException> { resolveSort("-asc", allowedFields, defaultField) }
        }

        @Test
        fun `should accept a nested property when it is allowed`() {
            val order = resolveSort("game.name-asc", setOf("game.name", "createdAt"), defaultField).single()

            assertEquals("game.name", order.property)
            assertTrue(order.isAscending)
        }
    }

    @Nested
    @DisplayName("nulls last")
    inner class NullsLastTests {

        private val nullsLastFields = setOf("rating")

        @ParameterizedTest
        @ValueSource(strings = ["rating-asc", "rating-desc"])
        fun `should put nulls last for the configured fields in both directions`(sort: String) {
            val order = resolveSort(sort, allowedFields, defaultField, nullsLastFields).single()

            assertEquals(Sort.NullHandling.NULLS_LAST, order.nullHandling)
        }

        @Test
        fun `should keep the native null handling for other fields`() {
            val order = resolveSort("name-asc", allowedFields, defaultField, nullsLastFields).single()

            assertEquals(Sort.NullHandling.NATIVE, order.nullHandling)
        }

        @Test
        fun `should apply nulls last to the default field too`() {
            val order = resolveSort(null, allowedFields, "rating", nullsLastFields).single()

            assertEquals(Sort.NullHandling.NULLS_LAST, order.nullHandling)
        }
    }

    @Nested
    @DisplayName("tie breaker")
    inner class TieBreakerTests {

        @Test
        fun `should append the tie breaker after the requested order`() {
            val orders = resolveSort("name-asc", allowedFields, defaultField, tieBreaker = Sort.by(Sort.Order.asc("id"))).toList()

            assertEquals(listOf("name", "id"), orders.map { it.property })
            assertTrue(orders[0].isAscending)
            assertTrue(orders[1].isAscending)
        }

        @Test
        fun `should keep multiple tie breakers in order`() {
            val tieBreaker = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))

            val orders = resolveSort("name-asc", allowedFields, defaultField, tieBreaker = tieBreaker).toList()

            assertEquals(listOf("name", "createdAt", "id"), orders.map { it.property })
        }

        @Test
        fun `should drop a tie breaker on the same field as the requested sort`() {
            val tieBreaker = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.asc("id"))

            val orders = resolveSort("createdAt-asc", allowedFields, defaultField, tieBreaker = tieBreaker).toList()

            assertEquals(listOf("createdAt", "id"), orders.map { it.property })
            assertTrue(orders[0].isAscending)
        }

        @Test
        fun `should append the tie breaker to the default sort too`() {
            val orders = resolveSort(null, allowedFields, "name", tieBreaker = Sort.by("id")).toList()

            assertEquals(listOf("name", "id"), orders.map { it.property })
        }
    }
}