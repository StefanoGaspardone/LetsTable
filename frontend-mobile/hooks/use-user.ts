import { useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query';

import { getUserDefaultWishlistItems, getUserFriends, getUserMatches, searchUser, updateMe } from '@/api/user';

import { UpdateUserPayload, User } from '@/types/user';

export const useUserSearch = (query: string) => {
	return useQuery({
		queryKey: ['users', 'search', query],
		queryFn: () => searchUser(query),
		enabled: query.length > 0,
	});
}

export const useUpdateMe = (onUserUpdated: (user: User) => void) => {
	return useMutation({
		mutationFn: (payload: UpdateUserPayload) => updateMe(payload),
		onSuccess: (updatedUser) => {
			onUserUpdated(updatedUser);
		},
	});
}

export const useUserMatches = (userId: string, sort?: string) => {
	return useInfiniteQuery({
		queryKey: ['users', 'matches', userId, sort ?? 'default'],
		queryFn: ({ pageParam }) => getUserMatches(userId, pageParam, 20, sort),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
	});
}

export const useUserFriends = (userId: string) => {
	return useQuery({
		queryKey: ['users', 'friends', userId],
		queryFn: () => getUserFriends(userId),
	});
}

export const useUserDefaultWishlistItems = (userId: string) => {
	return useInfiniteQuery({
		queryKey: ['users', 'wishlist', userId],
		queryFn: ({ pageParam }) => getUserDefaultWishlistItems(userId, pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
	});
}