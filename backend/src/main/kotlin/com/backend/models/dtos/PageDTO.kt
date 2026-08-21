package com.backend.models.dtos

import io.swagger.v3.oas.annotations.media.ArraySchema
import io.swagger.v3.oas.annotations.media.Schema

@Schema(description = "Sort metadata used in paged responses")
data class SortInfo(
    @field:Schema(description = "Whether no sort is applied")
    val empty: Boolean,
    @field:Schema(description = "Whether the result is sorted")
    val sorted: Boolean,
    @field:Schema(description = "Whether the result is unsorted")
    val unsorted: Boolean,
)

@Schema(description = "Pageable metadata")
data class PageableInfo(
    @field:Schema(description = "Current page number, zero-based")
    val pageNumber: Int,
    @field:Schema(description = "Number of items per page")
    val pageSize: Int,
    @field:Schema(description = "Sort applied to this page")
    val sort: SortInfo,
    @field:Schema(description = "Offset of the first element in this page")
    val offset: Long,
    @field:Schema(description = "Whether the result is paged")
    val paged: Boolean,
    @field:Schema(description = "Whether the result is unpaged")
    val unpaged: Boolean,
)

@Schema(description = "Generic paged response wrapper")
data class PageDTO<T>(
    @field:ArraySchema(schema = Schema(description = "Page content"))
    val content: List<T>,

    @field:Schema(description = "Pagination request metadata")
    val pageable: PageableInfo,

    @field:Schema(description = "Total number of elements across all pages")
    val totalElements: Long,

    @field:Schema(description = "Total number of pages")
    val totalPages: Int,

    @field:Schema(description = "Whether this is the last page")
    val last: Boolean,

    @field:Schema(description = "Size of this page")
    val size: Int,

    @field:Schema(description = "Index of this page, zero-based")
    val number: Int,

    @field:Schema(description = "Sort applied to this page")
    val sort: SortInfo,

    @field:Schema(description = "Whether this is the first page")
    val first: Boolean,

    @field:Schema(description = "Number of elements in this page")
    val numberOfElements: Int,

    @field:Schema(description = "Whether this page has no elements")
    val empty: Boolean,
)