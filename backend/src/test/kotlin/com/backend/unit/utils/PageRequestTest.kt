package com.backend.unit.utils

import com.backend.utils.safePageRequest
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.springframework.data.domain.Sort

class PaginationTest {

    @Nested
    @DisplayName("page")
    inner class PageTests {

        @ParameterizedTest
        @CsvSource("0, 0", "3, 3", "-1, 0", "-100, 0")
        fun `should never go below the first page`(page: Int, expected: Int) {
            assertThat(safePageRequest(page, 20).pageNumber).isEqualTo(expected)
        }

        @Test
        fun `should compute the offset without overflowing on huge pages`() {
            val request = safePageRequest(Int.MAX_VALUE, 100)

            assertThat(request.offset).isEqualTo(Int.MAX_VALUE.toLong() * 100)
        }
    }

    @Nested
    @DisplayName("size")
    inner class SizeTests {

        @ParameterizedTest
        @CsvSource("20, 20", "1, 1", "100, 100", "0, 1", "-5, 1", "101, 100", "10000, 100")
        fun `should keep the size between 1 and the default maximum`(size: Int, expected: Int) {
            assertThat(safePageRequest(0, size).pageSize).isEqualTo(expected)
        }

        @ParameterizedTest
        @CsvSource("10, 10", "50, 50", "51, 50", "0, 1")
        fun `should respect a custom maximum`(size: Int, expected: Int) {
            assertThat(safePageRequest(0, size, maxSize = 50).pageSize).isEqualTo(expected)
        }

        @Test
        fun `should allow a maximum of one`() {
            assertThat(safePageRequest(0, 20, maxSize = 1).pageSize).isEqualTo(1)
        }
    }

    @Nested
    @DisplayName("sort")
    inner class SortTests {

        @Test
        fun `should be unsorted by default`() {
            assertThat(safePageRequest(0, 20).sort.isUnsorted).isTrue()
        }

        @Test
        fun `should keep the given sort`() {
            val sort = Sort.by(Sort.Order.desc("playedAt"), Sort.Order.asc("id"))

            assertThat(safePageRequest(2, 10, sort).sort).isEqualTo(sort)
        }

        @Test
        fun `should keep the sort while clamping page and size`() {
            val sort = Sort.by("name")

            val request = safePageRequest(-1, 500, sort, maxSize = 50)

            assertThat(request.pageNumber).isEqualTo(0)
            assertThat(request.pageSize).isEqualTo(50)
            assertThat(request.sort).isEqualTo(sort)
        }
    }
}