import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useGameDetail, useHotGames, useGameSearch, useCollectionStatus, useDefaultWishlistStatus, useToggleCollection, useToggleDefaultWishlist, useGameRules, useGameExpansions, useOverallGames } from '@/hooks/use-game';
import { useDefaultWishlistId } from '@/hooks/use-wishlist';

import { getGameByBggId, getGameExpansions, getHotGames, getOverallGames, searchGame } from '@/api/game';
import { addToCollection, getGameStatusInCollection, removeFromCollection } from '@/api/collection';
import { addItemToWishlist, getItemStatusInWishlist, getMyDefaultWishlist, removeItemFromWishlist } from '@/api/wishlist';
import { listGameRules } from '@/api/game-rules';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/game');
jest.mock('@/api/collection');
jest.mock('@/api/wishlist');
jest.mock('@/hooks/use-wishlist', () => ({
	...jest.requireActual('@/hooks/use-wishlist'),
	useDefaultWishlistId: jest.fn(),
}));
jest.mock('@/api/game-rules');

const mockedGetMyDefaultWishlist = jest.mocked(getMyDefaultWishlist);
const mockedGetOverallGames = jest.mocked(getOverallGames);
const mockedGetGameExpansions = jest.mocked(getGameExpansions);
const mockedListGameRules = jest.mocked(listGameRules);
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
		jest.resetAllMocks();
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

	it('refreshes every collection query and the stats on success', async () => {
		mockedAddToCollection.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useToggleCollection(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['collection'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['me', 'stats'] });
	});
});

describe('useToggleDefaultWishlist', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.resetAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	const renderToggle = async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		return renderHook(() => useToggleDefaultWishlist(), { wrapper: wrapperResult.wrapper });
	}

	it('adds the game to the default wishlist when itemId is null', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValueOnce({ id: 'wishlist-1' } as any);
		mockedAddItemToWishlist.mockResolvedValueOnce(undefined as any);

		const { result } = await renderToggle();

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(mockedAddItemToWishlist).toHaveBeenCalledWith('wishlist-1', 'game-1');
		expect(mockedRemoveItemFromWishlist).not.toHaveBeenCalled();
	});

	it('removes the item from the default wishlist when itemId is present', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValueOnce({ id: 'wishlist-1' } as any);
		mockedRemoveItemFromWishlist.mockResolvedValueOnce(undefined as any);

		const { result } = await renderToggle();

		await result.current.mutateAsync({ gameId: 'game-1', itemId: 'item-1' });

		expect(mockedRemoveItemFromWishlist).toHaveBeenCalledWith('wishlist-1', 'item-1');
		expect(mockedAddItemToWishlist).not.toHaveBeenCalled();
	});

	it('reuses the cached default wishlist instead of fetching it again', async () => {
		mockedAddItemToWishlist.mockResolvedValue(undefined as any);

		const { result } = await renderToggle();
		queryClient.setQueryData(['default-wishlist'], { id: 'cached-wishlist' });

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(mockedGetMyDefaultWishlist).not.toHaveBeenCalled();
		expect(mockedAddItemToWishlist).toHaveBeenCalledWith('cached-wishlist', 'game-1');
	});

	it('fails without touching the wishlist when the default wishlist cannot be loaded', async () => {
		mockedGetMyDefaultWishlist.mockRejectedValueOnce(new Error('Default wishlist not found'));

		const { result } = await renderToggle();

		await expect(result.current.mutateAsync({ gameId: 'game-1', itemId: null })).rejects.toThrow('Default wishlist not found');

		expect(mockedAddItemToWishlist).not.toHaveBeenCalled();
	});

	it('refreshes the wishlists and the users wishlists on success', async () => {
		mockedGetMyDefaultWishlist.mockResolvedValueOnce({ id: 'wishlist-1' } as any);
		mockedAddItemToWishlist.mockResolvedValueOnce(undefined as any);

		const { result } = await renderToggle();
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		await result.current.mutateAsync({ gameId: 'game-1', itemId: null });

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'wishlist'] });
	});
});

describe('useOverallGames', () => {
	it('fetches the first page of the overall ranking', async () => {
		jest.resetAllMocks();
		mockedGetOverallGames.mockResolvedValueOnce({ content: [{ id: '1' }], number: 0, last: true } as any);

		const { wrapper, queryClient } = createWrapper();

		const { result } = await renderHook(() => useOverallGames(), { wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetOverallGames).toHaveBeenCalledWith(0);
		queryClient.clear();
	});

	it('does not fetch when disabled', async () => {
		jest.resetAllMocks();
		const { wrapper, queryClient } = createWrapper();

		const { result } = await renderHook(() => useOverallGames(false), { wrapper });

		expect(result.current.fetchStatus).toBe('idle');
		queryClient.clear();
	});
});

describe('useGameExpansions', () => {
	beforeEach(() => {
		jest.resetAllMocks();
	});

	it('does not fetch without a bggId or when disabled', async () => {
		const { wrapper, queryClient } = createWrapper();

		const { result: withoutId } = await renderHook(() => useGameExpansions(null), { wrapper });
		const { result: disabled } = await renderHook(() => useGameExpansions(13, false), { wrapper });

		expect(withoutId.current.fetchStatus).toBe('idle');
		expect(disabled.current.fetchStatus).toBe('idle');
		expect(mockedGetGameExpansions).not.toHaveBeenCalled();
		queryClient.clear();
	});

	it('fetches the expansions of the game', async () => {
		mockedGetGameExpansions.mockResolvedValueOnce({ content: [{ id: 'exp-1' }], number: 0, last: true } as any);
		const { wrapper, queryClient } = createWrapper();

		const { result } = await renderHook(() => useGameExpansions(13), { wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetGameExpansions).toHaveBeenCalledWith(13, 0);
		queryClient.clear();
	});
});

describe('useGameRules', () => {
	beforeEach(() => {
		jest.resetAllMocks();
	});

	it('does not fetch without a game id or when disabled', async () => {
		const { wrapper, queryClient } = createWrapper();

		await renderHook(() => useGameRules(undefined), { wrapper });
		await renderHook(() => useGameRules('game-1', false), { wrapper });

		expect(mockedListGameRules).not.toHaveBeenCalled();
		queryClient.clear();
	});

	it('lists the rule files of the game', async () => {
		mockedListGameRules.mockResolvedValueOnce([{ id: 'file-1' }] as any);
		const { wrapper, queryClient } = createWrapper();

		const { result } = await renderHook(() => useGameRules('game-1'), { wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListGameRules).toHaveBeenCalledWith('game-1');
		queryClient.clear();
	});
});