package com.backend.unit.utils

import com.backend.models.entities.ExpansionRef
import com.backend.utils.ExpansionRefListConverter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class ExpansionRefListConverterTest {

    private lateinit var converter: ExpansionRefListConverter

    @BeforeEach
    fun setUp() {
        converter = ExpansionRefListConverter()
    }

    @Nested
    @DisplayName("convertToDatabaseColumn")
    inner class ConvertToDatabaseColumnTests {

        @Test
        fun `should return null when input list is null`() {
            assertNull(converter.convertToDatabaseColumn(null))
        }

        @Test
        fun `should return empty string when input list is empty`() {
            assertEquals("", converter.convertToDatabaseColumn(emptyList()))
        }

        @Test
        fun `should convert single expansion ref to formatted string`() {
            val result = converter.convertToDatabaseColumn(listOf(ExpansionRef(12345L, "Wingspan: European Expansion")))

            assertEquals("12345::Wingspan: European Expansion", result)
        }

        @Test
        fun `should convert multiple expansion refs separated by pipe`() {
            val list = listOf(
                ExpansionRef(101L, "Expansion A"),
                ExpansionRef(202L, "Expansion B"),
                ExpansionRef(303L, "Expansion C"),
            )

            assertEquals("101::Expansion A|202::Expansion B|303::Expansion C", converter.convertToDatabaseColumn(list))
        }

        @Test
        fun `should keep double colons inside the name`() {
            assertEquals("999::Game::Special Edition", converter.convertToDatabaseColumn(listOf(ExpansionRef(999L, "Game::Special Edition"))))
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
        @ValueSource(strings = ["", "   ", "\n"])
        fun `should return empty list when dbData is blank`(dbData: String) {
            assertTrue(converter.convertToEntityAttribute(dbData).isEmpty())
        }

        @Test
        fun `should parse valid single expansion ref string`() {
            val result = converter.convertToEntityAttribute("12345::Wingspan: Oceania Expansion")

            assertEquals(listOf(ExpansionRef(12345L, "Wingspan: Oceania Expansion")), result)
        }

        @Test
        fun `should parse multiple expansion refs in order`() {
            val result = converter.convertToEntityAttribute("101::Expansion A|202::Expansion B|303::Expansion C")

            assertEquals(
                listOf(ExpansionRef(101L, "Expansion A"), ExpansionRef(202L, "Expansion B"), ExpansionRef(303L, "Expansion C")),
                result,
            )
        }

        @Test
        fun `should split only on the first double colon`() {
            val result = converter.convertToEntityAttribute("555::Game::SubTitle::Edition")

            assertEquals(listOf(ExpansionRef(555L, "Game::SubTitle::Edition")), result)
        }

        @Test
        fun `should ignore entries without separator`() {
            assertTrue(converter.convertToEntityAttribute("invalidEntryFormat").isEmpty())
        }

        @Test
        fun `should ignore entries with non-numeric bggId`() {
            assertTrue(converter.convertToEntityAttribute("abc::Expansion Name").isEmpty())
        }

        @Test
        fun `should ignore empty segments between pipes`() {
            val result = converter.convertToEntityAttribute("|101::Valid A||202::Valid B|")

            assertEquals(listOf(101L, 202L), result.map { it.bggId })
        }

        @Test
        fun `should filter out invalid entries and parse valid ones in mixed string`() {
            val result = converter.convertToEntityAttribute("101::Valid A|invalidEntry|abc::BadId|202::Valid B|303::")

            assertEquals(
                listOf(ExpansionRef(101L, "Valid A"), ExpansionRef(202L, "Valid B"), ExpansionRef(303L, "")),
                result,
            )
        }

        @Test
        fun `should keep the id when a stored name contained a pipe`() {
            val stored = converter.convertToDatabaseColumn(listOf(ExpansionRef(101L, "Pandemic | Legacy"), ExpansionRef(202L, "Expansion B")))

            val result = converter.convertToEntityAttribute(stored)

            assertEquals(listOf(101L, 202L), result.map { it.bggId })
            assertEquals("Pandemic ", result[0].name)
        }
    }

    @Nested
    @DisplayName("round trip")
    inner class RoundTripTests {

        @Test
        fun `should read back exactly what it writes`() {
            val list = listOf(
                ExpansionRef(400000L, "Ark Nova: Marine Worlds"),
                ExpansionRef(400001L, "Ark Nova – Zoo Map Pack 1"),
                ExpansionRef(400002L, "Arche Nova: Meereswelten"),
                ExpansionRef(400003L, "Game::Special Edition"),
                ExpansionRef(400004L, "方舟动物园"),
            )

            assertEquals(list, converter.convertToEntityAttribute(converter.convertToDatabaseColumn(list)))
        }

        @Test
        fun `should read back an empty list as empty`() {
            assertTrue(converter.convertToEntityAttribute(converter.convertToDatabaseColumn(emptyList())).isEmpty())
        }
    }
}