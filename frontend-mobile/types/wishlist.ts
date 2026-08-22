export interface WishlistItemStatus {
	inWishlist: boolean;
	itemId: string | null;
}

export interface UserSummary {
	id: string;
	username: string;
	email: string;
	role: string;
}

export interface Wishlist {
	id: string;
	name: string;
	owner: UserSummary;
	isShared: boolean;
	isDefault: boolean;
	createdAt: string;
}

export interface WishlistItem {
	id: string;
	game: { id: string; bggId: number; name: string; thumbnailUrl: string | null };
	addedBy: UserSummary;
	addedAt: string;
}

export interface WishlistMember {
	id: string;
	user: UserSummary;
	addedAt: string;
}