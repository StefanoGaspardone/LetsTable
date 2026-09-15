import axiosInstance from '@/apis/axiosConfig';
import { handleApiError } from '@/apis/axiosError';

import { toast } from '@/components/ui/toast';

import type { AdminUser, AdminUserDetail } from '@/types/user';
import type { CreateAdminPayload } from '@/types/auth';
import type { Page } from '@/types/page';

export interface ListUsersParams {
    role?: string;
    status?: string;
    search?: string;
    page?: number;
    size?: number;
    sort?: string;
}

export const listUsers = async (params?: ListUsersParams): Promise<Page<AdminUser>> => {
    try {
        const res = await axiosInstance.get<Page<AdminUser>>('/admin/users', { params });
        return res.data;
    } catch (error) {
        handleApiError(error, {
            fallback: 'Unable to fetch users list.',
            statusMessages: {
                401: 'Session expired. Please sign in again.',
                403: 'Access denied. Admin privileges required.',
            },
            toastError: true,
        });
    }
}

export const getUserDetail = async (userId: string): Promise<AdminUserDetail> => {
    try {
        const res = await axiosInstance.get<AdminUserDetail>(`/admin/users/${userId}`);
        return res.data;
    } catch (error) {
        handleApiError(error, {
            fallback: 'Unable to fetch user details.',
            statusMessages: {
                401: 'Session expired. Please sign in again.',
                403: 'Access denied. Admin privileges required.',
                404: 'User not found.',
            },
            toastError: true,
        });
    }
}

export const suspendUser = async (userId: string): Promise<AdminUser> => {
    try {
        const res = await axiosInstance.patch<AdminUser>(`/admin/users/${userId}/suspend`);
        toast.add({ type: 'success', description: 'User account suspended.' });
        return res.data;
    } catch (error) {
        handleApiError(error, {
            fallback: 'Unable to suspend user account.',
            statusMessages: {
                401: 'Session expired. Please sign in again.',
                403: 'Access denied. Admin privileges required.',
                404: 'User not found.',
                409: 'Cannot suspend this account (you cannot suspend yourself or account is inactive).',
            },
            toastError: true,
        });
    }
}

export const reactivateUser = async (userId: string): Promise<AdminUser> => {
    try {
        const res = await axiosInstance.patch<AdminUser>(`/admin/users/${userId}/reactivate`);
        toast.add({ type: 'success', description: 'User account reactivated.' });
        return res.data;
    } catch (error) {
        handleApiError(error, {
            fallback: 'Unable to reactivate user account.',
            statusMessages: {
                401: 'Session expired. Please sign in again.',
                403: 'Access denied. Admin privileges required.',
                404: 'User not found.',
                409: 'Only suspended accounts can be reactivated.',
            },
            toastError: true,
        });
    }
}

export const createAdmin = async (request: CreateAdminPayload): Promise<AdminUser> => {
    try {
        const res = await axiosInstance.post<AdminUser>('/admin/users/admin', request);
        toast.add({ type: 'success', description: 'Admin account created successfully.' });
        return res.data;
    } catch (error) {
        handleApiError(error, {
            fallback: 'Unable to create admin account.',
            statusMessages: {
                400: 'Invalid input data. Please check your inputs.',
                401: 'Session expired. Please sign in again.',
                403: 'Access denied. Admin privileges required.',
                409: 'Email or username is already in use.',
            },
            toastError: true,
        });
    }
}