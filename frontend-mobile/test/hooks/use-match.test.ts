import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useMatches, useMatchCalendar, useRecentGames, useMatchDetail, useMatchesByDay, useGameStats, useCreateMatch, useUpdateMatch, useDeleteMatch } from '@/hooks/use-match';
import { createMatch, deleteMatch, getCalendarMatch, getMatchById, getRecentGames, listGameStats, listMatches, updateMatch } from '@/api/match';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/match');
jest.mock('@/widgets/widget-registry', () => ({
	syncWidgets: jest.fn(() => Promise.resolve()),
}));

const mockedListMatches = jest.mocked(listMatches);
const mockedGetCalendarMatch = jest.mocked(getCalendarMatch);
const mockedGetRecentGames = jest.mocked(getRecentGames);
const mockedGetMatchById = jest.mocked(getMatchById);
const mockedListGameStats = jest.mocked(listGameStats);
const mockedCreateMatch = jest.mocked(createMatch);
const mockedUpdateMatch = jest.mocked(updateMatch);
const mockedDeleteMatch = jest.mocked(deleteMatch);

const page = (content: unknown[], number = 0, last = true) => ({ content, number, last }) as any;

let queryClient: QueryClient;

const setup = () => {
	const wrapperResult = createWrapper();
	queryClient = wrapperResult.queryClient;
	return wrapperResult.wrapper;
}

const isInvalidated = (queryKey: unknown[]) => queryClient.getQueryState(queryKey)?.isInvalidated ?? false;

beforeEach(() => {
	jest.resetAllMocks();
});

afterEach(() => {
	queryClient?.clear();
});

describe('useMatches', () => {
	it('fetches the first page with the given filters', async () => {
		mockedListMatches.mockResolvedValueOnce(page([{ id: '1' }]));

		const { result } = await renderHook(() => useMatches({ sort: 'playedAt-desc', size: 20 }), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListMatches).toHaveBeenCalledWith({ sort: 'playedAt-desc', size: 20, page: 0 });
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});

	it('passes the winner score sort through', async () => {
		mockedListMatches.mockResolvedValueOnce(page([]));

		const { result } = await renderHook(() => useMatches({ sort: 'winnerScore-desc', gameId: 'game-1' }), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListMatches).toHaveBeenCalledWith({ sort: 'winnerScore-desc', gameId: 'game-1', page: 0 });
	});

	it('does not fetch when disabled', async () => {
		const { result } = await renderHook(() => useMatches({}, false), { wrapper: setup() });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedListMatches).not.toHaveBeenCalled();
	});

	it('exposes hasNextPage based on the last flag', async () => {
		mockedListMatches.mockResolvedValueOnce(page([{ id: '1' }], 0, false));

		const { result } = await renderHook(() => useMatches({}), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.hasNextPage).toBe(true);
	});

	it('fetches the next page with the following page number', async () => {
		mockedListMatches.mockImplementation(async ({ page: pageNumber }) =>
			pageNumber === 0 ? page([{ id: '1' }], 0, false) : page([{ id: '2' }], 1, true)
		);

		const { result } = await renderHook(() => useMatches({}), { wrapper: setup() });

		await waitFor(() => expect(result.current.hasNextPage).toBe(true));

		await act(async () => {
			await result.current.fetchNextPage();
		});

		await waitFor(() => expect(result.current.data?.pages).toHaveLength(2));

		expect(mockedListMatches).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1 }));
		expect(result.current.hasNextPage).toBe(false);
	});

	it('keeps showing the previous results while the filters change', async () => {
		mockedListMatches
			.mockResolvedValueOnce(page([{ id: '1' }]))
			.mockImplementationOnce(() => new Promise(() => {}));

		const { result, rerender } = await renderHook(
			({ sort }: { sort: string }) => useMatches({ sort }),
			{ wrapper: setup(), initialProps: { sort: 'playedAt-desc' } }
		);

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await rerender({ sort: 'durationMinutes-desc' });

		expect(result.current.isPlaceholderData).toBe(true);
		expect(result.current.data?.pages[0].content).toEqual([{ id: '1' }]);
	});

	it('surfaces an error when the request fails', async () => {
		mockedListMatches.mockRejectedValueOnce(new Error('Network error'));

		const { result } = await renderHook(() => useMatches({}), { wrapper: setup() });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useMatchDetail', () => {
	it('does not fetch without a match id', async () => {
		const { result } = await renderHook(() => useMatchDetail(''), { wrapper: setup() });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedGetMatchById).not.toHaveBeenCalled();
	});

	it('fetches the match by id', async () => {
		mockedGetMatchById.mockResolvedValueOnce({ id: 'm-1' } as any);

		const { result } = await renderHook(() => useMatchDetail('m-1'), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetMatchById).toHaveBeenCalledWith('m-1');
	});
});

