import { useCallback, useState } from 'react';

import type { Page } from '@/types/page';

interface UseInfiniteListOptions<T, P> {
	fetchPage: (params: P & { page: number; size: number }) => Promise<Page<T>>;
	params: P;
	size?: number;
}

export const useInfiniteList = <T, P extends object>({ fetchPage, params, size = 20 }: UseInfiniteListOptions<T, P>) => {
	const [content, setContent] = useState<T[]>([]);
	const [pageNumber, setPageNumber] = useState(0);
	const [isLast, setIsLast] = useState(false);
	const [isLoading, setIsLoading] = useState(true);
	const [isLoadingMore, setIsLoadingMore] = useState(false);

	const load = useCallback(async (reset: boolean) => {
		const targetPage = reset ? 0 : pageNumber;

		if(reset) setIsLoading(true);
		else setIsLoadingMore(true);

		try {
			const result = await fetchPage({ ...params, page: targetPage, size });

			setContent(prev => (reset ? result.content : [...prev, ...result.content]));
			setPageNumber(targetPage + 1);
			setIsLast(result.last);
		} finally {
			setIsLoading(false);
			setIsLoadingMore(false);
		}
	}, [fetchPage, params, size, pageNumber]);

	const loadMore = useCallback(() => {
		if(!isLast && !isLoadingMore) load(false);
	}, [isLast, isLoadingMore, load]);

	const reload = useCallback(() => {
		load(true);
	}, [load]);

	const updateItem = useCallback((predicate: (item: T) => boolean, updater: (item: T) => T) => {
		setContent(prev => prev.map(item => (predicate(item) ? updater(item) : item)));
	}, []);

	return { content, isLoading, isLoadingMore, isLast, loadMore, reload, updateItem };
}