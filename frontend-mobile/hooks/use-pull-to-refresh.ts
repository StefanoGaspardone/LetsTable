import { useCallback, useState } from 'react';
import { useQueryClient, QueryKey } from '@tanstack/react-query';

export const usePullToRefresh = (queryKeys: QueryKey[]) => {
	const queryClient = useQueryClient();
	const [refreshing, setRefreshing] = useState(false);

	const onRefresh = useCallback(async () => {
		setRefreshing(true);

		try {
			await Promise.all(
				queryKeys.map(queryKey => queryClient.invalidateQueries({ queryKey }))
			);
		} finally {
			setRefreshing(false);
		}
	}, [queryClient, queryKeys]);

	return { refreshing, onRefresh };
}