describe('useMatchCalendar', () => {
	it('fetches the calendar for the given year and month', async () => {
		mockedGetCalendarMatch.mockResolvedValueOnce([{ date: '2026-09-10', count: 2 }] as any);

		const { result } = await renderHook(() => useMatchCalendar(2026, 9), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetCalendarMatch).toHaveBeenCalledWith(2026, 9);
		expect(result.current.data).toHaveLength(1);
	});

	it('refetches when the month changes', async () => {
		mockedGetCalendarMatch
			.mockResolvedValueOnce([{ date: '2026-09-10', count: 1 }] as any)
			.mockResolvedValueOnce([{ date: '2026-10-05', count: 3 }] as any);

		const { result, rerender } = await renderHook(
			({ year, month }: { year: number; month: number }) => useMatchCalendar(year, month),
			{ wrapper: setup(), initialProps: { year: 2026, month: 9 } }
		);

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await rerender({ year: 2026, month: 10 });

		await waitFor(() => expect(mockedGetCalendarMatch).toHaveBeenLastCalledWith(2026, 10));
	});

	it('does not fetch when disabled', async () => {
		const { result } = await renderHook(() => useMatchCalendar(2026, 9, false), { wrapper: setup() });

		expect(result.current.fetchStatus).toBe('idle');
	});
});

describe('useMatchesByDay', () => {
	it('does not fetch without a date', async () => {
		const { result } = await renderHook(() => useMatchesByDay(null), { wrapper: setup() });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedListMatches).not.toHaveBeenCalled();
	});

	it('lists the matches of the given day, most recent first', async () => {
		mockedListMatches.mockResolvedValueOnce(page([{ id: 'm-1' }]));

		const { result } = await renderHook(() => useMatchesByDay('2026-11-03'), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListMatches).toHaveBeenCalledWith({ page: 0, size: 50, fromDate: '2026-11-03', toDate: '2026-11-03', sort: 'playedAt-desc' });
	});
});

describe('useGameStats', () => {
	it('fetches the first page with the given sort', async () => {
		mockedListGameStats.mockResolvedValueOnce(page([{ gameId: 'game-1' }]));

		const { result } = await renderHook(() => useGameStats('winRate-desc'), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListGameStats).toHaveBeenCalledWith({ page: 0, size: 20, sort: 'winRate-desc' });
	});

	it('does not fetch when disabled', async () => {
		const { result } = await renderHook(() => useGameStats('matchCount-desc', false), { wrapper: setup() });

		expect(result.current.fetchStatus).toBe('idle');
	});
});

