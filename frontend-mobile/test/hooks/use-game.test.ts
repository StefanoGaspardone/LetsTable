import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useGameDetail, useHotGames, useGameSearch, useCollectionStatus, useDefaultWishlistStatus, useToggleCollection, useToggleDefaultWishlist } from '@/hooks/use-game';
import { useDefaultWishlistId } from '@/hooks/use-wishlist';

import { getGameByBggId, getHotGames, searchGame } from '@/api/game';
import { addToCollection, getGameStatusInCollection, removeFromCollection } from '@/api/collection';
import { addItemToWishlist, getItemStatusInWishlist, removeItemFromWishlist } from '@/api/wishlist';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/game');
jest.mock('@/api/collection');
jest.mock('@/api/wishlist');
jest.mock('@/hooks/use-wishlist');

const mockedGetGameByBggId = getGameByBggId as jest.MockedFunction<typeof getGameByBggId>;
const mockedGetHotGames = getHotGames as jest.MockedFunction<typeof getHotGames>;
const mockedSearchGame = searchGame as jest.MockedFunction<typeof searchGame>;
const mockedAddToCollection = addToCollection as jest.MockedFunction<typeof addToCollection>;
const mockedGetGameStatusInCollection = getGameStatusInCollection as jest.MockedFunction<typeof getGameStatusInCollection>;
const mockedRemoveFromCollection = removeFromCollection as jest.MockedFunction<typeof removeFromCollection>;
const mockedAddItemToWishlist = addItemToWishlist as jest.MockedFunction<typeof addItemToWishlist>;
const mockedGetItemStatusInWishlist = getItemStatusInWishlist as jest.MockedFunction<typeof getItemStatusInWishlist>;
const mockedRemoveItemFromWishlist = removeItemFromWishlist as jest.MockedFunction<typeof removeItemFromWishlist>;
const mockedUseDefaultWishlistId = useDefaultWishlistId as jest.MockedFunction<typeof useDefaultWishlistId>;

describe('useGameDetail', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the game detail by bggId', async () => {
		mockedGetGameByBggId.mockResolvedValueOnce({ id: 'game-1', name: 'Catan' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useGameDetail(123), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetGameByBggId).toHaveBeenCalledWith(123);
		expect(result.current.data?.name).toBe('Catan');
	});
});

describe('useHotGames', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the first page of hot games', async () => {
		mockedGetHotGames.mockResolvedValueOnce({ content: [{ id: '1' } as any], number: 0, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useHotGames(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetHotGames).toHaveBeenCalledWith(0);
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});
});

describe('useGameSearch', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('does not fetch when the query is empty', async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useGameSearch(''), { wrapper: wrapperResult.wrapper });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedSearchGame).not.toHaveBeenCalled();
	});

	it('fetches results when a query is provided', async () => {
		mockedSearchGame.mockResolvedValueOnce({ content: [{ id: '1' } as any], number: 0, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useGameSearch('catan'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedSearchGame).toHaveBeenCalledWith('catan', 0);
	});
});

describe('useCollectionStatus', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('does not fetch when gameId is undefined', async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCollectionStatus(undefined), { wrapper: wrapperResult.wrapper });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedGetGameStatusInCollection).not.toHaveBeenCalled();
	});

	it('fetches the collection status when gameId is provided', async () => {
		mockedGetGameStatusInCollection.mockResolvedValueOnce({ inCollection: true, itemId: 'item-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCollectionStatus('game-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetGameStatusInCollection).toHaveBeenCalledWith('game-1');
		expect(result.current.data?.inCollection).toBe(true);
	});
});

describe('useDefaultWishlistStatus', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('does not fetch when gameId is undefined', async () => {
		mockedUseDefaultWishlistId.mockReturnValue('wishlist-1');

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistStatus(undefined), { wrapper: wrapperResult.wrapper });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedGetItemStatusInWishlist).not.toHaveBeenCalled();
	});

	it('does not fetch when defaultWishlistId is null', async () => {
		mockedUseDefaultWishlistId.mockReturnValue(null);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistStatus('game-1'), { wrapper: wrapperResult.wrapper });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedGetItemStatusInWishlist).not.toHaveBeenCalled();
	});

	it('fetches the wishlist item status when both ids are available', async () => {
		mockedUseDefaultWishlistId.mockReturnValue('wishlist-1');
		mockedGetItemStatusInWishlist.mockResolvedValueOnce({ inWishlist: true, itemId: 'item-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useDefaultWishlistStatus('game-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetItemStatusInWishlist).toHaveBeenCalledWith('wishlist-1', 'game-1');
	});
});

describe('useToggleCollection', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls addToCollection when itemId is null', async () => {
		mockedAddToCollection.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useToggleCollection(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(mockedAddToCollection).toHaveBeenCalledWith('game-1');
		expect(mockedRemoveFromCollection).not.toHaveBeenCalled();
	});

	it('calls removeFromCollection when itemId is present', async () => {
		mockedRemoveFromCollection.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useToggleCollection(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: 'item-1' });

		expect(mockedRemoveFromCollection).toHaveBeenCalledWith('item-1');
		expect(mockedAddToCollection).not.toHaveBeenCalled();
	});

	it('invalidates the correct queries on success', async () => {
		mockedAddToCollection.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useToggleCollection(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['collection', 'status', 'game-1'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['collection'] });
	});
});

describe('useToggleDefaultWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('throws when there is no default wishlist', async () => {
		mockedUseDefaultWishlistId.mockReturnValue(null);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useToggleDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		result.current.mutate({ gameId: 'game-1', itemId: null });

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(mockedAddItemToWishlist).not.toHaveBeenCalled();
	});

	it('calls addItemToWishlist when itemId is null', async () => {
		mockedUseDefaultWishlistId.mockReturnValue('wishlist-1');
		mockedAddItemToWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useToggleDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(mockedAddItemToWishlist).toHaveBeenCalledWith('wishlist-1', 'game-1');
		expect(mockedRemoveItemFromWishlist).not.toHaveBeenCalled();
	});

	it('calls removeItemFromWishlist when itemId is present', async () => {
		mockedUseDefaultWishlistId.mockReturnValue('wishlist-1');
		mockedRemoveItemFromWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useToggleDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: 'item-1' });

		expect(mockedRemoveItemFromWishlist).toHaveBeenCalledWith('wishlist-1', 'item-1');
		expect(mockedAddItemToWishlist).not.toHaveBeenCalled();
	});

	it('invalidates the correct queries on success', async () => {
		mockedUseDefaultWishlistId.mockReturnValue('wishlist-1');
		mockedAddItemToWishlist.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useToggleDefaultWishlist(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'item-status', 'wishlist-1', 'game-1'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists'] });
	});
});