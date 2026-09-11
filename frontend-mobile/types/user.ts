export interface User {
	id: string;
	username: string;
	email: string;
	role: string;
	avatarUrl: string;
	notificationsEnabled: boolean;
}

export interface UpdateUserPayload {
	username?: string | null;
	notificationsEnabled?: boolean | null;
}