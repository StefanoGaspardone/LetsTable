package com.backend.unit.utils

import com.backend.models.entities.ExpansionRef
import com.backend.utils.ExpansionRefListConverter
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

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
            val result = converter.convertToDatabaseColumn(null)
            assertNull(result)
        }

        @Test
        fun `should return empty string when input list is empty`() {
            val result = converter.convertToDatabaseColumn(emptyList())
            assertEquals("", result)
        }

        @Test
        fun `should convert single expansion ref to formatted string`() {
            val list = listOf(ExpansionRef(12345L, "Wingspan: European Expansion"))

            val result = converter.convertToDatabaseColumn(list)

            assertEquals("12345::Wingspan: European Expansion", result)
        }

        @Test
        fun `should convert multiple expansion refs separated by pipe`() {
            val list = listOf(
                ExpansionRef(101L, "Expansion A"),
                ExpansionRef(202L, "Expansion B"),
                ExpansionRef(303L, "Expansion C")
            )

            val result = converter.convertToDatabaseColumn(list)

            assertEquals("101::Expansion A|202::Expansion B|303::Expansion C", result)
        }

        @Test
        fun `should correctly format name containing double colons`() {
            val list = listOf(ExpansionRef(999L, "Game::Special Edition"))

            val result = converter.convertToDatabaseColumn(list)

            assertEquals("999::Game::Special Edition", result)
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
            val result = converter.convertToEntityAttribute("   ")
            assertTrue(result.isEmpty())
        }

        @Test
        fun `should parse valid single expansion ref string`() {
            val dbData = "12345::Wingspan: Oceania Expansion"

            val result = converter.convertToEntityAttribute(dbData)

            assertEquals(1, result.size)
            assertEquals(12345L, result[0].bggId)
            assertEquals("Wingspan: Oceania Expansion", result[0].name)
        }

        @Test
        fun `should parse multiple expansion refs correctly`() {
            val dbData = "101::Expansion A|202::Expansion B|303::Expansion C"

            val result = converter.convertToEntityAttribute(dbData)

            assertEquals(3, result.size)
            assertEquals(ExpansionRef(101L, "Expansion A"), result[0])
            assertEquals(ExpansionRef(202L, "Expansion B"), result[1])
            assertEquals(ExpansionRef(303L, "Expansion C"), result[2])
        }

        @Test
        fun `should handle limit=2 when expansion name contains double colons`() {
            val dbData = "555::Game::SubTitle::Edition"

            val result = converter.convertToEntityAttribute(dbData)

            assertEquals(1, result.size)
            assertEquals(555L, result[0].bggId)
            assertEquals("Game::SubTitle::Edition", result[0].name)
        }

        @Test
        fun `should ignore entries without separator`() {
            val dbData = "invalidEntryFormat"

            val result = converter.convertToEntityAttribute(dbData)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should ignore entries with non-numeric bggId`() {
            val dbData = "abc::Expansion Name"

            val result = converter.convertToEntityAttribute(dbData)

            assertTrue(result.isEmpty())
        }

        @Test
        fun `should filter out invalid entries and parse valid ones in mixed string`() {
            val dbData = "101::Valid A|invalidEntry|abc::BadId|202::Valid B|303::"

            val result = converter.convertToEntityAttribute(dbData)

            assertEquals(3, result.size)
            assertEquals(ExpansionRef(101L, "Valid A"), result[0])
            assertEquals(ExpansionRef(202L, "Valid B"), result[1])
            assertEquals(ExpansionRef(303L, ""), result[2]) // Nome vuoto ma valido
        }
    }
}