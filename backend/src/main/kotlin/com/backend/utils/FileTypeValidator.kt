package com.backend.utils

object FileTypeValidator {

    private val ALWAYS_REJECTED_TYPES = setOf("image/svg+xml")

    fun exactTypes(vararg types: String): (String?) -> Boolean =
        exactTypesOrPrefixes(types.toSet(), emptySet())

    fun exactTypesOrPrefixes(exactTypes: Set<String>, prefixes: Set<String>): (String?) -> Boolean {
        val allowedTypes = exactTypes.mapNotNull { normalize(it) }.toSet()
        val allowedPrefixes = prefixes.mapNotNull { normalize(it) }.toSet()

        return { contentType ->
            normalize(contentType)?.let { type ->
                type !in ALWAYS_REJECTED_TYPES && (type in allowedTypes || allowedPrefixes.any { type.startsWith(it) })
            } ?: false
        }
    }

    private fun normalize(contentType: String?): String? =
        contentType?.substringBefore(';')?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
}