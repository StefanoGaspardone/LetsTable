import AsyncStorage from '@react-native-async-storage/async-storage';

import { fetchWidgetStats, loadWidgetStats, saveWidgetStats } from '@/widgets/widget-stats';
import { getMyStats } from '@/api/stats';
import { tokenStorage } from '@/lib/token-storage';

jest.mock('@/api/stats', () => ({
	getMyStats: jest.fn(),
}));

jest.mock('@/lib/token-storage', () => ({
	tokenStorage: {
		getAccessToken: jest.fn(),
	},
}));

const mockedGetMyStats = jest.mocked(getMyStats);
const mockedGetAccessToken = jest.mocked(tokenStorage.getAccessToken);

const STORAGE_KEY = '@widget_stats';
const stats = { totalMatches: 12, totalWins: 5, totalGames: 8, totalFriends: 3 };

describe('widget-stats', () => {
	beforeEach(async () => {
		jest.clearAllMocks();
		mockedGetMyStats.mockReset();
		mockedGetAccessToken.mockReset();
		await AsyncStorage.clear();
	});

	describe('loadWidgetStats', () => {
		it('returns null when nothing is stored', async () => {
			await expect(loadWidgetStats()).resolves.toBeNull();
		});

		it('returns the stored snapshot', async () => {
			await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(stats));

			await expect(loadWidgetStats()).resolves.toEqual(stats);
		});

		it('returns null when the stored value is corrupted', async () => {
			await AsyncStorage.setItem(STORAGE_KEY, '{not-json');

			await expect(loadWidgetStats()).resolves.toBeNull();
		});

		it('returns null when reading from storage fails', async () => {
			jest.spyOn(AsyncStorage, 'getItem').mockRejectedValueOnce(new Error('Storage error'));

			await expect(loadWidgetStats()).resolves.toBeNull();
		});
	});

	describe('saveWidgetStats', () => {
		it('stores the snapshot as JSON', async () => {
			await saveWidgetStats(stats as any);

			expect(JSON.parse((await AsyncStorage.getItem(STORAGE_KEY))!)).toEqual(stats);
		});

		it('removes the snapshot when the user is logged out', async () => {
			await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify(stats));

			await saveWidgetStats(null);

			await expect(AsyncStorage.getItem(STORAGE_KEY)).resolves.toBeNull();
		});
	});

	describe('fetchWidgetStats', () => {
		it('returns null without calling the API when there is no access token', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			await expect(fetchWidgetStats()).resolves.toBeNull();

			expect(mockedGetMyStats).not.toHaveBeenCalled();
		});

		it('returns the fresh stats when logged in', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('token');
			mockedGetMyStats.mockResolvedValueOnce(stats as any);

			await expect(fetchWidgetStats()).resolves.toEqual(stats);
		});

		it('returns undefined when the request fails, so the cached snapshot is kept', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('token');
			mockedGetMyStats.mockRejectedValueOnce(new Error('Network error'));

			await expect(fetchWidgetStats()).resolves.toBeUndefined();
		});
	});
});