package com.backend.utils

import com.backend.exceptions.InvalidSortException
import org.springframework.data.domain.Sort

fun resolveSort(sort: String?, allowedFields: Set<String>, defaultField: String): Sort {
    val parts = sort?.split("-") ?: listOf(defaultField, "desc")
    val field = parts.getOrNull(0) ?: defaultField
    val direction = parts.getOrNull(1)?.uppercase() ?: "DESC"

    if(field !in allowedFields) throw InvalidSortException(field)

    return when (direction) {
        "ASC" -> Sort.by(field).ascending()
        else -> Sort.by(field).descending()
    }
}