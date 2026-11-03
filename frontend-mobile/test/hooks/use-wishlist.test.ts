import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import {
	useAddToDefaultWishlist,
	useMyWishlists,
	useDefaultWishlist,
	useDefaultWishlistId,
	useWishlist,
	useWishlistItems,
	useWishlistMembers,
	useAddItemToWishlist,
	useRemoveItemFromWishlist,
	useAddMemberToWishlist,
	useRemoveMemberFromWishlist,
	useDeleteWishlist,
	useLeaveWishlist,
	useUpdateWishlist,
} from '@/hooks/use-wishlist';

import {
	addItemToWishlist,
	addMemberToWishlist,
	deleteWishlist,
	getMyDefaultWishlist,
	getWishlistById,
	leaveWishlist,
	listMyWishlists,
	listWishlistItems,
	listWishlistMembers,
	removeItemFromWishlist,
	removeMemberFromWishlist,
	updateWishlist,
} from '@/api/wishlist';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/wishlist');

const mockedAddItemToWishlist = addItemToWishlist as jest.MockedFunction<typeof addItemToWishlist>;
const mockedGetWishlistById = getWishlistById as jest.MockedFunction<typeof getWishlistById>;
const mockedListMyWishlists = listMyWishlists as jest.MockedFunction<typeof listMyWishlists>;
const mockedListWishlistItems = listWishlistItems as jest.MockedFunction<typeof listWishlistItems>;
const mockedListWishlistMembers = listWishlistMembers as jest.MockedFunction<typeof listWishlistMembers>;
const mockedAddMemberToWishlist = addMemberToWishlist as jest.MockedFunction<typeof addMemberToWishlist>;
const mockedRemoveItemFromWishlist = removeItemFromWishlist as jest.MockedFunction<typeof removeItemFromWishlist>;
const mockedRemoveMemberFromWishlist = removeMemberFromWishlist as jest.MockedFunction<typeof removeMemberFromWishlist>;
const mockedDeleteWishlist = deleteWishlist as jest.MockedFunction<typeof deleteWishlist>;
const mockedLeaveWishlist = leaveWishlist as jest.MockedFunction<typeof leaveWishlist>;
const mockedUpdateWishlist = updateWishlist as jest.MockedFunction<typeof updateWishlist>;
const mockedGetMyDefaultWishlist = jest.mocked(getMyDefaultWishlist);

