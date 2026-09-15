import axiosInstance, { setAuthTokens } from '@/apis/axiosConfig';
import { handleApiError } from '@/apis/axiosError';

import { toast } from '@/components/ui/toast';

import type { AuthResponse, CreateAdminPayload, LoginPayload } from '@/types/auth';
import type { User } from '@/types/user';

export const login = async (loginRequest: LoginPayload): Promise<AuthResponse> => {
	try {
		const res = await axiosInstance.post<AuthResponse>('/auth/login', loginRequest);

		setAuthTokens(res.data.accessToken, res.data.refreshToken);
		return res.data;
	} catch(error) {
		handleApiError(error, {
			fallback: 'Invalid credentials.',
			statusMessages: {
				401: 'Invalid credentials.',
				403: 'Account is not activated yet. Please check your email for the verification code.',
			},
			toastError: true,
		});
	}
}

export const logout = async (): Promise<void> => {
	const refreshToken = localStorage.getItem('refreshToken');

	if(!refreshToken) return;

	try {
		await axiosInstance.post('/auth/logout', { refreshToken });
		toast.add({ type: 'success', description: 'Successfully logged out!' });
	} catch(error) {
		handleApiError(error, {
			fallback: 'Unable to logout. Please try again.',
			statusMessages: {
				401: 'Session expired. Please sign in again.',
			},
			toastError: true,
		});
	}
}

export const createAdmin = async (request: CreateAdminPayload): Promise<User> => {
	try {
		const res = await axiosInstance.post<User>('/admin/users/admin', request);
		toast.add({ type: 'success', description: 'Admin account created.' });
		return res.data;
	} catch(error) {
		handleApiError(error, {
			fallback: 'Unable to create admin account.',
			statusMessages: {
				409: 'Email or username already in use.',
			},
			toastError: true,
		});
	}
}