import type { Match } from '@/types/match';

export type AccountStatus = 'ACTIVE' | 'INACTIVE' | 'DELETED' | 'SUSPENDED';

export interface User {
	id: string;
	username: string;
	email: string;
	role: string;
	avatarId: string | null;
	notificationsEnabled: boolean;
}

export interface AdminUser {
    id: string;
    username: string;
    email: string;
    role: string;
    accountStatus: AccountStatus;
    avatarId: string | null;   
    notificationsEnabled: boolean;
    createdAt: string; 
}

export interface AdminUserDetail {
    user: AdminUser;
    totalMatches: number;
    totalWins: number;
    collectionCount: number;
    recentMatches: Match[];
}