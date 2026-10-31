import { apiClient } from '@/api/client';

import { UserStats } from '@/types/stats';

export const getMyStats = async (): Promise<UserStats> => {
    const { data } = await apiClient.get<UserStats>('/users/me/stats');
    return data;
}