import { useQuery } from '@tanstack/react-query';

import { listCollection } from '@/api/collection';
import { listMatches } from '@/api/match';

export const useHomeStats = () => {
	const matchesQuery = useQuery({
		queryKey: ['matches', 'home-summary'],
		queryFn: () => listMatches({ page: 0, size: 1, sort: 'playedAt-desc' }),
	});

	const collectionQuery = useQuery({
		queryKey: ['collection', 'home-summary'],
		queryFn: () => listCollection({ page: 0, size: 1 }),
	});

	return {
		totalMatches: matchesQuery.data?.totalElements ?? 0,
		totalGames: collectionQuery.data?.totalElements ?? 0,
		isLoading: matchesQuery.isLoading || collectionQuery.isLoading,
	};
}