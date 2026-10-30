import { keepPreviousData, QueryClient, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { createMatch, deleteMatch, getCalendarMatch, getMatchById, getRecentGames, listGameStats, listMatches, ListMatchesParams, updateMatch } from '@/api/match';

import { CreateMatchPayload, UpdateMatchPayload } from '@/types/match';

const invalidateMatchData = (queryClient: QueryClient, excludedMatchId?: string) => {
	queryClient.invalidateQueries({
		queryKey: ['matches'],
		predicate: query => !excludedMatchId || !query.queryKey.includes(excludedMatchId),
	});
	queryClient.invalidateQueries({ queryKey: ['users', 'matches'] });
	queryClient.invalidateQueries({ queryKey: ['collection', 'list'] });
}

export const useMatches = (filters: Omit<ListMatchesParams, 'page'>, enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['matches', filters],
		queryFn: ({ pageParam }) => listMatches({ ...filters, page: pageParam }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		placeholderData: keepPreviousData,
		enabled,
	});
}

export const useMatchDetail = (matchId: string) => {
	return useQuery({
		queryKey: ['matches', 'detail', matchId],
		queryFn: () => getMatchById(matchId),
		enabled: !!matchId,
	});
}

export const useMatchCalendar = (year: number, month: number, enabled = true) => {
	return useQuery({
		queryKey: ['matches', 'calendar', year, month],
		queryFn: () => getCalendarMatch(year, month),
		placeholderData: keepPreviousData,
		enabled,
	});
}

export const useMatchesByDay = (date: string | null, enabled = true) => {
	return useQuery({
		queryKey: ['matches', 'day', date],
		queryFn: () => listMatches({ page: 0, size: 50, fromDate: date!, toDate: date!, sort: 'playedAt-desc' }),
		enabled: enabled && !!date,
	});
}

export const useGameStats = (sort: string, enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['matches', 'game-stats', sort],
		queryFn: ({ pageParam }) => listGameStats({ page: pageParam, size: 20, sort }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		placeholderData: keepPreviousData,
		enabled,
	});
}

export const useRecentGames = (enabled = true) => {
	return useQuery({
		queryKey: ['matches', 'recent-games'],
		queryFn: () => getRecentGames(),
		enabled,
	});
}

export const useCreateMatch = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (payload: CreateMatchPayload) => createMatch(payload),
		onSuccess: () => invalidateMatchData(queryClient),
	});
}

export const useUpdateMatch = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: ({ matchId, payload }: { matchId: string; payload: UpdateMatchPayload }) => updateMatch(matchId, payload),
		onSuccess: (updatedMatch) => {
			queryClient.setQueryData(['matches', 'detail', updatedMatch.id], updatedMatch);
			invalidateMatchData(queryClient);
		},
	});
}

export const useDeleteMatch = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (matchId: string) => deleteMatch(matchId),
		onSuccess: (_, matchId) => invalidateMatchData(queryClient, matchId),
	});
}