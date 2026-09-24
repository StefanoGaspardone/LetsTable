import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { addItemToWishlist, addMemberToWishlist, deleteWishlist, getWishlistById, leaveWishlist, listMyWishlists, listWishlistItems, listWishlistMembers, ListWishlistsParams, removeItemFromWishlist, removeMemberFromWishlist } from '@/api/wishlist';

export const useAddToDefaultWishlist = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: async (gameId: string) => {
			const defaultWishlist = await findDefaultWishlist();

			if(!defaultWishlist) {
				throw new Error('Default wishlist not found');
			}

			return addItemToWishlist(defaultWishlist.id, gameId);
		},
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
		},
	});
}

const findDefaultWishlist = async () => {
	let page = 0;
	
	while(true) {
		const result = await listMyWishlists({ page, size: 50 });
		const found = result.content.find(w => w.isDefault);
		
		if(found) return found;
		if(result.last) return null;
		
		page += 1;
	}
}

export const useMyWishlists = (params?: ListWishlistsParams) => {
	return useInfiniteQuery({
		queryKey: ['wishlists', 'mine', params?.type ?? 'all'],
		queryFn: ({ pageParam }) => listMyWishlists({ ...params, page: pageParam }),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
	});
}

export const useDefaultWishlist = () => {
	return useQuery({
		queryKey: ['wishlists', 'default'],
		queryFn: findDefaultWishlist,
	});
}

export const useDefaultWishlistId = () => {
	const { data } = useDefaultWishlist();
	return data?.id ?? null;
}

export const useWishlist = (wishlistId: string) => {
	return useQuery({
		queryKey: ['wishlists', 'detail', wishlistId],
		queryFn: () => getWishlistById(wishlistId),
	});
}

export const useWishlistItems = (wishlistId: string) => {
	return useInfiniteQuery({
		queryKey: ['wishlists', 'items', wishlistId],
		queryFn: ({ pageParam }) => listWishlistItems(wishlistId, pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
	});
}

export const useWishlistMembers = (wishlistId: string, enabled: boolean) => {
	return useQuery({
		queryKey: ['wishlists', 'members', wishlistId],
		queryFn: () => listWishlistMembers(wishlistId),
		enabled,
	});
}

export const useAddItemToWishlist = (wishlistId: string) => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (gameId: string) => addItemToWishlist(wishlistId, gameId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'items', wishlistId] });
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'preview-items', wishlistId] });
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
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
		},
	});
}

export const useLeaveWishlist = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (wishlistId: string) => leaveWishlist(wishlistId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
		},
	});
}