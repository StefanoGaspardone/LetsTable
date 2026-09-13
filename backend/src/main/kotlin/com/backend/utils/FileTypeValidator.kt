package com.backend.utils

object FileTypeValidator {

    fun exactTypes(vararg types: String): (String?) -> Boolean =
        { contentType -> contentType != null && contentType in types }

    fun exactTypesOrPrefixes(exactTypes: Set<String>, prefixes: Set<String>): (String?) -> Boolean =
        { contentType ->
            contentType != null && (
                    contentType in exactTypes ||
                            prefixes.any { contentType.startsWith(it) }
                    )
        }
}