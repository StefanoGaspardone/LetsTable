import { keepPreviousData, QueryClient, useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { addItemToWishlist, addMemberToWishlist, deleteWishlist, getMyDefaultWishlist, getWishlistById, leaveWishlist, listMyWishlists, listWishlistItems, listWishlistMembers, ListWishlistsParams, removeItemFromWishlist, removeMemberFromWishlist, updateWishlist } from '@/api/wishlist';

export const defaultWishlistQuery = {
	queryKey: ['default-wishlist'],
	queryFn: getMyDefaultWishlist,
	staleTime: Infinity,
};

const invalidateDefaultWishlistData = (queryClient: QueryClient) => {
	queryClient.invalidateQueries({ queryKey: ['wishlists'] });
	queryClient.invalidateQueries({ queryKey: ['users', 'wishlist'] });
}

const dropWishlist = (queryClient: QueryClient, wishlistId: string) => {
	queryClient.invalidateQueries({
		queryKey: ['wishlists'],
		predicate: query => !query.queryKey.includes(wishlistId),
	});
	queryClient.removeQueries({
		queryKey: ['wishlists'],
		predicate: query => query.queryKey.includes(wishlistId),
	});
}

export const useDefaultWishlist = () => {
	return useQuery(defaultWishlistQuery);
}

export const useDefaultWishlistId = () => {
	const { data } = useDefaultWishlist();
	return data?.id ?? null;
}

export const useAddToDefaultWishlist = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: async (gameId: string) => {
			const defaultWishlist = await queryClient.fetchQuery(defaultWishlistQuery);
			return addItemToWishlist(defaultWishlist.id, gameId);
		},
		onSuccess: () => invalidateDefaultWishlistData(queryClient),
	});
}

export const useMyWishlists = (params?: ListWishlistsParams) => {
	return useInfiniteQuery({
		queryKey: ['wishlists', 'mine', params?.type ?? 'all', params?.sort ?? 'default'],
		queryFn: ({ pageParam }) => listMyWishlists({ ...params, page: pageParam }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		placeholderData: keepPreviousData,
	});
}

export const useWishlist = (wishlistId: string) => {
	return useQuery({
		queryKey: ['wishlists', 'detail', wishlistId],
		queryFn: () => getWishlistById(wishlistId),
		enabled: !!wishlistId,
	});
}

export const useWishlistItems = (wishlistId: string) => {
	return useInfiniteQuery({
		queryKey: ['wishlists', 'items', wishlistId],
		queryFn: ({ pageParam }) => listWishlistItems(wishlistId, pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: !!wishlistId,
	});
}

export const useWishlistMembers = (wishlistId: string, enabled: boolean) => {
	return useQuery({
		queryKey: ['wishlists', 'members', wishlistId],
		queryFn: () => listWishlistMembers(wishlistId),
		enabled: enabled && !!wishlistId,
	});
}

export const useAddItemToWishlist = (wishlistId: string) => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (gameId: string) => addItemToWishlist(wishlistId, gameId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'items', wishlistId] });
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'preview-items', wishlistId] });
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'item-status', wishlistId] });
		},
	});
}

export const useRemoveItemFromWishlist = (wishlistId: string) => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (itemId: string) => removeItemFromWishlist(wishlistId, itemId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'items', wishlistId] });
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'preview-items', wishlistId] });
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'item-status', wishlistId] });
		},
	});
}

export const useAddMemberToWishlist = (wishlistId: string) => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (userId: string) => addMemberToWishlist(wishlistId, userId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'members', wishlistId] });
		},
	});
}

export const useRemoveMemberFromWishlist = (wishlistId: string) => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (memberUserId: string) => removeMemberFromWishlist(wishlistId, memberUserId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'members', wishlistId] });
		},
	});
}

export const useDeleteWishlist = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (wishlistId: string) => deleteWishlist(wishlistId),
		onSuccess: (_, wishlistId) => dropWishlist(queryClient, wishlistId),
	});
}

export const useLeaveWishlist = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (wishlistId: string) => leaveWishlist(wishlistId),
		onSuccess: (_, wishlistId) => dropWishlist(queryClient, wishlistId),
	});
}

export const useUpdateWishlist = (wishlistId: string) => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (name: string) => updateWishlist(wishlistId, name),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
		},
	});
}