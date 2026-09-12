import { apiClient } from '@/api/client';
import { listCollection, addToCollection, removeFromCollection, getGameStatusInCollection } from '@/api/collection';

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

describe('collection API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('listCollection', () => {
		it('calls GET /collection with the given page and default size', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listCollection({ page: 0 });

			expect(mockedGet).toHaveBeenCalledWith('/collection', {
				params: {
					page: 0,
					size: 20,
					gameName: undefined,
					played: undefined,
					isExpansion: undefined,
				},
			});
		});

		it('uses the given size instead of the default when provided', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listCollection({ page: 1, size: 50 });

			expect(mockedGet).toHaveBeenCalledWith('/collection', {
				params: expect.objectContaining({ size: 50 }),
			});
		});

		it('passes gameName through when it is a non-empty string', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listCollection({ page: 0, gameName: 'Catan' });

			expect(mockedGet).toHaveBeenCalledWith('/collection', {
				params: expect.objectContaining({ gameName: 'Catan' }),
			});
		});

		it('omits gameName as undefined when it is an empty string', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listCollection({ page: 0, gameName: '' });

			expect(mockedGet).toHaveBeenCalledWith('/collection', {
				params: expect.objectContaining({ gameName: undefined }),
			});
		});

		it('passes played and isExpansion filters through when provided', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listCollection({ page: 0, played: true, isExpansion: false });

			expect(mockedGet).toHaveBeenCalledWith('/collection', {
				params: expect.objectContaining({ played: true, isExpansion: false }),
			});
		});

		it('returns the response data', async () => {
			const responseData = { content: [{ id: 'item-1' }], totalElements: 1 };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listCollection({ page: 0 });

			expect(result).toEqual(responseData);
		});
	});

	describe('addToCollection', () => {
		it('posts to /collection with the given gameId and returns the response data', async () => {
			const responseData = { id: 'item-1', gameId: 'game-1' };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await addToCollection('game-1');

			expect(mockedPost).toHaveBeenCalledWith('/collection', { gameId: 'game-1' });
			expect(result).toEqual(responseData);
		});
	});

	describe('removeFromCollection', () => {
		it('calls DELETE on /collection/:itemId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await removeFromCollection('item-1');

			expect(mockedDelete).toHaveBeenCalledWith('/collection/item-1');
		});
	});

	describe('getGameStatusInCollection', () => {
		it('calls GET /collection/status with the gameId param and returns the response data', async () => {
			const responseData = { inCollection: true, itemId: 'item-1' };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getGameStatusInCollection('game-1');

			expect(mockedGet).toHaveBeenCalledWith('/collection/status', {
				params: { gameId: 'game-1' },
			});
			expect(result).toEqual(responseData);
		});
	});
});