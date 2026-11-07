package com.backend.unit.services

import com.backend.exceptions.InvalidRankIndexFileException
import com.backend.models.entities.BggRankIndex
import com.backend.repositories.BggRankIndexRepository
import com.backend.services.BggRankIndexService
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.just
import io.mockk.Runs
import io.mockk.verify
import io.mockk.verifyOrder
import jakarta.persistence.EntityManager
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.mock.web.MockMultipartFile

@ExtendWith(MockKExtension::class)
class BggRankIndexServiceTest {

    @MockK(relaxUnitFun = true)
    private lateinit var bggRankIndexRepository: BggRankIndexRepository

    @MockK(relaxUnitFun = true)
    private lateinit var entityManager: EntityManager

    private lateinit var bggRankIndexService: BggRankIndexService

    private val persisted = mutableListOf<Any>()

    @BeforeEach
    fun setUp() {
        bggRankIndexService = BggRankIndexService(bggRankIndexRepository, entityManager)

        persisted.clear()
        every { entityManager.persist(capture(persisted)) } just Runs
    }

    private fun csvFile(content: String, filename: String = "ranks.csv") =
        MockMultipartFile("file", filename, "text/csv", content.toByteArray(Charsets.UTF_8))

    private fun persistedEntries(): List<BggRankIndex> = persisted.map { it as BggRankIndex }

    @Nested
    @DisplayName("uploadRankIndex - parsing")
    inner class ParsingTests {

        @Test
        fun `should parse the BGG dump and persist every ranked game`() {
            val csvContent = """
                id,name,yearpublished,rank,bayesaverage,average,usersrated,is_expansion,abstracts_rank,cgs_rank,childrensgames_rank,familygames_rank,partygames_rank,strategygames_rank,thematic_rank,wargames_rank
                224517,"Brass: Birmingham",2018,1,8.39028,8.55979,60175,0,,,,,,1,,
                342942,"Ark Nova",2021,2,8.35429,8.53813,63067,0,,,,,,2,,
            """.trimIndent()

            val result = bggRankIndexService.uploadRankIndex(csvFile(csvContent))

            assertThat(result.count).isEqualTo(2L)
            assertThat(persistedEntries().map { it.bggId to it.rank }).containsExactly(224517L to 1, 342942L to 2)
        }

        @Test
        fun `should handle names containing commas and quotes`() {
            val csvContent = "id,name,rank\n1,\"Tigris & Euphrates, \"\"Deluxe\"\"\",10\n2,Plain Name,11\n"

            bggRankIndexService.uploadRankIndex(csvFile(csvContent))

            assertThat(persistedEntries().map { it.bggId }).containsExactly(1L, 2L)
        }

        @Test
        fun `should accept a file starting with a UTF-8 byte order mark`() {
            val result = bggRankIndexService.uploadRankIndex(csvFile("﻿id,rank\n100,1\n"))

            assertThat(result.count).isEqualTo(1L)
        }

        @Test
        fun `should accept header names in any case and with spaces`() {
            val result = bggRankIndexService.uploadRankIndex(csvFile(" ID , Rank \n100 , 1\n"))

            assertThat(result.count).isEqualTo(1L)
            assertThat(persistedEntries().single().bggId).isEqualTo(100L)
        }

        @Test
        fun `should accept Windows line endings`() {
            val result = bggRankIndexService.uploadRankIndex(csvFile("id,rank\r\n100,1\r\n200,2\r\n"))

            assertThat(result.count).isEqualTo(2L)
        }

        @Test
        fun `should skip rows with a non-positive rank`() {
            bggRankIndexService.uploadRankIndex(csvFile("id,rank\n100,1\n200,0\n300,-5\n400,\n"))

            assertThat(persistedEntries().map { it.bggId }).containsExactly(100L)
        }

        @Test
        fun `should skip rows with a non-numeric or invalid id or rank`() {
            bggRankIndexService.uploadRankIndex(csvFile("id,rank\nabc,1\n200,xyz\n0,4\n300,3\n"))

            assertThat(persistedEntries().map { it.bggId }).containsExactly(300L)
        }

        @Test
        fun `should skip short rows instead of rejecting the whole file`() {
            bggRankIndexService.uploadRankIndex(csvFile("id,name,rank\n100,Short\n200,Full,2\n"))

            assertThat(persistedEntries().map { it.bggId }).containsExactly(200L)
        }

        @Test
        fun `should keep only the first entry of a duplicated id`() {
            val result = bggRankIndexService.uploadRankIndex(csvFile("id,rank\n100,1\n100,7\n200,2\n"))

            assertThat(result.count).isEqualTo(2L)
            assertThat(persistedEntries().map { it.bggId to it.rank }).containsExactly(100L to 1, 200L to 2)
        }
    }

