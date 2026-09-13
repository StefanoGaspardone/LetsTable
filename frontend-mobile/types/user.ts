import { Match } from '@/types/match';

export type FriendshipStatus = 'SELF' | 'FRIENDS' | 'REQUEST_SENT' | 'REQUEST_RECEIVED' | 'NONE';

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

export interface UserProfile {
	user: User;
	friendshipStatus: FriendshipStatus;
	totalMatches: number;
	totalWins: number;
	recentMatches: Match[];
}