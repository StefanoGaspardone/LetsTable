import { apiClient } from '@/api/client';
import { getMyStats } from '@/api/stats';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
	},
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;

describe('stats API', () => {
	beforeEach(() => {
		mockedGet.mockReset();
	});

	it('fetches my stats', async () => {
		const stats = { totalMatches: 12, totalWins: 5 } as any;
		mockedGet.mockResolvedValue({ data: stats });

		await expect(getMyStats()).resolves.toEqual(stats);
		expect(mockedGet).toHaveBeenCalledWith('/users/me/stats');
	});

	it('propagates API errors', async () => {
		mockedGet.mockRejectedValue(new Error('Unauthorized'));

		await expect(getMyStats()).rejects.toThrow('Unauthorized');
	});
});