describe('useRecentGames', () => {
	it('fetches the recent games list', async () => {
		mockedGetRecentGames.mockResolvedValueOnce([{ id: 'game-1' }] as any);

		const { result } = await renderHook(() => useRecentGames(), { wrapper: setup() });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.data).toHaveLength(1);
	});

	it('surfaces an error when the request fails', async () => {
		mockedGetRecentGames.mockRejectedValueOnce(new Error('Network error'));

		const { result } = await renderHook(() => useRecentGames(), { wrapper: setup() });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('match mutations', () => {
	const seedCache = () => {
		queryClient.setQueryData(['matches', { sort: 'playedAt-desc' }], { pages: [], pageParams: [] });
		queryClient.setQueryData(['matches', 'detail', 'm-1'], { id: 'm-1' });
		queryClient.setQueryData(['matches', 'detail', 'm-2'], { id: 'm-2' });
		queryClient.setQueryData(['users', 'matches', 'user-1'], []);
		queryClient.setQueryData(['collection', 'list'], []);
		queryClient.setQueryData(['me', 'stats'], { totalMatches: 1 });
	}

	it('useCreateMatch creates the match and refreshes matches, user matches, collection and stats', async () => {
		mockedCreateMatch.mockResolvedValueOnce({ id: 'm-3' } as any);
		const wrapper = setup();
		seedCache();

		const { result } = await renderHook(() => useCreateMatch(), { wrapper });

		await act(async () => {
			await result.current.mutateAsync({ gameId: 'game-1' } as any);
		});

		expect(mockedCreateMatch).toHaveBeenCalledWith({ gameId: 'game-1' });
		expect(isInvalidated(['matches', { sort: 'playedAt-desc' }])).toBe(true);
		expect(isInvalidated(['matches', 'detail', 'm-1'])).toBe(true);
		expect(isInvalidated(['users', 'matches', 'user-1'])).toBe(true);
		expect(isInvalidated(['collection', 'list'])).toBe(true);
		expect(isInvalidated(['me', 'stats'])).toBe(true);
	});

	it('useUpdateMatch stores the updated match in the detail cache and refreshes the lists', async () => {
		const updated = { id: 'm-1', notes: 'updated' };
		mockedUpdateMatch.mockResolvedValueOnce(updated as any);
		const wrapper = setup();
		seedCache();

		const { result } = await renderHook(() => useUpdateMatch(), { wrapper });

		await act(async () => {
			await result.current.mutateAsync({ matchId: 'm-1', payload: { notes: 'updated' } as any });
		});

		expect(mockedUpdateMatch).toHaveBeenCalledWith('m-1', { notes: 'updated' });
		expect(queryClient.getQueryData(['matches', 'detail', 'm-1'])).toEqual(updated);
		expect(isInvalidated(['matches', { sort: 'playedAt-desc' }])).toBe(true);
		expect(isInvalidated(['me', 'stats'])).toBe(true);
	});

	it('useDeleteMatch refreshes everything except the queries of the deleted match', async () => {
		mockedDeleteMatch.mockResolvedValueOnce(undefined);
		const wrapper = setup();
		seedCache();

		const { result } = await renderHook(() => useDeleteMatch(), { wrapper });

		await act(async () => {
			await result.current.mutateAsync('m-1');
		});

		expect(mockedDeleteMatch).toHaveBeenCalledWith('m-1');
		expect(isInvalidated(['matches', 'detail', 'm-1'])).toBe(false);
		expect(isInvalidated(['matches', 'detail', 'm-2'])).toBe(true);
		expect(isInvalidated(['matches', { sort: 'playedAt-desc' }])).toBe(true);
		expect(isInvalidated(['me', 'stats'])).toBe(true);
	});

	it('does not refresh anything when the mutation fails', async () => {
		mockedDeleteMatch.mockRejectedValueOnce(new Error('Forbidden'));
		const wrapper = setup();
		seedCache();

		const { result } = await renderHook(() => useDeleteMatch(), { wrapper });

		await act(async () => {
			await expect(result.current.mutateAsync('m-1')).rejects.toThrow('Forbidden');
		});

		expect(isInvalidated(['matches', 'detail', 'm-2'])).toBe(false);
		expect(isInvalidated(['me', 'stats'])).toBe(false);
	});
});