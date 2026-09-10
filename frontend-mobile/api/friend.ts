import { User } from '@/types/user';

import { apiClient } from '@/api/client';

export const listFriends = async (): Promise<User[]> => {
	const { data } = await apiClient.get<User[]>('/friends');
	return data;
}