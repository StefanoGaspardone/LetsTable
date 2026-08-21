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

export interface UserResponse {
	id: string;
	username: string;
	email: string;
	role: string;
}

export interface AuthResponse {
	accessToken: string;
	refreshToken: string;
	user: UserResponse;
}