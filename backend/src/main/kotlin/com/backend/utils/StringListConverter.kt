package com.backend.utils

import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class StringListConverter: AttributeConverter<List<String>, String> {

    companion object {
        private const val SEPARATOR = '|'
        private const val ESCAPE = '\\'
    }

    override fun convertToDatabaseColumn(attribute: List<String>?): String? =
        attribute?.joinToString(SEPARATOR.toString()) { escape(it) }

    override fun convertToEntityAttribute(dbData: String?): List<String> {
        if(dbData.isNullOrBlank()) return emptyList()

        val items = mutableListOf<String>()
        val current = StringBuilder()
        var escaping = false

        for(char in dbData) {
            when {
                escaping -> {
                    current.append(char)
                    escaping = false
                }
                char == ESCAPE -> escaping = true
                char == SEPARATOR -> {
                    items.add(current.toString())
                    current.clear()
                }
                else -> current.append(char)
            }
        }

        if(escaping) current.append(ESCAPE)
        items.add(current.toString())

        return items
    }

    private fun escape(value: String): String =
        value.replace(ESCAPE.toString(), "$ESCAPE$ESCAPE").replace(SEPARATOR.toString(), "$ESCAPE$SEPARATOR")
}