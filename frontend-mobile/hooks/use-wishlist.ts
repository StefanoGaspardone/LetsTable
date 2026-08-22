import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { addItemToWishlist, getWishlistById, listMyWishlists, listWishlistItems, listWishlistMembers } from '@/api/wishlist';

export function useAddToDefaultWishlist() {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: async (gameId: string) => {
			const wishlists = await listMyWishlists();
			const defaultWishlist = wishlists.find(w => w.isDefault);

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

export const useMyWishlists = () => {
	return useQuery({
		queryKey: ['wishlists', 'mine'],
		queryFn: () => listMyWishlists(),
	});
}

export const useDefaultWishlistId = () => {
	const { data } = useMyWishlists();
	return data?.find((w) => w.isDefault)?.id ?? null;
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