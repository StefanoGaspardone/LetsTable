import { apiClient } from '@/api/client';
import { searchUser, getMe, getById, updateMe, getUserProfile, getUserMatches, getUserFriends, getUserDefaultWishlistItems } from '@/api/user';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		patch: jest.fn(),
	},
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;
const mockedPatch = apiClient.patch as jest.MockedFunction<typeof apiClient.patch>;

describe('user API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('searchUser', () => {
		it('calls GET /users/search with the query param and returns the response data', async () => {
			const responseData = [{ id: 'user-1', username: 'marco' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await searchUser('marco');

			expect(mockedGet).toHaveBeenCalledWith('/users/search', { params: { query: 'marco' } });
			expect(result).toEqual(responseData);
		});
	});

	describe('getMe', () => {
		it('calls GET /users/me and returns the response data', async () => {
			const responseData = { id: 'user-1', username: 'stefano' };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getMe();

			expect(mockedGet).toHaveBeenCalledWith('/users/me');
			expect(result).toEqual(responseData);
		});
	});

	describe('getById', () => {
		it('calls GET /users/:userId and returns the response data', async () => {
			const responseData = { id: 'user-2', username: 'anna' };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getById('user-2');

			expect(mockedGet).toHaveBeenCalledWith('/users/user-2');
			expect(result).toEqual(responseData);
		});
	});

	describe('updateMe', () => {
		it('patches /users/me with the given payload and returns the response data', async () => {
			const payload = { username: 'newname' };
			const responseData = { id: 'user-1', username: 'newname' };
			mockedPatch.mockResolvedValueOnce({ data: responseData } as any);

			const result = await updateMe(payload);

			expect(mockedPatch).toHaveBeenCalledWith('/users/me', payload);
			expect(result).toEqual(responseData);
		});

		it('patches with only notificationsEnabled when username is not provided', async () => {
			const payload = { notificationsEnabled: false };
			const responseData = { id: 'user-1', notificationsEnabled: false };
			mockedPatch.mockResolvedValueOnce({ data: responseData } as any);

			const result = await updateMe(payload);

			expect(mockedPatch).toHaveBeenCalledWith('/users/me', payload);
			expect(result).toEqual(responseData);
		});
	});

		describe('getUserProfile', () => {
		it('calls GET /users/:userId/profile and returns the response data', async () => {
			const responseData = { user: { id: 'user-2', username: 'anna' }, totalMatches: 5, totalWins: 2 };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getUserProfile('user-2');

			expect(mockedGet).toHaveBeenCalledWith('/users/user-2/profile');
			expect(result).toEqual(responseData);
		});
	});

	describe('getUserMatches', () => {
		it('calls GET /users/:userId/matches with page and default size', async () => {
			const responseData = { content: [], number: 0, last: true };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getUserMatches('user-2', 0);

			expect(mockedGet).toHaveBeenCalledWith('/users/user-2/matches', {
				params: { page: 0, size: 20, sort: undefined },
			});
			expect(result).toEqual(responseData);
		});

		it('uses the given size and sort when provided', async () => {
			const responseData = { content: [], number: 1, last: true };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			await getUserMatches('user-2', 1, 5, 'playedAt-desc');

			expect(mockedGet).toHaveBeenCalledWith('/users/user-2/matches', {
				params: { page: 1, size: 5, sort: 'playedAt-desc' },
			});
		});
	});

	describe('getUserFriends', () => {
		it('calls GET /users/:userId/friends and returns the response data', async () => {
			const responseData = [{ id: 'friend-1', username: 'luca' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getUserFriends('user-2');

			expect(mockedGet).toHaveBeenCalledWith('/users/user-2/friends');
			expect(result).toEqual(responseData);
		});
	});

	describe('getUserDefaultWishlistItems', () => {
		it('calls GET /users/:userId/wishlist with page and default size', async () => {
			const responseData = { content: [], number: 0, last: true };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getUserDefaultWishlistItems('user-2', 0);

			expect(mockedGet).toHaveBeenCalledWith('/users/user-2/wishlist', {
				params: { page: 0, size: 20 },
			});
			expect(result).toEqual(responseData);
		});

		it('uses the given size when provided', async () => {
			const responseData = { content: [], number: 1, last: true };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			await getUserDefaultWishlistItems('user-2', 1, 5);

			expect(mockedGet).toHaveBeenCalledWith('/users/user-2/wishlist', {
				params: { page: 1, size: 5 },
			});
		});
	});
});