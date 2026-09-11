import { useMutation, useQuery } from '@tanstack/react-query';

import { searchUser, updateMe } from '@/api/user';

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