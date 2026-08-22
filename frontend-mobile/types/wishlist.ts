import { User } from '@/types/user';

export interface WishlistItemStatus {
	inWishlist: boolean;
	itemId: string | null;
}

export interface Wishlist {
	id: string;
	name: string;
	owner: User;
	isShared: boolean;
	isDefault: boolean;
	createdAt: string;
}

export interface WishlistItem {
	id: string;
	game: { id: string; bggId: number; name: string; thumbnailUrl: string | null };
	addedBy: User;
	addedAt: string;
}

export interface WishlistMember {
	id: string;
	user: User;
	addedAt: string;
}