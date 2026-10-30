import { keepPreviousData, useInfiniteQuery, useMutation, useQuery } from '@tanstack/react-query';

import { getUserDefaultWishlistItems, getUserFriends, getUserMatches, getUserProfile, searchUser, updateMe } from '@/api/user';

import { UpdateUserPayload, User } from '@/types/user';

export const useUserSearch = (query: string) => {
	return useQuery({
		queryKey: ['users', 'search', query],
		queryFn: () => searchUser(query),
		enabled: query.length > 0,
		placeholderData: keepPreviousData,
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

export const useUserProfile = (userId: string) => {
	return useQuery({
		queryKey: ['users', 'profile', userId],
		queryFn: () => getUserProfile(userId),
		enabled: !!userId,
	});
}

export const useUserMatches = (userId: string, sort?: string, enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['users', 'matches', userId, sort ?? 'default'],
		queryFn: ({ pageParam }) => getUserMatches(userId, pageParam, 20, sort),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: enabled && !!userId,
	});
}

export const useUserFriends = (userId: string, enabled = true) => {
	return useQuery({
		queryKey: ['users', 'friends', userId],
		queryFn: () => getUserFriends(userId),
		enabled: enabled && !!userId,
	});
}

export const useUserDefaultWishlistItems = (userId: string, enabled = true) => {
	return useInfiniteQuery({
		queryKey: ['users', 'wishlist', userId],
		queryFn: ({ pageParam }) => getUserDefaultWishlistItems(userId, pageParam),
		initialPageParam: 0,
		getNextPageParam: (lastPage) => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: enabled && !!userId,
	});
}