import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { MY_STATS_KEY, useMyStats } from '@/hooks/use-stat';
import { getMyStats } from '@/api/stats';
import { syncWidgets } from '@/widgets/widget-registry';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/stats', () => ({
	getMyStats: jest.fn(),
}));

jest.mock('@/widgets/widget-registry', () => ({
	syncWidgets: jest.fn(),
}));

const mockedGetMyStats = jest.mocked(getMyStats);
const mockedSyncWidgets = jest.mocked(syncWidgets);

const stats = { totalMatches: 12, totalWins: 5, totalGames: 8, totalFriends: 3 };

describe('useMyStats', () => {
	let queryClient: QueryClient;

	const renderStats = async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		return renderHook(() => useMyStats(), { wrapper: wrapperResult.wrapper });
	}

	beforeEach(() => {
		jest.resetAllMocks();
		mockedSyncWidgets.mockResolvedValue(undefined);
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('returns zero values while loading', async () => {
		mockedGetMyStats.mockImplementation(() => new Promise(() => {}));

		const { result } = await renderStats();

		expect(result.current).toEqual({ totalMatches: 0, totalWins: 0, totalGames: 0, totalFriends: 0, isLoading: true });
		expect(mockedSyncWidgets).not.toHaveBeenCalled();
	});

	it('returns the stats once loaded and caches them under the shared key', async () => {
		mockedGetMyStats.mockResolvedValueOnce(stats as any);

		const { result } = await renderStats();

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current).toEqual({ ...stats, isLoading: false });
		expect(mockedGetMyStats).toHaveBeenCalledTimes(1);
		expect(queryClient.getQueryData(MY_STATS_KEY)).toEqual(stats);
	});

	it('syncs the home screen widgets with the loaded stats', async () => {
		mockedGetMyStats.mockResolvedValueOnce(stats as any);

		await renderStats();

		await waitFor(() => expect(mockedSyncWidgets).toHaveBeenCalledWith(stats));
	});

	it('syncs the widgets again when the stats change', async () => {
		mockedGetMyStats.mockResolvedValueOnce(stats as any);

		await renderStats();

		await waitFor(() => expect(mockedSyncWidgets).toHaveBeenCalledTimes(1));

		const updated = { ...stats, totalWins: 6 };

		await act(async () => {
			queryClient.setQueryData(MY_STATS_KEY, updated);
		});

		await waitFor(() => expect(mockedSyncWidgets).toHaveBeenLastCalledWith(updated));
	});

	it('falls back to zero values and does not sync the widgets when the request fails', async () => {
		mockedGetMyStats.mockRejectedValueOnce(new Error('Network error'));

		const { result } = await renderStats();

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.totalMatches).toBe(0);
		expect(result.current.totalFriends).toBe(0);
		expect(mockedSyncWidgets).not.toHaveBeenCalled();
	});

	it('logs without throwing when syncing the widgets fails', async () => {
		const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
		mockedGetMyStats.mockResolvedValueOnce(stats as any);
		mockedSyncWidgets.mockRejectedValueOnce(new Error('Widget error'));

		const { result } = await renderStats();

		await waitFor(() => expect(consoleLog).toHaveBeenCalledWith('Failed to sync widgets:', expect.any(Error)));
		expect(result.current.totalMatches).toBe(12);
		consoleLog.mockRestore();
	});
});