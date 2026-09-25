import { apiClient } from '@/api/client';

import { Match } from '@/types/match';
import { PageDTO } from '@/types/page';
import { UpdateUserPayload, User, UserProfile } from '@/types/user';
import { WishlistItem } from '@/types/wishlist';

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

export const getUserMatches = async (userId: string, page: number, size = 20, sort?: string): Promise<PageDTO<Match>> => {
    const { data } = await apiClient.get<PageDTO<Match>>(`/users/${userId}/matches`, {
        params: { page, size, sort },
    });

    return data;
}

export const getUserFriends = async (userId: string): Promise<User[]> => {
    const { data } = await apiClient.get<User[]>(`/users/${userId}/friends`);
    return data;
}

export const getUserDefaultWishlistItems = async (userId: string, page: number, size = 20): Promise<PageDTO<WishlistItem>> => {
    const { data } = await apiClient.get<PageDTO<WishlistItem>>(`/users/${userId}/wishlist`, {
        params: { page, size },
    });

    return data;
}