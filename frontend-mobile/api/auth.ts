import { apiClient } from '@/api/client';

import { AuthResponse, LoginPayload, RegisterPayload, SignupResponse } from '@/types/auth';

export const signup = async (payload: RegisterPayload): Promise<SignupResponse> => {
    const { data } = await apiClient.post<SignupResponse>('/auth/signup', payload);
    return data;
}

export const activate = async (identifier: string, otpCode: string): Promise<void> => {
    await apiClient.post('/auth/activate', { identifier, otpCode });
}

export const resendActivationOtp = async (identifier: string): Promise<void> => {
    await apiClient.post('/auth/activate/resend', { identifier });
}

export const login = async (payload: LoginPayload): Promise<AuthResponse> => {
    const { data } = await apiClient.post<AuthResponse>('/auth/login', payload);
    return data;
}

export const forgotPassword = async (identifier: string): Promise<void> => {
    await apiClient.post('/auth/password/forgot', { identifier });
}

export const resetPassword = async (identifier: string, otpCode: string, newPassword: string): Promise<void> => {
    await apiClient.post('/auth/password/reset', { identifier, otpCode, newPassword });
}