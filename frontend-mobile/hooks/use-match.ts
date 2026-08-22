import { useInfiniteQuery, useQuery } from '@tanstack/react-query';

import { getCalendarMatch, listMatches, ListMatchesParams } from '@/api/match';

export const useMatches = (filters: Omit<ListMatchesParams, 'page'>) => {
	return useInfiniteQuery({
		queryKey: ['matches', filters],
		queryFn: ({ pageParam }) => listMatches({ ...filters, page: pageParam }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
	});
}

export const useMatchCalendar = (year: number, month: number) => {
	return useQuery({
		queryKey: ['matches', 'calendar', year, month],
		queryFn: () => getCalendarMatch(year, month),
	});
}