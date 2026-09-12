import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useCollection, useAddToCollection } from '@/hooks/use-collection';
import { listCollection, addToCollection } from '@/api/collection';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/collection');

const mockedListCollection = listCollection as jest.MockedFunction<typeof listCollection>;
const mockedAddToCollection = addToCollection as jest.MockedFunction<typeof addToCollection>;

describe('useCollection', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the first page with the given filters', async () => {
		mockedListCollection.mockResolvedValueOnce({
			content: [{ id: '1' } as any],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCollection('Catan', true, false), {
			wrapper: wrapperResult.wrapper,
		});

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListCollection).toHaveBeenCalledWith({
			page: 0,
			gameName: 'Catan',
			played: true,
			isExpansion: false,
		});
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});

	it('exposes hasNextPage as false when the last page is returned', async () => {
		mockedListCollection.mockResolvedValueOnce({
			content: [],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCollection(''), {
			wrapper: wrapperResult.wrapper,
		});

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.hasNextPage).toBe(false);
	});

	it('exposes hasNextPage as true and computes the next page number when not on the last page', async () => {
		mockedListCollection.mockResolvedValueOnce({
			content: [{ id: '1' } as any],
			number: 2,
			last: false,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCollection(''), {
			wrapper: wrapperResult.wrapper,
		});

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.hasNextPage).toBe(true);
	});

	it('calls fetchNextPage with the correct page number', async () => {
		mockedListCollection
			.mockResolvedValueOnce({ content: [{ id: '1' } as any], number: 0, last: false } as any)
			.mockResolvedValueOnce({ content: [{ id: '2' } as any], number: 1, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCollection(''), {
			wrapper: wrapperResult.wrapper,
		});

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await act(async () => {
			await result.current.fetchNextPage();
		});

		expect(mockedListCollection).toHaveBeenCalledTimes(2);
		expect(mockedListCollection).toHaveBeenLastCalledWith(
			expect.objectContaining({ page: 1 })
		);
	});

	it('surfaces an error when the request fails', async () => {
		mockedListCollection.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCollection(''), {
			wrapper: wrapperResult.wrapper,
		});

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toEqual(new Error('Network error'));
	});
});

describe('useAddToCollection', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls addToCollection with the given gameId', async () => {
		mockedAddToCollection.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddToCollection(), {
			wrapper: wrapperResult.wrapper,
		});

		await result.current.mutateAsync('game-123');

		expect(mockedAddToCollection).toHaveBeenCalledWith('game-123');
	});

	it('sets isSuccess to true after a successful mutation', async () => {
		mockedAddToCollection.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddToCollection(), {
			wrapper: wrapperResult.wrapper,
		});

		result.current.mutate('game-123');

		await waitFor(() => expect(result.current.isSuccess).toBe(true));
	});

	it('surfaces an error when the mutation fails', async () => {
		mockedAddToCollection.mockRejectedValueOnce(new Error('Add failed'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddToCollection(), {
			wrapper: wrapperResult.wrapper,
		});

		result.current.mutate('game-123');

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toEqual(new Error('Add failed'));
	});
});