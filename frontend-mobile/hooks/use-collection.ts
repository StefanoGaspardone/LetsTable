import { useInfiniteQuery, useMutation, useQueryClient } from '@tanstack/react-query';

import { addToCollection, listCollection } from '@/api/collection';

export const useCollection = (gameName: string) => {
	return useInfiniteQuery({
		queryKey: ['collection', gameName],
		queryFn: ({ pageParam }) => listCollection({ page: pageParam, gameName }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
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