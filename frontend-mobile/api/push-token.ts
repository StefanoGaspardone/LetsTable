import { apiClient } from '@/api/client';

import { RegisterPushTokenPayload } from '@/types/push-token';

export const registerPushToken = async (payload: RegisterPushTokenPayload): Promise<void> => {
	await apiClient.post('/push-tokens', payload);
}

export const unregisterPushToken = async (token: string): Promise<void> => {
	await apiClient.delete('/push-tokens', { params: { token } });
}