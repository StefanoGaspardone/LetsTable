package com.backend.unit.services

import com.backend.exceptions.InvalidRankIndexFileException
import com.backend.models.entities.BggRankIndex
import com.backend.repositories.BggRankIndexRepository
import com.backend.services.BggRankIndexService
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit5.MockKExtension
import io.mockk.slot
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.mock.web.MockMultipartFile

@ExtendWith(MockKExtension::class)
class BggRankIndexServiceTest {

    @MockK
    private lateinit var bggRankIndexRepository: BggRankIndexRepository

    @InjectMockKs
    private lateinit var bggRankIndexService: BggRankIndexService

    private fun csvFile(content: String, filename: String = "ranks.csv") =
        MockMultipartFile("file", filename, "text/csv", content.toByteArray())

    @Nested
    @DisplayName("uploadRankIndex")
    inner class UploadRankIndexTests {

        @Test
        fun `should parse valid CSV and save entries, returning their count`() {
            val csvContent = """
                id,name,yearpublished,rank,bayesaverage,average,usersrated,is_expansion,abstracts_rank,cgs_rank,childrensgames_rank,familygames_rank,partygames_rank,strategygames_rank,thematic_rank,wargames_rank
                224517,"Brass: Birmingham",2018,1,8.39028,8.55979,60175,0,,,,,,1,,
                342942,"Ark Nova",2021,2,8.35429,8.53813,63067,0,,,,,,2,,
            """.trimIndent()

            every { bggRankIndexRepository.deleteAllInBatch() } returns Unit
            val entriesSlot = slot<List<BggRankIndex>>()
            every { bggRankIndexRepository.saveAll(capture(entriesSlot)) } answers { entriesSlot.captured }

            val result = bggRankIndexService.uploadRankIndex(csvFile(csvContent))

            assertThat(result.count).isEqualTo(2L)
            assertThat(entriesSlot.captured).hasSize(2)
            assertThat(entriesSlot.captured.map { it.bggId }).containsExactly(224517L, 342942L)
            assertThat(entriesSlot.captured.map { it.rank }).containsExactly(1, 2)

            verify(exactly = 1) { bggRankIndexRepository.deleteAllInBatch() }
        }

        @Test
        fun `should skip rows with a non-positive rank`() {
            val csvContent = """
                id,rank
                100,1
                200,0
                300,-5
            """.trimIndent()

            every { bggRankIndexRepository.deleteAllInBatch() } returns Unit
            val entriesSlot = slot<List<BggRankIndex>>()
            every { bggRankIndexRepository.saveAll(capture(entriesSlot)) } answers { entriesSlot.captured }

            val result = bggRankIndexService.uploadRankIndex(csvFile(csvContent))

            assertThat(result.count).isEqualTo(1L)
            assertThat(entriesSlot.captured.single().bggId).isEqualTo(100L)
        }

        @Test
        fun `should skip rows with a non-numeric id or rank`() {
            val csvContent = """
                id,rank
                abc,1
                200,xyz
                300,3
            """.trimIndent()

            every { bggRankIndexRepository.deleteAllInBatch() } returns Unit
            val entriesSlot = slot<List<BggRankIndex>>()
            every { bggRankIndexRepository.saveAll(capture(entriesSlot)) } answers { entriesSlot.captured }

            val result = bggRankIndexService.uploadRankIndex(csvFile(csvContent))

            assertThat(result.count).isEqualTo(1L)
            assertThat(entriesSlot.captured.single().bggId).isEqualTo(300L)
        }

        @Test
        fun `should throw InvalidRankIndexFileException when no valid entries are found`() {
            val csvContent = """
                id,rank
                abc,def
            """.trimIndent()

            assertThatThrownBy {
                bggRankIndexService.uploadRankIndex(csvFile(csvContent))
            }.isInstanceOf(InvalidRankIndexFileException::class.java)

            verify(exactly = 0) { bggRankIndexRepository.deleteAllInBatch() }
            verify(exactly = 0) { bggRankIndexRepository.saveAll(any<List<BggRankIndex>>()) }
        }

        @Test
        fun `should throw InvalidRankIndexFileException when the file has no header columns id or rank`() {
            val csvContent = """
                name,year
                Catan,1995
            """.trimIndent()

            assertThatThrownBy {
                bggRankIndexService.uploadRankIndex(csvFile(csvContent))
            }.isInstanceOf(InvalidRankIndexFileException::class.java)
        }

        @Test
        fun `should wrap unexpected exception from the repository as InvalidRankIndexFileException`() {
            val csvContent = """
                id,rank
                100,1
            """.trimIndent()

            every { bggRankIndexRepository.deleteAllInBatch() } throws RuntimeException("DB unavailable")

            assertThatThrownBy {
                bggRankIndexService.uploadRankIndex(csvFile(csvContent))
            }.isInstanceOf(InvalidRankIndexFileException::class.java)
                .hasMessageContaining("DB unavailable")
        }
    }

    @Nested
    @DisplayName("getRankIndexCount")
    inner class GetRankIndexCountTests {

        @Test
        fun `should return the count from the repository`() {
            every { bggRankIndexRepository.count() } returns 181081L

            val result = bggRankIndexService.getRankIndexCount()

            assertThat(result).isEqualTo(181081L)
        }

        @Test
        fun `should return zero when the index is empty`() {
            every { bggRankIndexRepository.count() } returns 0L

            val result = bggRankIndexService.getRankIndexCount()

            assertThat(result).isEqualTo(0L)
        }
    }
}