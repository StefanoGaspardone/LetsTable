import { User } from '@/types/user';

export interface FriendRequest {
	id: string;
	sender: User;
	receiver: User;
	status: string;
	createdAt: string;
}