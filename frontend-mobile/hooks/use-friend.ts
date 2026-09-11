import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { listFriends, listPendingReceived, listPendingSent, sendFriendRequest, acceptFriendRequest, rejectFriendRequest, cancelFriendRequest, removeFriend } from '@/api/friend';

export const useFriends = () => {
	return useQuery({
		queryKey: ['friends', 'mine'],
		queryFn: () => listFriends(),
	});
}

export const usePendingReceived = () => {
	return useQuery({
		queryKey: ['friends', 'requests', 'received'],
		queryFn: () => listPendingReceived(),
	});
}

export const usePendingSent = () => {
	return useQuery({
		queryKey: ['friends', 'requests', 'sent'],
		queryFn: () => listPendingSent(),
	});
}

export const useSendFriendRequest = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (receiverId: string) => sendFriendRequest(receiverId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['friends'] });
			queryClient.invalidateQueries({ queryKey: ['users', 'search'] });
		},
	});
}

export const useAcceptFriendRequest = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (requestId: string) => acceptFriendRequest(requestId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['friends'] });
		},
	});
}

export const useRejectFriendRequest = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (requestId: string) => rejectFriendRequest(requestId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['friends'] });
		},
	});
}

export const useCancelFriendRequest = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (requestId: string) => cancelFriendRequest(requestId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['friends'] });
		},
	});
}

export const useRemoveFriend = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (friendUserId: string) => removeFriend(friendUserId),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['friends'] });
		},
	});
}