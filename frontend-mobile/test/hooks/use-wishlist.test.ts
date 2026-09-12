import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import {
	useAddToDefaultWishlist,
	useMyWishlists,
	useDefaultWishlistId,
	useWishlist,
	useWishlistItems,
	useWishlistMembers,
} from '@/hooks/use-wishlist';
import {
	addItemToWishlist,
	getWishlistById,
	listMyWishlists,
	listWishlistItems,
	listWishlistMembers,
} from '@/api/wishlist';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/wishlist');

const mockedAddItemToWishlist = addItemToWishlist as jest.MockedFunction<typeof addItemToWishlist>;
const mockedGetWishlistById = getWishlistById as jest.MockedFunction<typeof getWishlistById>;
const mockedListMyWishlists = listMyWishlists as jest.MockedFunction<typeof listMyWishlists>;
const mockedListWishlistItems = listWishlistItems as jest.MockedFunction<typeof listWishlistItems>;
const mockedListWishlistMembers = listWishlistMembers as jest.MockedFunction<typeof listWishlistMembers>;

describe('useMyWishlists', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the current user wishlists', async () => {
		mockedListMyWishlists.mockResolvedValueOnce([{ id: 'w-1', isDefault: true } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMyWishlists(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListMyWishlists).toHaveBeenCalled();
		expect(result.current.data).toHaveLength(1);
	});

	it('surfaces an error when the request fails', async () => {
		mockedListMyWishlists.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMyWishlists(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useDefaultWishlistId', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('returns the id of the wishlist marked as default', async () => {
		mockedListMyWishlists.mockResolvedValueOnce([
			{ id: 'w-1', isDefault: false } as any,
			{ id: 'w-2', isDefault: true } as any,
		]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistId(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current).toBe('w-2'));
	});

	it('returns null when no wishlist is marked as default', async () => {
		mockedListMyWishlists.mockResolvedValueOnce([{ id: 'w-1', isDefault: false } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistId(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(mockedListMyWishlists).toHaveBeenCalled());

		expect(result.current).toBeNull();
	});

	it('returns null while the wishlists are still loading', async () => {
		mockedListMyWishlists.mockImplementation(() => new Promise(() => {}));

		const { wrapper } = createWrapper();

		const { result } = await renderHook(() => useDefaultWishlistId(), { wrapper });

		expect(result.current).toBeNull();
	});
});

describe('useWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the wishlist by id', async () => {
		mockedGetWishlistById.mockResolvedValueOnce({ id: 'w-1', name: 'My wishlist' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetWishlistById).toHaveBeenCalledWith('w-1');
		expect(result.current.data?.name).toBe('My wishlist');
	});

	it('surfaces an error when the request fails', async () => {
		mockedGetWishlistById.mockRejectedValueOnce(new Error('Not found'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useWishlistItems', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the first page of items', async () => {
		mockedListWishlistItems.mockResolvedValueOnce({
			content: [{ id: 'item-1' } as any],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useWishlistItems('w-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListWishlistItems).toHaveBeenCalledWith('w-1', 0);
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});

	it('calls fetchNextPage with the correct page number', async () => {
		mockedListWishlistItems
			.mockResolvedValueOnce({ content: [{ id: 'item-1' } as any], number: 0, last: false } as any)
			.mockResolvedValueOnce({ content: [{ id: 'item-2' } as any], number: 1, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useWishlistItems('w-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await act(async () => {
			await result.current.fetchNextPage();
		});

		expect(mockedListWishlistItems).toHaveBeenCalledTimes(2);
		expect(mockedListWishlistItems).toHaveBeenLastCalledWith('w-1', 1);
	});

	it('exposes hasNextPage as false on the last page', async () => {
		mockedListWishlistItems.mockResolvedValueOnce({ content: [], number: 0, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useWishlistItems('w-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.hasNextPage).toBe(false);
	});
});

describe('useWishlistMembers', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('does not fetch when enabled is false', async () => {
		const { wrapper } = createWrapper();

		const { result } = await renderHook(() => useWishlistMembers('w-1', false), { wrapper });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedListWishlistMembers).not.toHaveBeenCalled();
	});

	it('fetches members when enabled is true', async () => {
		mockedListWishlistMembers.mockResolvedValueOnce([{ id: 'member-1' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useWishlistMembers('w-1', true), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListWishlistMembers).toHaveBeenCalledWith('w-1');
		expect(result.current.data).toHaveLength(1);
	});
});

describe('useAddToDefaultWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('adds the game to the default wishlist', async () => {
		mockedListMyWishlists.mockResolvedValueOnce([
			{ id: 'w-1', isDefault: false } as any,
			{ id: 'w-2', isDefault: true } as any,
		]);
		mockedAddItemToWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddToDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('game-1');

		expect(mockedAddItemToWishlist).toHaveBeenCalledWith('w-2', 'game-1');
	});

	it('throws when no default wishlist exists', async () => {
		mockedListMyWishlists.mockResolvedValueOnce([{ id: 'w-1', isDefault: false } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddToDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		result.current.mutate('game-1');

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toEqual(new Error('Default wishlist not found'));
		expect(mockedAddItemToWishlist).not.toHaveBeenCalled();
	});

	it('invalidates the wishlists query on success', async () => {
		mockedListMyWishlists.mockResolvedValueOnce([{ id: 'w-1', isDefault: true } as any]);
		mockedAddItemToWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useAddToDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('game-1');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists'] });
	});
});