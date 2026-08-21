export interface SortInfo {
	empty: boolean;
	sorted: boolean;
	unsorted: boolean;
}

export interface PageableInfo {
	pageNumber: number;
	pageSize: number;
	sort: SortInfo;
	offset: number;
	paged: boolean;
	unpaged: boolean;
}

export interface PageDTO<T> {
	content: T[];
	pageable: PageableInfo;
	totalElements: number;
	totalPages: number;
	last: boolean;
	size: number;
	number: number;
	sort: SortInfo;
	first: boolean;
	numberOfElements: number;
	empty: boolean;
}