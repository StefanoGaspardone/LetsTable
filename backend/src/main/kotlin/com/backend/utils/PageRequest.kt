package com.backend.utils

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort

private const val DEFAULT_MAX_PAGE_SIZE = 100

fun safePageRequest(page: Int, size: Int, sort: Sort = Sort.unsorted(), maxSize: Int = DEFAULT_MAX_PAGE_SIZE): PageRequest =
    PageRequest.of(page.coerceAtLeast(0), size.coerceIn(1, maxSize), sort)