describe('useMyWishlists', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the current user wishlists', async () => {
		mockedListMyWishlists.mockResolvedValueOnce({
			content: [{ id: 'w-1', isDefault: true } as any],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMyWishlists(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListMyWishlists).toHaveBeenCalled();
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});

	it('surfaces an error when the request fails', async () => {
		mockedListMyWishlists.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useMyWishlists(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
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

describe('useAddItemToWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('adds a game to the given wishlist', async () => {
		mockedAddItemToWishlist.mockResolvedValueOnce({ id: 'item-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddItemToWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('game-1');

		expect(mockedAddItemToWishlist).toHaveBeenCalledWith('w-1', 'game-1');
	});

	it('invalidates the items and preview-items queries on success', async () => {
		mockedAddItemToWishlist.mockResolvedValueOnce({ id: 'item-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useAddItemToWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('game-1');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'items', 'w-1'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'preview-items', 'w-1'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'item-status', 'w-1'] });
	});

	it('surfaces an error when the mutation fails', async () => {
		mockedAddItemToWishlist.mockRejectedValueOnce(new Error('Game already in wishlist'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddItemToWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		result.current.mutate('game-1');

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useRemoveItemFromWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('removes an item from the given wishlist', async () => {
		mockedRemoveItemFromWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useRemoveItemFromWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('item-1');

		expect(mockedRemoveItemFromWishlist).toHaveBeenCalledWith('w-1', 'item-1');
	});

	it('invalidates the items and preview-items queries on success', async () => {
		mockedRemoveItemFromWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useRemoveItemFromWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('item-1');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'items', 'w-1'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'preview-items', 'w-1'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'item-status', 'w-1'] });
	});
});

describe('useAddMemberToWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('adds a member to the given wishlist', async () => {
		mockedAddMemberToWishlist.mockResolvedValueOnce({ id: 'member-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAddMemberToWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('user-2');

		expect(mockedAddMemberToWishlist).toHaveBeenCalledWith('w-1', 'user-2');
	});

	it('invalidates the members query on success', async () => {
		mockedAddMemberToWishlist.mockResolvedValueOnce({ id: 'member-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useAddMemberToWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('user-2');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'members', 'w-1'] });
	});
});

describe('useRemoveMemberFromWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('removes a member from the given wishlist', async () => {
		mockedRemoveMemberFromWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useRemoveMemberFromWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('user-2');

		expect(mockedRemoveMemberFromWishlist).toHaveBeenCalledWith('w-1', 'user-2');
	});

	it('invalidates the members query on success', async () => {
		mockedRemoveMemberFromWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useRemoveMemberFromWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('user-2');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'members', 'w-1'] });
	});
});

describe('useUpdateWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('renames the given wishlist', async () => {
		mockedUpdateWishlist.mockResolvedValueOnce({ id: 'w-1', name: 'Nuovo nome' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUpdateWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('Nuovo nome');

		expect(mockedUpdateWishlist).toHaveBeenCalledWith('w-1', 'Nuovo nome');
	});

	it('invalidates the wishlists query on success', async () => {
		mockedUpdateWishlist.mockResolvedValueOnce({ id: 'w-1', name: 'Nuovo nome' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useUpdateWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('Nuovo nome');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists'] });
	});

	it('does not invalidate the wishlists query when the mutation fails', async () => {
		mockedUpdateWishlist.mockRejectedValueOnce(new Error('Forbidden'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useUpdateWishlist('w-1'), { wrapper: wrapperResult.wrapper });

		result.current.mutate('Nuovo nome');

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toEqual(new Error('Forbidden'));
		expect(invalidateSpy).not.toHaveBeenCalled();
	});
});

describe('useDefaultWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.resetAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('loads the default wishlist from the dedicated endpoint', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValueOnce({ id: 'w-2', name: 'La mia wishlist', isDefault: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.data?.id).toBe('w-2');
		expect(mockedListMyWishlists).not.toHaveBeenCalled();
	});

	it('is fetched only once and shared between components', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValue({ id: 'w-2' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result: first } = await renderHook(() => useDefaultWishlist(), { wrapper: wrapperResult.wrapper });
		await waitFor(() => expect(first.current.isSuccess).toBe(true));

		const { result: second } = await renderHook(() => useDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		expect(second.current.data?.id).toBe('w-2');
		expect(mockedGetMyDefaultWishlist).toHaveBeenCalledTimes(1);
	});

	it('surfaces an error when the default wishlist cannot be loaded', async () => {
		mockedGetMyDefaultWishlist.mockRejectedValueOnce(new Error('Default wishlist not found'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useDefaultWishlistId', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.resetAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('returns the id of the default wishlist', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValueOnce({ id: 'w-2' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistId(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current).toBe('w-2'));
	});

	it('returns null while loading', async () => {
		mockedGetMyDefaultWishlist.mockImplementation(() => new Promise(() => {}));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistId(), { wrapper: wrapperResult.wrapper });

		expect(result.current).toBeNull();
	});

	it('returns null when the default wishlist cannot be loaded', async () => {
		mockedGetMyDefaultWishlist.mockRejectedValueOnce(new Error('Not found'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistId(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(mockedGetMyDefaultWishlist).toHaveBeenCalled());
		expect(result.current).toBeNull();
	});
});

describe('useAddToDefaultWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.resetAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	const renderAdd = async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		return renderHook(() => useAddToDefaultWishlist(), { wrapper: wrapperResult.wrapper });
	}

	it('adds the game to the default wishlist', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValueOnce({ id: 'w-2' } as any);
		mockedAddItemToWishlist.mockResolvedValueOnce({ id: 'item-1' } as any);

		const { result } = await renderAdd();

		await result.current.mutateAsync('game-1');

		expect(mockedAddItemToWishlist).toHaveBeenCalledWith('w-2', 'game-1');
	});

	it('reuses the cached default wishlist', async () => {
		mockedAddItemToWishlist.mockResolvedValueOnce({ id: 'item-1' } as any);

		const { result } = await renderAdd();
		queryClient.setQueryData(['default-wishlist'], { id: 'cached' });

		await result.current.mutateAsync('game-1');

		expect(mockedGetMyDefaultWishlist).not.toHaveBeenCalled();
		expect(mockedAddItemToWishlist).toHaveBeenCalledWith('cached', 'game-1');
	});

	it('fails without adding anything when the default wishlist cannot be loaded', async () => {
		mockedGetMyDefaultWishlist.mockRejectedValueOnce(new Error('Default wishlist not found'));

		const { result } = await renderAdd();

		await expect(result.current.mutateAsync('game-1')).rejects.toThrow('Default wishlist not found');

		expect(mockedAddItemToWishlist).not.toHaveBeenCalled();
	});

	it('refreshes the wishlists and the users wishlists on success', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValueOnce({ id: 'w-1' } as any);
		mockedAddItemToWishlist.mockResolvedValueOnce({ id: 'item-1' } as any);

		const { result } = await renderAdd();
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		await result.current.mutateAsync('game-1');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'wishlist'] });
	});
});

