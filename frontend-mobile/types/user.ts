export interface User {
	id: string;
	username: string;
	email: string;
	role: string;
	avatarId: string | null;
	notificationsEnabled: boolean;
}

export interface UpdateUserPayload {
	username?: string | null;
	notificationsEnabled?: boolean | null;
	avatarId?: string | null;
	removeAvatar?: boolean;
}