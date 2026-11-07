package com.backend.unit.utils

import com.backend.utils.FileTypeValidator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class FileTypeValidatorTest {

    @Nested
    @DisplayName("exactTypes")
    inner class ExactTypesTests {

        private val validator = FileTypeValidator.exactTypes("application/pdf", "image/png")

        @Test
        fun `should accept the configured types`() {
            assertThat(validator("application/pdf")).isTrue()
            assertThat(validator("image/png")).isTrue()
        }

        @Test
        fun `should reject types that are not configured`() {
            assertThat(validator("application/zip")).isFalse()
            assertThat(validator("image/jpeg")).isFalse()
        }

        @Test
        fun `should reject types that only share a prefix with a configured type`() {
            assertThat(validator("application/pdfx")).isFalse()
            assertThat(validator("image/png-evil")).isFalse()
        }

        @Test
        fun `should ignore the case of the content type`() {
            assertThat(validator("APPLICATION/PDF")).isTrue()
            assertThat(validator("Image/PNG")).isTrue()
        }

        @Test
        fun `should ignore the case of the configured types`() {
            val upperCaseValidator = FileTypeValidator.exactTypes("Application/PDF")

            assertThat(upperCaseValidator("application/pdf")).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["application/pdf; charset=binary", "application/pdf;name=rules.pdf", "  application/pdf  ", "application/pdf ; q=1"])
        fun `should ignore parameters and surrounding spaces`(contentType: String) {
            assertThat(validator(contentType)).isTrue()
        }

        @Test
        fun `should reject a missing content type`() {
            assertThat(validator(null)).isFalse()
        }

        @ParameterizedTest
        @ValueSource(strings = ["", "   ", ";charset=utf-8"])
        fun `should reject a blank content type`(contentType: String) {
            assertThat(validator(contentType)).isFalse()
        }

        @Test
        fun `should reject everything when no types are configured`() {
            assertThat(FileTypeValidator.exactTypes()("application/pdf")).isFalse()
        }

        @Test
        fun `should reject SVG even when it is explicitly configured`() {
            val svgValidator = FileTypeValidator.exactTypes("image/svg+xml", "image/png")

            assertThat(svgValidator("image/svg+xml")).isFalse()
            assertThat(svgValidator("IMAGE/SVG+XML; charset=utf-8")).isFalse()
            assertThat(svgValidator("image/png")).isTrue()
        }
    }

    @Nested
    @DisplayName("exactTypesOrPrefixes")
    inner class ExactTypesOrPrefixesTests {

        private val validator = FileTypeValidator.exactTypesOrPrefixes(
            exactTypes = setOf("application/pdf"),
            prefixes = setOf("image/", "video/"),
        )

        @Test
        fun `should accept an exact type`() {
            assertThat(validator("application/pdf")).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["image/png", "image/jpeg", "image/webp", "video/mp4", "video/quicktime"])
        fun `should accept any type matching a prefix`(contentType: String) {
            assertThat(validator(contentType)).isTrue()
        }

        @ParameterizedTest
        @ValueSource(strings = ["application/zip", "text/html", "application/x-msdownload", "audio/mpeg"])
        fun `should reject types matching neither exact types nor prefixes`(contentType: String) {
            assertThat(validator(contentType)).isFalse()
        }

        @ParameterizedTest
        @ValueSource(strings = ["image/svg+xml", "Image/SVG+XML", "image/svg+xml; charset=utf-8"])
        fun `should reject SVG even though it matches the image prefix`(contentType: String) {
            assertThat(validator(contentType)).isFalse()
        }

        @Test
        fun `should ignore case and parameters when matching prefixes`() {
            assertThat(validator("IMAGE/PNG; name=photo.png")).isTrue()
        }

        @Test
        fun `should ignore the case of the configured prefixes`() {
            val upperCaseValidator = FileTypeValidator.exactTypesOrPrefixes(emptySet(), setOf("IMAGE/"))

            assertThat(upperCaseValidator("image/png")).isTrue()
        }

        @Test
        fun `should reject a missing content type`() {
            assertThat(validator(null)).isFalse()
        }

        @Test
        fun `should reject everything when both exactTypes and prefixes are empty`() {
            assertThat(FileTypeValidator.exactTypesOrPrefixes(emptySet(), emptySet())("application/pdf")).isFalse()
        }

        @Test
        fun `should work with only prefixes and no exact types`() {
            val prefixOnly = FileTypeValidator.exactTypesOrPrefixes(emptySet(), setOf("image/"))

            assertThat(prefixOnly("image/webp")).isTrue()
            assertThat(prefixOnly("application/pdf")).isFalse()
        }

        @Test
        fun `should work with only exact types and no prefixes`() {
            val exactOnly = FileTypeValidator.exactTypesOrPrefixes(setOf("application/pdf"), emptySet())

            assertThat(exactOnly("application/pdf")).isTrue()
            assertThat(exactOnly("image/png")).isFalse()
        }

        @Test
        fun `should ignore blank prefixes instead of accepting everything`() {
            val blankPrefix = FileTypeValidator.exactTypesOrPrefixes(emptySet(), setOf(""))

            assertThat(blankPrefix("application/x-msdownload")).isFalse()
        }
    }
}