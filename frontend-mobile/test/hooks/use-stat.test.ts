import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useHomeStats } from '@/hooks/use-stat';
import { listCollection } from '@/api/collection';
import { getWinStats } from '@/api/match';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/collection');
jest.mock('@/api/match');

const mockedListCollection = listCollection as jest.MockedFunction<typeof listCollection>;
const mockedGetWinStats = getWinStats as jest.MockedFunction<typeof getWinStats>;

describe('useHomeStats', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('returns default zero values while loading', async () => {
		mockedGetWinStats.mockImplementation(() => new Promise(() => {}));
		mockedListCollection.mockImplementation(() => new Promise(() => {}));

		const { wrapper } = createWrapper();

		const { result } = await renderHook(() => useHomeStats(), { wrapper });

		expect(result.current.totalMatches).toBe(0);
		expect(result.current.totalWins).toBe(0);
		expect(result.current.totalGames).toBe(0);
		expect(result.current.isLoading).toBe(true);
	});

	it('returns the combined stats once both queries resolve', async () => {
		mockedGetWinStats.mockResolvedValueOnce({ totalMatches: 12, totalWins: 5 } as any);
		mockedListCollection.mockResolvedValueOnce({ totalElements: 8 } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useHomeStats(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.totalMatches).toBe(12);
		expect(result.current.totalWins).toBe(5);
		expect(result.current.totalGames).toBe(8);
	});

	it('calls listCollection with page 0 and size 1', async () => {
		mockedGetWinStats.mockResolvedValueOnce({ totalMatches: 0, totalWins: 0 } as any);
		mockedListCollection.mockResolvedValueOnce({ totalElements: 0 } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		await renderHook(() => useHomeStats(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(mockedListCollection).toHaveBeenCalledWith({ page: 0, size: 1 }));
	});

	it('remains loading if only one of the two queries has resolved', async () => {
		mockedGetWinStats.mockResolvedValueOnce({ totalMatches: 3, totalWins: 1 } as any);
		mockedListCollection.mockImplementation(() => new Promise(() => {}));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useHomeStats(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.totalMatches).toBe(3));

		expect(result.current.isLoading).toBe(true);
		expect(result.current.totalGames).toBe(0);
	});

	it('falls back to zero when winStats data is undefined after an error', async () => {
		mockedGetWinStats.mockRejectedValueOnce(new Error('Network error'));
		mockedListCollection.mockResolvedValueOnce({ totalElements: 4 } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useHomeStats(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isLoading).toBe(false));

		expect(result.current.totalMatches).toBe(0);
		expect(result.current.totalWins).toBe(0);
		expect(result.current.totalGames).toBe(4);
	});
});