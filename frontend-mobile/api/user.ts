import { apiClient } from '@/api/client';

import { User } from '@/types/user';

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