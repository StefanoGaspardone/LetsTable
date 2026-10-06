package com.backend.utils

import com.backend.exceptions.InvalidSortException
import org.springframework.data.domain.Sort

fun resolveSort(
    sort: String?,
    allowedFields: Set<String>,
    defaultField: String,
    nullsLastFields: Set<String> = emptySet(),
    tieBreaker: Sort = Sort.unsorted(),
): Sort {
    val parts = sort?.split("-") ?: listOf(defaultField, "desc")
    val field = parts.getOrNull(0) ?: defaultField
    val direction = parts.getOrNull(1)?.uppercase() ?: "DESC"

    if(field !in allowedFields) throw InvalidSortException(field)

    val baseOrder = if(direction == "ASC") Sort.Order.asc(field) else Sort.Order.desc(field)
    val order = if(field in nullsLastFields) baseOrder.nullsLast() else baseOrder

    val extraOrders = tieBreaker.toList().filter { it.property != field }

    return Sort.by(order).and(Sort.by(extraOrders))
}