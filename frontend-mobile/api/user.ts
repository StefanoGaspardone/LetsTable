import { apiClient } from '@/api/client';

import { UpdateUserPayload, User, UserProfile } from '@/types/user';

export const searchUser = async (query: string): Promise<User[]> => {
    const { data } = await apiClient.get<User[]>('/users/search', { params: { query } });
    return data;
}

export const getMe = async (): Promise<User> => {
    const { data } = await apiClient.get<User>('/users/me');
    return data;
}

export const getById = async (userId: string): Promise<User> => {
    const { data } = await apiClient.get<User>(`/users/${userId}`);
    return data;
}

export const updateMe = async (payload: UpdateUserPayload): Promise<User> => {
	const { data } = await apiClient.patch<User>('/users/me', payload);
	return data;
}

export const getUserProfile = async (userId: string): Promise<UserProfile> => {
    const { data } = await apiClient.get<UserProfile>(`/users/${userId}/profile`);
    return data;
}