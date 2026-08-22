import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getGameByBggId, getHotGames, searchGame } from '@/api/game';
import { addToCollection, getGameStatusInCollection, removeFromCollection } from '@/api/collection';
import { addItemToWishlist, getItemStatusInWishlist, removeItemFromWishlist } from '@/api/wishlist';

import { useDefaultWishlistId } from '@/hooks/use-wishlist';

export const useGameDetail = (bggId: number) => {
	return useQuery({
		queryKey: ['games', 'detail', bggId],
		queryFn: () => getGameByBggId(bggId),
	});
}

export const useHotGames = () => {
	return useInfiniteQuery({
		queryKey: ['games', 'hot'],
		queryFn: ({ pageParam }) => getHotGames(pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
	});
}

export const useGameSearch = (query: string) => {
	return useInfiniteQuery({
		queryKey: ['games', 'search', query],
		queryFn: ({ pageParam }) => searchGame(query, pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: query.length > 0,
	});
}

export function useCollectionStatus(gameId: string | undefined) {
	return useQuery({
		queryKey: ['collection', 'status', gameId],
		queryFn: () => getGameStatusInCollection(gameId!),
		enabled: !!gameId,
	});
}

export const useDefaultWishlistStatus = (gameId: string | undefined) => {
	const defaultWishlistId = useDefaultWishlistId();

	return useQuery({
		queryKey: ['wishlists', 'item-status', defaultWishlistId, gameId],
		queryFn: () => getItemStatusInWishlist(defaultWishlistId!, gameId!),
		enabled: !!gameId && !!defaultWishlistId,
	});
}

export const useToggleCollection = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: async ({ gameId, itemId }: { gameId: string; itemId: string | null }) => {
			if (itemId) {
				await removeFromCollection(itemId);
			} else {
				await addToCollection(gameId);
			}
		},
		onSuccess: (_, { gameId }) => {
			queryClient.invalidateQueries({ queryKey: ['collection', 'status', gameId] });
			queryClient.invalidateQueries({ queryKey: ['collection'] });
		},
	});
}

export const useToggleDefaultWishlist = () => {
	const queryClient = useQueryClient();
	const defaultWishlistId = useDefaultWishlistId();

	return useMutation({
		mutationFn: async ({ gameId, itemId }: { gameId: string; itemId: string | null }) => {
			if (!defaultWishlistId) throw new Error('Default wishlist not found');
			if (itemId) {
				await removeItemFromWishlist(defaultWishlistId, itemId);
			} else {
				await addItemToWishlist(defaultWishlistId, gameId);
			}
		},
		onSuccess: (_, { gameId }) => {
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'item-status', defaultWishlistId, gameId] });
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
		},
	});
}