    @Nested
    @DisplayName("uploadRankIndex - persistence")
    inner class PersistenceTests {

        @Test
        fun `should replace the whole index`() {
            bggRankIndexService.uploadRankIndex(csvFile("id,rank\n100,1\n"))

            verifyOrder {
                bggRankIndexRepository.deleteAllInBatch()
                entityManager.persist(any())
                entityManager.flush()
                entityManager.clear()
            }
        }

        @Test
        fun `should flush and clear every 500 entries`() {
            val rows = (1..1200).joinToString("\n") { "$it,$it" }

            val result = bggRankIndexService.uploadRankIndex(csvFile("id,rank\n$rows\n"))

            assertThat(result.count).isEqualTo(1200L)
            assertThat(persisted).hasSize(1200)
            verify(exactly = 3) { entityManager.flush() }
            verify(exactly = 3) { entityManager.clear() }
        }

        @Test
        fun `should propagate database errors instead of blaming the file`() {
            every { bggRankIndexRepository.deleteAllInBatch() } throws RuntimeException("DB unavailable")

            assertThatThrownBy { bggRankIndexService.uploadRankIndex(csvFile("id,rank\n100,1\n")) }
                .isInstanceOf(RuntimeException::class.java)
                .isNotInstanceOf(InvalidRankIndexFileException::class.java)
                .hasMessage("DB unavailable")
        }
    }

    @Nested
    @DisplayName("uploadRankIndex - invalid files")
    inner class InvalidFileTests {

        @ParameterizedTest
        @ValueSource(strings = ["ranks.xlsx", "ranks.txt", "ranks", "ranks.csv.exe"])
        fun `should reject files that are not CSV`(filename: String) {
            assertThatThrownBy { bggRankIndexService.uploadRankIndex(csvFile("id,rank\n100,1\n", filename)) }
                .isInstanceOf(InvalidRankIndexFileException::class.java)
                .hasMessageContaining("Only CSV")

            verify(exactly = 0) { bggRankIndexRepository.deleteAllInBatch() }
        }

        @Test
        fun `should accept an upper case extension`() {
            assertThat(bggRankIndexService.uploadRankIndex(csvFile("id,rank\n100,1\n", "RANKS.CSV")).count).isEqualTo(1L)
        }

        @Test
        fun `should reject a file without valid entries and keep the current index`() {
            assertThatThrownBy { bggRankIndexService.uploadRankIndex(csvFile("id,rank\nabc,def\n")) }
                .isInstanceOf(InvalidRankIndexFileException::class.java)

            verify(exactly = 0) { bggRankIndexRepository.deleteAllInBatch() }
            assertThat(persisted).isEmpty()
        }

        @Test
        fun `should reject a file with only the header`() {
            assertThatThrownBy { bggRankIndexService.uploadRankIndex(csvFile("id,rank\n")) }
                .isInstanceOf(InvalidRankIndexFileException::class.java)
        }

        @Test
        fun `should reject an empty file`() {
            assertThatThrownBy { bggRankIndexService.uploadRankIndex(csvFile("")) }
                .isInstanceOf(InvalidRankIndexFileException::class.java)
        }

        @ParameterizedTest
        @ValueSource(strings = ["name,year\nCatan,1995\n", "id,name\n1,Catan\n", "rank,name\n1,Catan\n"])
        fun `should reject a file missing the id or rank column`(content: String) {
            assertThatThrownBy { bggRankIndexService.uploadRankIndex(csvFile(content)) }
                .isInstanceOf(InvalidRankIndexFileException::class.java)
                .hasMessageContaining("Missing required column")

            verify(exactly = 0) { bggRankIndexRepository.deleteAllInBatch() }
        }

        @Test
        fun `should reject a file with a broken quoted field`() {
            assertThatThrownBy { bggRankIndexService.uploadRankIndex(csvFile("id,name,rank\n1,\"Unclosed,1\n")) }
                .isInstanceOf(InvalidRankIndexFileException::class.java)

            verify(exactly = 0) { bggRankIndexRepository.deleteAllInBatch() }
        }
    }

    @Nested
    @DisplayName("getRankIndexCount")
    inner class GetRankIndexCountTests {

        @Test
        fun `should return the count from the repository`() {
            every { bggRankIndexRepository.count() } returns 181081L

            assertThat(bggRankIndexService.getRankIndexCount()).isEqualTo(181081L)
        }

        @Test
        fun `should return zero when the index is empty`() {
            every { bggRankIndexRepository.count() } returns 0L

            assertThat(bggRankIndexService.getRankIndexCount()).isEqualTo(0L)
        }
    }
}