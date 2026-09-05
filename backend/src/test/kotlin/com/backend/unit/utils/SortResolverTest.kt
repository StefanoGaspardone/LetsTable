package com.backend.unit.utils

import com.backend.exceptions.InvalidSortException
import com.backend.utils.resolveSort
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SortResolverTest {

    private val allowedFields = setOf("name", "createdAt", "rating", "complexity")
    private val defaultField = "createdAt"

    @Nested
    @DisplayName("Default & Null Handling")
    inner class DefaultHandlingTests {

        @Test
        fun `should return default field descending when sort parameter is null`() {
            val result = resolveSort(null, allowedFields, defaultField)

            val order = result.getOrderFor(defaultField)
            assertNotNull(order)
            assertTrue(order!!.isDescending)
        }

        @Test
        fun `should return default field descending when sort parameter is empty string`() {
            assertThrows<InvalidSortException> {
                resolveSort("", allowedFields, defaultField)
            }
        }
    }

    @Nested
    @DisplayName("Direction Parsing")
    inner class DirectionParsingTests {

        @Test
        fun `should parse ASC direction correctly`() {
            val result = resolveSort("name-asc", allowedFields, defaultField)

            val order = result.getOrderFor("name")
            assertNotNull(order)
            assertTrue(order!!.isAscending)
        }

        @Test
        fun `should parse DESC direction correctly`() {
            val result = resolveSort("rating-desc", allowedFields, defaultField)

            val order = result.getOrderFor("rating")
            assertNotNull(order)
            assertTrue(order!!.isDescending)
        }

        @Test
        fun `should handle lowercase and mixed case direction parameters`() {
            val resultAsc = resolveSort("complexity-Asc", allowedFields, defaultField)
            val resultDesc = resolveSort("complexity-dEsC", allowedFields, defaultField)

            assertTrue(resultAsc.getOrderFor("complexity")!!.isAscending)
            assertTrue(resultDesc.getOrderFor("complexity")!!.isDescending)
        }

        @Test
        fun `should fallback to DESC when direction is missing`() {
            val result = resolveSort("name", allowedFields, defaultField)

            val order = result.getOrderFor("name")
            assertNotNull(order)
            assertTrue(order!!.isDescending)
        }

        @Test
        fun `should fallback to DESC when direction is invalid string`() {
            val result = resolveSort("name-invalidDirection", allowedFields, defaultField)

            val order = result.getOrderFor("name")
            assertNotNull(order)
            assertTrue(order!!.isDescending)
        }
    }

    @Nested
    @DisplayName("Allowed Fields & Validation")
    inner class ValidationTests {

        @Test
        fun `should throw InvalidSortException when field is not in allowedFields`() {
            val exception = assertThrows<InvalidSortException> {
                resolveSort("unauthorizedField-asc", allowedFields, defaultField)
            }

            assertEquals("Invalid sort field: unauthorizedField", exception.message)
        }

        @Test
        fun `should accept field when it is in allowedFields`() {
            assertDoesNotThrow {
                resolveSort("rating-asc", allowedFields, defaultField)
            }
        }
    }

    @Nested
    @DisplayName("Edge Cases & Hyphen Splitting")
    inner class EdgeCasesTests {

        @Test
        fun `should handle multiple hyphens by taking second part as direction`() {
            val result = resolveSort("name-asc-extra", allowedFields, defaultField)

            val order = result.getOrderFor("name")
            assertNotNull(order)
            assertTrue(order!!.isAscending)
        }

        @Test
        fun `should throw exception if defaultField itself is not in allowedFields when sort is invalid`() {
            val restrictedAllowedFields = setOf("name", "rating")

            val exception = assertThrows<InvalidSortException> {
                resolveSort("unknown-asc", restrictedAllowedFields, "createdAt")
            }

            assertEquals("Invalid sort field: unknown", exception.message)
        }
    }
}