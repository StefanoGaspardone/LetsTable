import { User } from '@/types/user';
import { FriendRequest } from '@/types/friend';

import { apiClient } from '@/api/client';

export const listFriends = async (): Promise<User[]> => {
	const { data } = await apiClient.get<User[]>('/friends');
	return data;
}

export const listPendingReceived = async (): Promise<FriendRequest[]> => {
	const { data } = await apiClient.get<FriendRequest[]>('/friends/requests/received');
	return data;
}

export const listPendingSent = async (): Promise<FriendRequest[]> => {
	const { data } = await apiClient.get<FriendRequest[]>('/friends/requests/sent');
	return data;
}

export const sendFriendRequest = async (receiverId: string): Promise<FriendRequest> => {
	const { data } = await apiClient.post<FriendRequest>('/friends/requests', { receiverId });
	return data;
}

export const acceptFriendRequest = async (requestId: string): Promise<FriendRequest> => {
	const { data } = await apiClient.post<FriendRequest>(`/friends/requests/${requestId}/accept`);
	return data;
}

export const rejectFriendRequest = async (requestId: string): Promise<void> => {
	await apiClient.post(`/friends/requests/${requestId}/reject`);
}

export const cancelFriendRequest = async (requestId: string): Promise<void> => {
	await apiClient.delete(`/friends/requests/${requestId}`);
}

export const removeFriend = async (friendUserId: string): Promise<void> => {
	await apiClient.delete(`/friends/${friendUserId}`);
}