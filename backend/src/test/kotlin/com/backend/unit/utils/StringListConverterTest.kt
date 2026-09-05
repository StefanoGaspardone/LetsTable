package com.backend.unit.utils

import com.backend.utils.StringListConverter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class StringListConverterTest {

    private lateinit var converter: StringListConverter

    @BeforeEach
    fun setUp() {
        converter = StringListConverter()
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    inner class ConvertToDatabaseColumnTests {

        @Test
        fun `should return null when attribute is null`() {
            val result = converter.convertToDatabaseColumn(null)
            assertNull(result)
        }

        @Test
        fun `should return empty string when attribute is empty list`() {
            val result = converter.convertToDatabaseColumn(emptyList())
            assertEquals("", result)
        }

        @Test
        fun `should return single item string when attribute has one element`() {
            val list = listOf("BoardGameGeek")

            val result = converter.convertToDatabaseColumn(list)

            assertEquals("BoardGameGeek", result)
        }

        @Test
        fun `should join multiple items with pipe separator`() {
            val list = listOf("Strategy", "Family", "Card Game")

            val result = converter.convertToDatabaseColumn(list)

            assertEquals("Strategy|Family|Card Game", result)
        }

        @Test
        fun `should handle empty strings and whitespace inside the list`() {
            val list = listOf("First", "", "   ", "Last")

            val result = converter.convertToDatabaseColumn(list)

            assertEquals("First||   |Last", result)
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    inner class ConvertToEntityAttributeTests {

        @Test
        fun `should return empty list when dbData is null`() {
            val result = converter.convertToEntityAttribute(null)
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list when dbData is empty string`() {
            val result = converter.convertToEntityAttribute("")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should return empty list when dbData is blank string`() {
            val result = converter.convertToEntityAttribute("     ")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should parse single item string into single element list`() {
            val dbData = "Eurogame"

            val result = converter.convertToEntityAttribute(dbData)

            assertEquals(1, result.size)
            assertEquals("Eurogame", result[0])
        }

        @Test
        fun `should split multiple pipe separated items into list`() {
            val dbData = "Cooperative|Deck Building|Worker Placement"

            val result = converter.convertToEntityAttribute(dbData)

            assertEquals(3, result.size)
            assertEquals("Cooperative", result[0])
            assertEquals("Deck Building", result[1])
            assertEquals("Worker Placement", result[2])
        }

        @Test
        fun `should preserve empty components when present between pipes`() {
            val dbData = "Tag1||Tag3"

            val result = converter.convertToEntityAttribute(dbData)

            assertEquals(3, result.size)
            assertEquals("Tag1", result[0])
            assertEquals("", result[1])
            assertEquals("Tag3", result[2])
        }
    }
}