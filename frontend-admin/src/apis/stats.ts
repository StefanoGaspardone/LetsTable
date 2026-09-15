import axiosInstance from '@/apis/axiosConfig';
import { handleApiError } from '@/apis/axiosError';

import type { AdminStats } from '@/types/stats';

export const getStats = async (): Promise<AdminStats> => {
    try {
        const res = await axiosInstance.get<AdminStats>('/admin/stats');
        return res.data;
    } catch (error) {
        handleApiError(error, {
            fallback: 'Unable to fetch dashboard statistics.',
            statusMessages: {
                401: 'Session expired. Please sign in again.',
                403: 'Access denied. Admin privileges required.',
            },
            toastError: true,
        });
    }
}