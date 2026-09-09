package com.backend.utils

import com.backend.models.entities.ExpansionRef
import jakarta.persistence.AttributeConverter
import jakarta.persistence.Converter

@Converter
class ExpansionRefListConverter: AttributeConverter<List<ExpansionRef>, String> {

    override fun convertToDatabaseColumn(attribute: List<ExpansionRef>?): String? =
        attribute?.joinToString("|") { "${it.bggId}::${it.name}" }

    override fun convertToEntityAttribute(dbData: String?): List<ExpansionRef> {
        if(dbData.isNullOrBlank()) return emptyList()

        return dbData.split("|").mapNotNull { entry ->
            val parts = entry.split("::", limit = 2)

            if(parts.size == 2) {
                val id = parts[0].toLongOrNull() ?: return@mapNotNull null
                ExpansionRef(id, parts[1])
            } else null
        }
    }
}