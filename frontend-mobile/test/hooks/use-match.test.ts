import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useMatches, useMatchCalendar, useRecentGames } from '@/hooks/use-match';
import { getCalendarMatch, listMatches } from '@/api/match';
import { getRecentGames } from '@/api/game';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/match');
jest.mock('@/api/game');

const mockedListMatches = listMatches as jest.MockedFunction<typeof listMatches>;
const mockedGetCalendarMatch = getCalendarMatch as jest.MockedFunction<typeof getCalendarMatch>;
const mockedGetRecentGames = getRecentGames as jest.MockedFunction<typeof getRecentGames>;

describe('useMatches', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the first page with the given filters', async () => {
		mockedListMatches.mockResolvedValueOnce({
			content: [{ id: '1' } as any],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMatches({ sort: 'playedAt-desc', size: 20 }), {
			wrapper: wrapperResult.wrapper,
		});

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListMatches).toHaveBeenCalledWith({
			sort: 'playedAt-desc',
			size: 20,
			page: 0,
		});
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});

	it('exposes hasNextPage as false when the last page is returned', async () => {
		mockedListMatches.mockResolvedValueOnce({
			content: [],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMatches({}), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.hasNextPage).toBe(false);
	});

	it('computes the next page number when not on the last page', async () => {
		mockedListMatches.mockResolvedValueOnce({
			content: [{ id: '1' } as any],
			number: 1,
			last: false,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMatches({}), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.hasNextPage).toBe(true);
	});

	it('calls fetchNextPage with the correct page number', async () => {
		mockedListMatches
			.mockResolvedValueOnce({ content: [{ id: '1' } as any], number: 0, last: false } as any)
			.mockResolvedValueOnce({ content: [{ id: '2' } as any], number: 1, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMatches({}), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await act(async () => {
			await result.current.fetchNextPage();
		});

		expect(mockedListMatches).toHaveBeenCalledTimes(2);
		expect(mockedListMatches).toHaveBeenLastCalledWith(
			expect.objectContaining({ page: 1 })
		);
	});

	it('surfaces an error when the request fails', async () => {
		mockedListMatches.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMatches({}), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useMatchCalendar', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the calendar for the given year and month', async () => {
		mockedGetCalendarMatch.mockResolvedValueOnce([{ date: '2026-09-10', count: 2 } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMatchCalendar(2026, 9), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetCalendarMatch).toHaveBeenCalledWith(2026, 9);
		expect(result.current.data).toHaveLength(1);
	});

	it('refetches when the year or month changes', async () => {
		mockedGetCalendarMatch
			.mockResolvedValueOnce([{ date: '2026-09-10', count: 1 } as any])
			.mockResolvedValueOnce([{ date: '2026-10-05', count: 3 } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result, rerender } = await renderHook(
            ({ year, month }: { year: number; month: number }) => useMatchCalendar(year, month),
            { wrapper: wrapperResult.wrapper, initialProps: { year: 2026, month: 9 } }
        );

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await rerender({ year: 2026, month: 10 });

		await waitFor(() => expect(mockedGetCalendarMatch).toHaveBeenCalledTimes(2));

		expect(mockedGetCalendarMatch).toHaveBeenLastCalledWith(2026, 10);
	});

	it('surfaces an error when the request fails', async () => {
		mockedGetCalendarMatch.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMatchCalendar(2026, 9), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useRecentGames', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the recent games list', async () => {
		mockedGetRecentGames.mockResolvedValueOnce([{ id: 'game-1' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useRecentGames(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetRecentGames).toHaveBeenCalled();
		expect(result.current.data).toHaveLength(1);
	});

	it('surfaces an error when the request fails', async () => {
		mockedGetRecentGames.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useRecentGames(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});