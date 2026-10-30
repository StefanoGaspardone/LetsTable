import { keepPreviousData, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { addToCollection, getGameStatusInCollection, removeFromCollection } from '@/api/collection';
import { addItemToWishlist, getItemStatusInWishlist, removeItemFromWishlist } from '@/api/wishlist';
import { getGameByBggId, getGameExpansions, getHotGames, getOverallGames, searchGame } from '@/api/game';
import { listGameRules } from '@/api/game-rules';

import { defaultWishlistQuery, useDefaultWishlistId } from '@/hooks/use-wishlist';

const GAME_STALE_TIME_MS = 5 * 60 * 1000;
const RANKING_STALE_TIME_MS = 30 * 60 * 1000;

interface ToggleVariables {
	gameId: string;
	itemId: string | null;
}

export const useGameDetail = (bggId: number) => {
	return useQuery({
		queryKey: ['games', 'detail', bggId],
		queryFn: () => getGameByBggId(bggId),
		enabled: Number.isFinite(bggId) && bggId > 0,
		staleTime: GAME_STALE_TIME_MS,
	});
}

export const useHotGames = (enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['games', 'hot'],
		queryFn: ({ pageParam }) => getHotGames(pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		staleTime: RANKING_STALE_TIME_MS,
		enabled,
	});
}

export const useOverallGames = (enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['games', 'overall'],
		queryFn: ({ pageParam }) => getOverallGames(pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		staleTime: RANKING_STALE_TIME_MS,
		enabled,
	});
}

export const useGameSearch = (query: string) => {
	return useInfiniteQuery({
		queryKey: ['games', 'search', query],
		queryFn: ({ pageParam }) => searchGame(query, pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: query.length > 0,
		staleTime: GAME_STALE_TIME_MS,
		placeholderData: keepPreviousData,
	});
}

export const useCollectionStatus = (gameId: string | undefined) => {
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
		mutationFn: async ({ gameId, itemId }: ToggleVariables) => {
			if(itemId) await removeFromCollection(itemId);
			else await addToCollection(gameId);
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['collection'] });
		},
	});
}

export const useToggleDefaultWishlist = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: async ({ gameId, itemId }: ToggleVariables) => {
			const { id: wishlistId } = await queryClient.fetchQuery(defaultWishlistQuery);

			if(itemId) await removeItemFromWishlist(wishlistId, itemId);
			else await addItemToWishlist(wishlistId, gameId);
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
			queryClient.invalidateQueries({ queryKey: ['users', 'wishlist'] });
		},
	});
}

export const useGameExpansions = (bggId: number | null, enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['games', 'expansions', bggId],
		queryFn: ({ pageParam }) => getGameExpansions(bggId!, pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: enabled && bggId != null && Number.isFinite(bggId),
		staleTime: GAME_STALE_TIME_MS,
	});
}

export const useGameRules = (gameId: string | undefined, enabled = true) => {
	return useQuery({
		queryKey: ['games', 'rules', gameId],
		queryFn: () => listGameRules(gameId!),
		enabled: enabled && !!gameId,
	});
}