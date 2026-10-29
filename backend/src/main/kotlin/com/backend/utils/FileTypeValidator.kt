package com.backend.utils

object FileTypeValidator {

    private val ALWAYS_REJECTED_TYPES = setOf("image/svg+xml")

    fun exactTypes(vararg types: String): (String?) -> Boolean =
        { contentType -> normalize(contentType)?.let { it !in ALWAYS_REJECTED_TYPES && it in types } ?: false }

    fun exactTypesOrPrefixes(exactTypes: Set<String>, prefixes: Set<String>): (String?) -> Boolean =
        { contentType ->
            normalize(contentType)?.let { type ->
                type !in ALWAYS_REJECTED_TYPES && (type in exactTypes || prefixes.any { type.startsWith(it) })
            } ?: false
        }

    private fun normalize(contentType: String?): String? =
        contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}