describe.each([
	['useDeleteWishlist', () => useDeleteWishlist(), () => mockedDeleteWishlist],
	['useLeaveWishlist', () => useLeaveWishlist(), () => mockedLeaveWishlist],
] as const)('%s', (_, useHook, getApiMock) => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.resetAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	const seedCache = () => {
		queryClient.setQueryData(['wishlists', 'mine', 'all', 'default'], { pages: [], pageParams: [] });
		queryClient.setQueryData(['wishlists', 'detail', 'w-1'], { id: 'w-1' });
		queryClient.setQueryData(['wishlists', 'items', 'w-1'], { pages: [], pageParams: [] });
		queryClient.setQueryData(['wishlists', 'detail', 'w-2'], { id: 'w-2' });
	}

	const findQuery = (queryKey: unknown[]) => queryClient.getQueryCache().find({ queryKey, exact: true });

	it('calls the API with the wishlist id', async () => {
		(getApiMock() as jest.Mock).mockResolvedValueOnce(undefined);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(useHook, { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('w-1');

		expect(getApiMock()).toHaveBeenCalledWith('w-1');
	});

	it('removes the queries of that wishlist and refreshes the others', async () => {
		(getApiMock() as jest.Mock).mockResolvedValueOnce(undefined);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		seedCache();

		const { result } = await renderHook(useHook, { wrapper: wrapperResult.wrapper });

		await act(async () => {
			await result.current.mutateAsync('w-1');
		});

		expect(findQuery(['wishlists', 'detail', 'w-1'])).toBeUndefined();
		expect(findQuery(['wishlists', 'items', 'w-1'])).toBeUndefined();
		expect(findQuery(['wishlists', 'mine', 'all', 'default'])?.state.isInvalidated).toBe(true);
		expect(findQuery(['wishlists', 'detail', 'w-2'])?.state.isInvalidated).toBe(true);
	});

	it('leaves the cache untouched when the request fails', async () => {
		(getApiMock() as jest.Mock).mockRejectedValueOnce(new Error('Forbidden'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		seedCache();

		const { result } = await renderHook(useHook, { wrapper: wrapperResult.wrapper });

		await act(async () => {
			await expect(result.current.mutateAsync('w-1')).rejects.toThrow('Forbidden');
		});

		expect(findQuery(['wishlists', 'detail', 'w-1'])).toBeDefined();
		expect(findQuery(['wishlists', 'detail', 'w-2'])?.state.isInvalidated).toBe(false);
	});
});