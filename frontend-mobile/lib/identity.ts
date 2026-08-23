export interface PlayerIdentityLike {
	userId: string | null;
	guestName: string | null;
}

export function identityKey(identity: PlayerIdentityLike): string {
	return identity.userId
		? `u:${identity.userId}`
		: `g:${identity.guestName?.trim().toLowerCase() ?? ''}`;
}