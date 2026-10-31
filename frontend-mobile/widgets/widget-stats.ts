import AsyncStorage from '@react-native-async-storage/async-storage';

import { getMyStats } from '@/api/stats';

import { tokenStorage } from '@/lib/token-storage';

import { UserStats } from '@/types/stats';

const STORAGE_KEY = '@widget_stats';

export const loadWidgetStats = async (): Promise<UserStats | null> => {
	try {
		const stored = await AsyncStorage.getItem(STORAGE_KEY);
		return stored ? JSON.parse(stored) as UserStats : null;
	} catch {
		return null;
	}
}

export const saveWidgetStats = async (stats: UserStats | null): Promise<void> => {
	if(stats) await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(stats));
	else await AsyncStorage.removeItem(STORAGE_KEY);
}

export const fetchWidgetStats = async (): Promise<UserStats | null | undefined> => {
	const accessToken = await tokenStorage.getAccessToken();
	if(!accessToken) return null;

	try {
		return await getMyStats();
	} catch {
		return undefined;
	}
}