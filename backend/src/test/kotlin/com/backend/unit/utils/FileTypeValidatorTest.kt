package com.backend.unit.utils

import com.backend.utils.FileTypeValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class FileTypeValidatorsTest {

    @Nested
    @DisplayName("exactTypes")
    inner class ExactTypesTests {

        @Test
        fun `should return true when content type matches one of the allowed types`() {
            val validator = FileTypeValidator.exactTypes("application/pdf", "image/png")

            assertThat(validator("application/pdf")).isTrue()
            assertThat(validator("image/png")).isTrue()
        }

        @Test
        fun `should return false when content type does not match any allowed type`() {
            val validator = FileTypeValidator.exactTypes("application/pdf", "image/png")

            assertThat(validator("application/zip")).isFalse()
        }

        @Test
        fun `should return false when content type is null`() {
            val validator = FileTypeValidator.exactTypes("application/pdf")

            assertThat(validator(null)).isFalse()
        }

        @Test
        fun `should return false when no types are configured`() {
            val validator = FileTypeValidator.exactTypes()

            assertThat(validator("application/pdf")).isFalse()
        }

        @Test
        fun `should be case-sensitive`() {
            val validator = FileTypeValidator.exactTypes("application/pdf")

            assertThat(validator("APPLICATION/PDF")).isFalse()
        }
    }

    @Nested
    @DisplayName("exactTypesOrPrefixes")
    inner class ExactTypesOrPrefixesTests {

        @Test
        fun `should return true when content type matches an exact type`() {
            val validator = FileTypeValidator.exactTypesOrPrefixes(
                exactTypes = setOf("application/pdf"),
                prefixes = setOf("image/"),
            )

            assertThat(validator("application/pdf")).isTrue()
        }

        @Test
        fun `should return true when content type matches a prefix`() {
            val validator = FileTypeValidator.exactTypesOrPrefixes(
                exactTypes = setOf("application/pdf"),
                prefixes = setOf("image/", "video/"),
            )

            assertThat(validator("image/png")).isTrue()
            assertThat(validator("video/mp4")).isTrue()
        }

        @Test
        fun `should return false when content type matches neither exact types nor prefixes`() {
            val validator = FileTypeValidator.exactTypesOrPrefixes(
                exactTypes = setOf("application/pdf"),
                prefixes = setOf("image/"),
            )

            assertThat(validator("application/zip")).isFalse()
        }

        @Test
        fun `should return false when content type is null`() {
            val validator = FileTypeValidator.exactTypesOrPrefixes(
                exactTypes = setOf("application/pdf"),
                prefixes = setOf("image/"),
            )

            assertThat(validator(null)).isFalse()
        }

        @Test
        fun `should return false when both exactTypes and prefixes are empty`() {
            val validator = FileTypeValidator.exactTypesOrPrefixes(
                exactTypes = emptySet(),
                prefixes = emptySet(),
            )

            assertThat(validator("application/pdf")).isFalse()
        }

        @Test
        fun `should work with only prefixes and no exact types`() {
            val validator = FileTypeValidator.exactTypesOrPrefixes(
                exactTypes = emptySet(),
                prefixes = setOf("image/"),
            )

            assertThat(validator("image/webp")).isTrue()
            assertThat(validator("application/pdf")).isFalse()
        }
    }
}