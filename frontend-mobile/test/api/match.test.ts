import { apiClient } from '@/api/client';
import {
	listMatches,
	getMatchById,
	getCalendarMatch,
	deleteMatch,
	createMatch,
	updateMatch,
	getWinStats,
} from '@/api/match';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		post: jest.fn(),
		put: jest.fn(),
		delete: jest.fn(),
	},
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;
const mockedPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;
const mockedPut = apiClient.put as jest.MockedFunction<typeof apiClient.put>;
const mockedDelete = apiClient.delete as jest.MockedFunction<typeof apiClient.delete>;

describe('match API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('listMatches', () => {
		it('calls GET /matches with page and default size, omitting optional filters', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listMatches({ page: 0 });

			expect(mockedGet).toHaveBeenCalledWith('/matches', {
				params: {
					page: 0,
					size: 20,
					gameId: undefined,
					fromDate: undefined,
					toDate: undefined,
					sort: undefined,
				},
			});
		});

		it('uses the given size instead of the default when provided', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listMatches({ page: 0, size: 50 });

			expect(mockedGet).toHaveBeenCalledWith('/matches', {
				params: expect.objectContaining({ size: 50 }),
			});
		});

		it('passes gameId, fromDate, toDate, and sort through when they are non-empty strings', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listMatches({
				page: 0,
				gameId: 'game-1',
				fromDate: '2026-09-01',
				toDate: '2026-09-30',
				sort: 'playedAt-desc',
			});

			expect(mockedGet).toHaveBeenCalledWith('/matches', {
				params: expect.objectContaining({
					gameId: 'game-1',
					fromDate: '2026-09-01',
					toDate: '2026-09-30',
					sort: 'playedAt-desc',
				}),
			});
		});

		it('omits gameId, fromDate, toDate, and sort as undefined when they are empty strings', async () => {
			mockedGet.mockResolvedValueOnce({ data: { content: [] } } as any);

			await listMatches({ page: 0, gameId: '', fromDate: '', toDate: '', sort: '' });

			expect(mockedGet).toHaveBeenCalledWith('/matches', {
				params: expect.objectContaining({
					gameId: undefined,
					fromDate: undefined,
					toDate: undefined,
					sort: undefined,
				}),
			});
		});

		it('returns the response data', async () => {
			const responseData = { content: [{ id: 'match-1' }] };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listMatches({ page: 0 });

			expect(result).toEqual(responseData);
		});
	});

	describe('getMatchById', () => {
		it('calls GET /matches/:matchId and returns the response data', async () => {
			const responseData = { id: 'match-1', isTeamBased: false };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getMatchById('match-1');

			expect(mockedGet).toHaveBeenCalledWith('/matches/match-1');
			expect(result).toEqual(responseData);
		});
	});

	describe('getCalendarMatch', () => {
		it('calls GET /matches/calendar with year and month params', async () => {
			const responseData = [{ date: '2026-09-10', count: 2 }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getCalendarMatch(2026, 9);

			expect(mockedGet).toHaveBeenCalledWith('/matches/calendar', {
				params: { year: 2026, month: 9 },
			});
			expect(result).toEqual(responseData);
		});
	});

	describe('deleteMatch', () => {
		it('calls DELETE on /matches/:matchId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await deleteMatch('match-1');

			expect(mockedDelete).toHaveBeenCalledWith('/matches/match-1');
		});
	});

	describe('createMatch', () => {
		it('posts to /matches with the given payload and returns the response data', async () => {
			const payload = { gameId: 'game-1', isTeamBased: false, playedAt: '2026-09-12' };
			const responseData = { id: 'match-1', ...payload };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await createMatch(payload as any);

			expect(mockedPost).toHaveBeenCalledWith('/matches', payload);
			expect(result).toEqual(responseData);
		});
	});

	describe('updateMatch', () => {
		it('puts to /matches/:matchId with the given payload and returns the response data', async () => {
			const payload = { gameId: 'game-1', isTeamBased: false, playedAt: '2026-09-12' };
			const responseData = { id: 'match-1', ...payload };
			mockedPut.mockResolvedValueOnce({ data: responseData } as any);

			const result = await updateMatch('match-1', payload as any);

			expect(mockedPut).toHaveBeenCalledWith('/matches/match-1', payload);
			expect(result).toEqual(responseData);
		});
	});

	describe('getWinStats', () => {
		it('calls GET /matches/win-stats and returns the response data', async () => {
			const responseData = { totalMatches: 12, totalWins: 5 };
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await getWinStats();

			expect(mockedGet).toHaveBeenCalledWith('/matches/win-stats');
			expect(result).toEqual(responseData);
		});
	});
});