package com.backend.unit.utils

import com.backend.utils.compareVersions
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class VersionsTest {

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource(
        "2.3.0, 2.3.0, 0",
        "2.3.1, 2.3.0, 1",
        "2.3.0, 2.4.0, -1",
        "3.0.0, 2.9.9, 1",
        "2.10.0, 2.9.0, 1",
        "2.9.0, 2.10.0, -1",
        "2.3, 2.3.0, 0",
        "2.3.0.1, 2.3.0, 1",
        "2.3.0, 2.3.0.0, 0",
        "2.x.0, 2.0.0, 0",
        "v2.4.0, 2.4.0, 0",
        "V2.4.0, v2.3.9, 1",
        "' 2.4.0 ', 2.4.0, 0",
    )
    fun `should compare versions numerically part by part`(left: String, right: String, expected: Int) {
        assertThat(compareVersions(left, right)).isEqualTo(expected)
    }

    @Test
    fun `should be antisymmetric`() {
        val pairs = listOf("2.3.0" to "2.4.0", "2.10.0" to "2.9.9", "1.0" to "1.0.1")

        pairs.forEach { (left, right) ->
            assertThat(compareVersions(left, right)).isEqualTo(-compareVersions(right, left))
        }
    }

    @Test
    fun `should only return -1, 0 or 1`() {
        assertThat(compareVersions("10.0.0", "1.0.0")).isEqualTo(1)
        assertThat(compareVersions("1.0.0", "10.0.0")).isEqualTo(-1)
    }

    @Test
    fun `should order a list of versions`() {
        val versions = listOf("2.10.0", "2.3.1", "v2.4.0", "2.3.0", "3.0.0")

        assertThat(versions.sortedWith { a, b -> compareVersions(a, b) }).containsExactly("2.3.0", "2.3.1", "v2.4.0", "2.10.0", "3.0.0")
    }

    @Test
    fun `should treat non numeric parts as zero`() {
        assertThat(compareVersions("2.3.0-beta", "2.3.0")).isEqualTo(0)
        assertThat(compareVersions("", "0.0.0")).isEqualTo(0)
    }
}