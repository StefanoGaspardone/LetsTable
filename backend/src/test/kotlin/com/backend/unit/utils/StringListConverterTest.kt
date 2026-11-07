package com.backend.unit.utils

import com.backend.utils.StringListConverter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

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
            assertNull(converter.convertToDatabaseColumn(null))
        }

        @Test
        fun `should return empty string when attribute is empty list`() {
            assertEquals("", converter.convertToDatabaseColumn(emptyList()))
        }

        @Test
        fun `should return single item string when attribute has one element`() {
            assertEquals("Uwe Rosenberg", converter.convertToDatabaseColumn(listOf("Uwe Rosenberg")))
        }

        @Test
        fun `should join multiple items with pipe separator`() {
            assertEquals("Strategy|Family|Card Game", converter.convertToDatabaseColumn(listOf("Strategy", "Family", "Card Game")))
        }

        @Test
        fun `should keep empty strings and whitespace inside the list`() {
            assertEquals("First||   |Last", converter.convertToDatabaseColumn(listOf("First", "", "   ", "Last")))
        }

        @Test
        fun `should escape pipes inside an item`() {
            assertEquals("Pandemic \\| Legacy|Z-Man", converter.convertToDatabaseColumn(listOf("Pandemic | Legacy", "Z-Man")))
        }

        @Test
        fun `should escape backslashes inside an item`() {
            assertEquals("Back\\\\slash", converter.convertToDatabaseColumn(listOf("Back\\slash")))
        }
    }

    @Nested
    @DisplayName("convertToEntityAttribute")
    inner class ConvertToEntityAttributeTests {

        @Test
        fun `should return empty list when dbData is null`() {
            assertTrue(converter.convertToEntityAttribute(null).isEmpty())
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "     ", "\n"])
        fun `should return empty list when dbData is blank`(dbData: String) {
            assertTrue(converter.convertToEntityAttribute(dbData).isEmpty())
        }

        @Test
        fun `should parse single item string into single element list`() {
            assertEquals(listOf("Eurogame"), converter.convertToEntityAttribute("Eurogame"))
        }

        @Test
        fun `should split multiple pipe separated items into list`() {
            assertEquals(
                listOf("Cooperative", "Deck Building", "Worker Placement"),
                converter.convertToEntityAttribute("Cooperative|Deck Building|Worker Placement"),
            )
        }

        @Test
        fun `should preserve empty components when present between pipes`() {
            assertEquals(listOf("Tag1", "", "Tag3"), converter.convertToEntityAttribute("Tag1||Tag3"))
        }

        @Test
        fun `should not split on escaped pipes`() {
            assertEquals(listOf("Pandemic | Legacy", "Z-Man"), converter.convertToEntityAttribute("Pandemic \\| Legacy|Z-Man"))
        }

        @Test
        fun `should keep a trailing lone backslash`() {
            assertEquals(listOf("Broken\\"), converter.convertToEntityAttribute("Broken\\"))
        }

        @Test
        fun `should read values stored before escaping was introduced`() {
            assertEquals(
                listOf("Mathias Wigge", "Feuerland Spiele"),
                converter.convertToEntityAttribute("Mathias Wigge|Feuerland Spiele"),
            )
        }
    }

    @Nested
    @DisplayName("round trip")
    inner class RoundTripTests {

        @Test
        fun `should read back exactly what it writes`() {
            val list = listOf(
                "Mathias Wigge",
                "Loïc Billiau",
                "Pandemic | Legacy",
                "Back\\slash",
                "Ends with backslash\\",
                "\\|",
                "",
                "方舟动物园",
            )

            assertEquals(list, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(list)))
        }

        @Test
        fun `should read back an empty list as empty`() {
            assertTrue(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(emptyList())).isEmpty())
        }
    }
}