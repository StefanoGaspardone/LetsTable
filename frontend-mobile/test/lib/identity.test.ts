import { identityKey } from '@/lib/identity';

describe('identityKey', () => {
	it('builds a key prefixed with "u:" when userId is present', () => {
		expect(identityKey({ userId: 'user-123', guestName: null })).toBe('u:user-123');
	});

	it('ignores guestName when userId is present', () => {
		expect(identityKey({ userId: 'user-123', guestName: 'Marco' })).toBe('u:user-123');
	});

	it('builds a key prefixed with "g:" when userId is null', () => {
		expect(identityKey({ userId: null, guestName: 'Marco' })).toBe('g:marco');
	});

	it('lowercases the guest name', () => {
		expect(identityKey({ userId: null, guestName: 'MARCO' })).toBe('g:marco');
	});

	it('trims whitespace from the guest name', () => {
		expect(identityKey({ userId: null, guestName: '  Marco  ' })).toBe('g:marco');
	});

	it('trims and lowercases together', () => {
		expect(identityKey({ userId: null, guestName: '  MaRcO Rossi  ' })).toBe('g:marco rossi');
	});

	it('treats two guest names differing only by case and spacing as the same identity', () => {
		const key1 = identityKey({ userId: null, guestName: 'Marco' });
		const key2 = identityKey({ userId: null, guestName: '  marco  ' });

		expect(key1).toBe(key2);
	});

	it('returns "g:" when guestName is null', () => {
		expect(identityKey({ userId: null, guestName: null })).toBe('g:');
	});

	it('returns "g:" when guestName is an empty string', () => {
		expect(identityKey({ userId: null, guestName: '' })).toBe('g:');
	});

	it('produces different keys for different userIds', () => {
		const key1 = identityKey({ userId: 'user-1', guestName: null });
		const key2 = identityKey({ userId: 'user-2', guestName: null });

		expect(key1).not.toBe(key2);
	});

	it('produces different keys for a registered user and a guest with a similar name', () => {
		const userKey = identityKey({ userId: 'user-1', guestName: null });
		const guestKey = identityKey({ userId: null, guestName: 'user-1' });

		expect(userKey).not.toBe(guestKey);
	});
});