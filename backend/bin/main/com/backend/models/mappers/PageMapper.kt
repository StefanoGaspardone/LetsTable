package com.backend.models.mappers

import com.backend.models.dtos.PageDTO
import com.backend.models.dtos.PageableInfo
import com.backend.models.dtos.SortInfo
import org.springframework.data.domain.Page

private fun Page<*>.toSortInfo(): SortInfo = SortInfo(
    empty = sort.isEmpty,
    sorted = sort.isSorted,
    unsorted = sort.isUnsorted,
)

private fun Page<*>.toPageableInfo(): PageableInfo = PageableInfo(
    pageNumber = pageable.pageNumber,
    pageSize = pageable.pageSize,
    sort = toSortInfo(),
    offset = pageable.offset,
    paged = pageable.isPaged,
    unpaged = pageable.isUnpaged,
)

fun <T: Any, R> Page<T>.toPageDTO(mapper: (T) -> R): PageDTO<R> = PageDTO(
    content = content.map(mapper),
    pageable = toPageableInfo(),
    totalElements = totalElements,
    totalPages = totalPages,
    last = isLast,
    size = size,
    number = number,
    sort = toSortInfo(),
    first = isFirst,
    numberOfElements = numberOfElements,
    empty = isEmpty,
)