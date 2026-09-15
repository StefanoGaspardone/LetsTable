import type { User } from '@/types/user';

export interface LoginPayload {
	identifier: string;
	password: string;
}

export interface AuthResponse {
	accessToken: string;
	refreshToken: string;
	user: User;
}

export interface CreateAdminPayload {
	username: string;
	email: string;
	password: string;
}