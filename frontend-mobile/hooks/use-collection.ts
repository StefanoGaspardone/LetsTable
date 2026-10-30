import { keepPreviousData, useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { addToCollection, listCollection } from '@/api/collection';

export const useCollection = (gameName: string, played?: boolean, isExpansion?: boolean, enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['collection', 'list', { gameName, played, isExpansion }],
		queryFn: ({ pageParam }) => listCollection({ page: pageParam, gameName, played, isExpansion }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		placeholderData: keepPreviousData,
		enabled,
	});
}

export const useAddToCollection = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (gameId: string) => addToCollection(gameId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['collection'] });
		},
	});
}