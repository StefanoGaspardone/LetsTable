import { apiClient } from '@/api/client';

import { Achievement } from '@/types/achievement';

export const getMyAchievements = async (): Promise<Achievement[]> => {
	const { data } = await apiClient.get<Achievement[]>('/achievements/me');
	return data;
}

export const getUnseenAchievements = async (): Promise<Achievement[]> => {
	const { data } = await apiClient.get<Achievement[]>('/achievements/unseen');
	return data;
}

export const markAchievementsSeen = async (codes: string[]): Promise<void> => {
	await apiClient.post('/achievements/seen', { codes });
}

export const getUserAchievements = async (userId: string): Promise<Achievement[]> => {
	const { data } = await apiClient.get<Achievement[]>(`/users/${userId}/achievements`);
	return data;
}