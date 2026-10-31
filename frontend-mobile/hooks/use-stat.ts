import { useEffect } from 'react';
import { useQuery } from '@tanstack/react-query';

import { getMyStats } from '@/api/stats';

import { syncWidgets } from '@/widgets/widget-registry';

export const MY_STATS_KEY = ['me', 'stats'];

export const useMyStats = () => {
	const { data, isLoading } = useQuery({
		queryKey: MY_STATS_KEY,
		queryFn: getMyStats,
	});

	useEffect(() => {
		if(data) syncWidgets(data).catch(error => console.log('Failed to sync widgets:', error));
	}, [data]);

	return {
		totalMatches: data?.totalMatches ?? 0,
		totalWins: data?.totalWins ?? 0,
		totalGames: data?.totalGames ?? 0,
		totalFriends: data?.totalFriends ?? 0,
		isLoading,
	}
}