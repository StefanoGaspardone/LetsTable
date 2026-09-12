import { apiClient } from '@/api/client';
import { searchUser, getMe, getById, updateMe } from '@/api/user';

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
});