import { apiClient } from '@/api/client';

import { AuthResponse, ForgotPasswordPayload, LoginPayload, RegisterPayload, ResetPasswordPayload, SignupResponse } from '@/types/auth';

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

export const forgotPassword = async (payload: ForgotPasswordPayload): Promise<void> => {
    await apiClient.post('/auth/password/forgot', payload);
}

export const resetPassword = async (payload: ResetPasswordPayload): Promise<void> => {
    await apiClient.post('/auth/password/reset', payload);
}