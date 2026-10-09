import { User } from '@/types/user';

export interface RegisterPayload {
	username: string;
	email: string;
	password: string;
}

export interface SignupResponse {
	email: string;
	message: string;
}

export interface LoginPayload {
	identifier: string;
	password: string;
}

export interface AuthResponse {
	accessToken: string;
	refreshToken: string;
	user: User;
}

export interface ForgotPasswordPayload {
	identifier: string;
}

export interface ResetPasswordPayload {
	identifier: string;
	otpCode: string;
	newPassword: string;
}