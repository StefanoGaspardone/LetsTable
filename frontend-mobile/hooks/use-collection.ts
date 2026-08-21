import { useInfiniteQuery } from '@tanstack/react-query';

import { listCollection } from '@/api/collection';

export const useCollection = (gameName: string) => {
	return useInfiniteQuery({
		queryKey: ['collection', gameName],
		queryFn: ({ pageParam }) => listCollection({ page: pageParam, gameName }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
	});
}