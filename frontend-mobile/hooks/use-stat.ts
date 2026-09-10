import { useQuery } from '@tanstack/react-query';

import { listCollection } from '@/api/collection';
import { getWinStats } from '@/api/match';

export const useHomeStats = () => {
	const winStatsQuery = useQuery({
		queryKey: ['matches', 'win-stats'],
		queryFn: () => getWinStats(),
	});

	const collectionQuery = useQuery({
		queryKey: ['collection', 'home-summary'],
		queryFn: () => listCollection({ page: 0, size: 1 }),
	});

	return {
		totalMatches: winStatsQuery.data?.totalMatches ?? 0,
		totalWins: winStatsQuery.data?.totalWins ?? 0,
		totalGames: collectionQuery.data?.totalElements ?? 0,
		isLoading: winStatsQuery.isLoading || collectionQuery.isLoading,
	}
}