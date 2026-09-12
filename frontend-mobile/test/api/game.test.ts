import { apiClient } from '@/api/client';
import { searchGame, getHotGames, getGameByBggId, getGameExpansions, getRecentGames } from '@/api/game';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
	},
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;

describe('game API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('searchGame', () => {
		it('calls GET /games/search with query, page, and default size', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await searchGame('catan', 0);

			expect(mockedGet).toHaveBeenCalledWith('/games/search', {
				params: { query: 'catan', page: 0, size: 20 },
			});
		});

		it('uses the given size instead of the default when provided', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await searchGame('catan', 1, 50);

			expect(mockedGet).toHaveBeenCalledWith('/games/search', {
				params: { query: 'catan', page: 1, size: 50 },
			});
		});

		it('returns the response data', async () => {
			const responseData = { content: [{ id: 'game-1' }] };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await searchGame('catan', 0);

			expect(result).toEqual(responseData);
		});
	});

	describe('getHotGames', () => {
		it('calls GET /games/hot with page and default size', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await getHotGames(0);

			expect(mockedGet).toHaveBeenCalledWith('/games/hot', {
				params: { page: 0, size: 20 },
			});
		});

		it('uses the given size instead of the default when provided', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await getHotGames(2, 30);

			expect(mockedGet).toHaveBeenCalledWith('/games/hot', {
				params: { page: 2, size: 30 },
			});
		});
	});

	describe('getGameByBggId', () => {
		it('calls GET /games/:bggId and returns the response data', async () => {
			const responseData = { id: 'game-1', name: 'Catan', bggId: 13 };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getGameByBggId(13);

			expect(mockedGet).toHaveBeenCalledWith('/games/13');
			expect(result).toEqual(responseData);
		});
	});

	describe('getGameExpansions', () => {
		it('calls GET /games/:bggId/expansions with page and default size', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await getGameExpansions(13, 0);

			expect(mockedGet).toHaveBeenCalledWith('/games/13/expansions', {
				params: { page: 0, size: 10 },
			});
		});

		it('uses the given size instead of the default when provided', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await getGameExpansions(13, 1, 5);

			expect(mockedGet).toHaveBeenCalledWith('/games/13/expansions', {
				params: { page: 1, size: 5 },
			});
		});
	});

	describe('getRecentGames', () => {
		it('calls GET /matches/recent-games and returns the response data', async () => {
			const responseData = [{ id: 'game-1' }, { id: 'game-2' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getRecentGames();

			expect(mockedGet).toHaveBeenCalledWith('/matches/recent-games');
			expect(result).toEqual(responseData);
		});
	});
});