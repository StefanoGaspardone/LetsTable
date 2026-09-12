import { apiClient } from '@/api/client';
import {
	listFriends,
	listPendingReceived,
	listPendingSent,
	sendFriendRequest,
	acceptFriendRequest,
	rejectFriendRequest,
	cancelFriendRequest,
	removeFriend,
} from '@/api/friend';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		post: jest.fn(),
		delete: jest.fn(),
	},
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;
const mockedPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;
const mockedDelete = apiClient.delete as jest.MockedFunction<typeof apiClient.delete>;

describe('friend API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('listFriends', () => {
		it('calls GET /friends and returns the response data', async () => {
			const responseData = [{ id: 'user-1', username: 'marco' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listFriends();

			expect(mockedGet).toHaveBeenCalledWith('/friends');
			expect(result).toEqual(responseData);
		});
	});

	describe('listPendingReceived', () => {
		it('calls GET /friends/requests/received and returns the response data', async () => {
			const responseData = [{ id: 'req-1' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listPendingReceived();

			expect(mockedGet).toHaveBeenCalledWith('/friends/requests/received');
			expect(result).toEqual(responseData);
		});
	});

	describe('listPendingSent', () => {
		it('calls GET /friends/requests/sent and returns the response data', async () => {
			const responseData = [{ id: 'req-2' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listPendingSent();

			expect(mockedGet).toHaveBeenCalledWith('/friends/requests/sent');
			expect(result).toEqual(responseData);
		});
	});

	describe('sendFriendRequest', () => {
		it('posts to /friends/requests with the receiverId and returns the response data', async () => {
			const responseData = { id: 'req-1', receiverId: 'user-2' };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await sendFriendRequest('user-2');

			expect(mockedPost).toHaveBeenCalledWith('/friends/requests', { receiverId: 'user-2' });
			expect(result).toEqual(responseData);
		});
	});

	describe('acceptFriendRequest', () => {
		it('posts to /friends/requests/:requestId/accept and returns the response data', async () => {
			const responseData = { id: 'req-1', status: 'ACCEPTED' };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await acceptFriendRequest('req-1');

			expect(mockedPost).toHaveBeenCalledWith('/friends/requests/req-1/accept');
			expect(result).toEqual(responseData);
		});
	});

	describe('rejectFriendRequest', () => {
		it('posts to /friends/requests/:requestId/reject', async () => {
			mockedPost.mockResolvedValueOnce({} as any);

			await rejectFriendRequest('req-1');

			expect(mockedPost).toHaveBeenCalledWith('/friends/requests/req-1/reject');
		});
	});

	describe('cancelFriendRequest', () => {
		it('calls DELETE on /friends/requests/:requestId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await cancelFriendRequest('req-1');

			expect(mockedDelete).toHaveBeenCalledWith('/friends/requests/req-1');
		});
	});

	describe('removeFriend', () => {
		it('calls DELETE on /friends/:friendUserId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await removeFriend('user-2');

			expect(mockedDelete).toHaveBeenCalledWith('/friends/user-2');
		});
	});
});