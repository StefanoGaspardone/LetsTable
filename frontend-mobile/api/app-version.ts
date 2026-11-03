import { isAxiosError } from 'axios';

import { apiClient } from '@/api/client';

import { AppVersionInfo } from '@/types/app-version';

export const getLatestAppVersion = async (): Promise<AppVersionInfo | null> => {
	try {
		const { data } = await apiClient.get<AppVersionInfo>('/app/version');
		return data;
	} catch(error) {
		if(isAxiosError(error) && error.response?.status === 404) return null;
		throw error;
	}
}