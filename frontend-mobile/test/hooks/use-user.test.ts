import { renderHook, waitFor, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useUserSearch, useUpdateMe, useUserMatches, useUserFriends, useUserDefaultWishlistItems } from '@/hooks/use-user';
import { searchUser, updateMe, getUserMatches, getUserFriends, getUserDefaultWishlistItems } from '@/api/user';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/user');

const mockedSearchUser = searchUser as jest.MockedFunction<typeof searchUser>;
const mockedUpdateMe = updateMe as jest.MockedFunction<typeof updateMe>;
const mockedGetUserMatches = getUserMatches as jest.MockedFunction<typeof getUserMatches>;
const mockedGetUserFriends = getUserFriends as jest.MockedFunction<typeof getUserFriends>;
const mockedGetUserDefaultWishlistItems = getUserDefaultWishlistItems as jest.MockedFunction<typeof getUserDefaultWishlistItems>;

describe('useUserSearch', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('does not fetch when the query is empty', async () => {
		const { wrapper } = createWrapper();

		const { result } = await renderHook(() => useUserSearch(''), { wrapper });

		expect(result.current.fetchStatus).toBe('idle');
		expect(mockedSearchUser).not.toHaveBeenCalled();
	});

	it('fetches results when a query is provided', async () => {
		mockedSearchUser.mockResolvedValueOnce([{ id: 'user-1', username: 'marco' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserSearch('marco'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedSearchUser).toHaveBeenCalledWith('marco');
		expect(result.current.data).toHaveLength(1);
	});

	it('surfaces an error when the request fails', async () => {
		mockedSearchUser.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserSearch('marco'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});

	it('refetches when the query string changes', async () => {
		mockedSearchUser
			.mockResolvedValueOnce([{ id: 'user-1', username: 'marco' } as any])
			.mockResolvedValueOnce([{ id: 'user-2', username: 'anna' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result, rerender } = await renderHook(
			({ query }: { query: string }) => useUserSearch(query),
			{ wrapper: wrapperResult.wrapper, initialProps: { query: 'marco' } }
		);

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await rerender({ query: 'anna' });

		await waitFor(() => expect(mockedSearchUser).toHaveBeenCalledTimes(2));

		expect(mockedSearchUser).toHaveBeenLastCalledWith('anna');
	});
});

describe('useUpdateMe', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls updateMe with the given payload', async () => {
		mockedUpdateMe.mockResolvedValueOnce({ id: 'user-1', username: 'newname' } as any);
		const onUserUpdated = jest.fn();

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUpdateMe(onUserUpdated), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ username: 'newname' });

		expect(mockedUpdateMe).toHaveBeenCalledWith({ username: 'newname' });
	});

	it('calls onUserUpdated with the updated user on success', async () => {
		const updatedUser = { id: 'user-1', username: 'newname' } as any;
		mockedUpdateMe.mockResolvedValueOnce(updatedUser);
		const onUserUpdated = jest.fn();

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUpdateMe(onUserUpdated), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync({ username: 'newname' });

		expect(onUserUpdated).toHaveBeenCalledWith(updatedUser);
	});

	it('does not call onUserUpdated when the mutation fails', async () => {
		mockedUpdateMe.mockRejectedValueOnce(new Error('Update failed'));
		const onUserUpdated = jest.fn();

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUpdateMe(onUserUpdated), { wrapper: wrapperResult.wrapper });

		result.current.mutate({ username: 'newname' });

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(onUserUpdated).not.toHaveBeenCalled();
	});

	it('surfaces an error when the mutation fails', async () => {
		mockedUpdateMe.mockRejectedValueOnce(new Error('Update failed'));
		const onUserUpdated = jest.fn();

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUpdateMe(onUserUpdated), { wrapper: wrapperResult.wrapper });

		result.current.mutate({ username: 'newname' });

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toEqual(new Error('Update failed'));
	});
});


describe('useUserMatches', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the first page of a user\'s matches', async () => {
		mockedGetUserMatches.mockResolvedValueOnce({
			content: [{ id: 'match-1' } as any],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserMatches('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetUserMatches).toHaveBeenCalledWith('user-1', 0, 20, undefined);
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});

	it('passes the sort parameter through to the API call', async () => {
		mockedGetUserMatches.mockResolvedValueOnce({ content: [], number: 0, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		await renderHook(() => useUserMatches('user-1', 'playedAt-desc'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(mockedGetUserMatches).toHaveBeenCalledWith('user-1', 0, 20, 'playedAt-desc'));
	});

	it('fetches the next page with an incremented page number', async () => {
		mockedGetUserMatches
			.mockResolvedValueOnce({ content: [{ id: 'match-1' } as any], number: 0, last: false } as any)
			.mockResolvedValueOnce({ content: [{ id: 'match-2' } as any], number: 1, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserMatches('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await act(async () => {
			await result.current.fetchNextPage();
		});

		expect(mockedGetUserMatches).toHaveBeenCalledTimes(2);
		expect(mockedGetUserMatches).toHaveBeenLastCalledWith('user-1', 1, 20, undefined);
	});

	it('surfaces an error when the request fails', async () => {
		mockedGetUserMatches.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserMatches('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useUserFriends', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the friends of the given user', async () => {
		mockedGetUserFriends.mockResolvedValueOnce([{ id: 'friend-1', username: 'anna' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserFriends('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetUserFriends).toHaveBeenCalledWith('user-1');
		expect(result.current.data).toHaveLength(1);
	});

	it('surfaces an error when the request fails', async () => {
		mockedGetUserFriends.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserFriends('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('useUserDefaultWishlistItems', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the first page of the default wishlist items', async () => {
		mockedGetUserDefaultWishlistItems.mockResolvedValueOnce({
			content: [{ id: 'item-1' } as any],
			number: 0,
			last: true,
		} as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserDefaultWishlistItems('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedGetUserDefaultWishlistItems).toHaveBeenCalledWith('user-1', 0);
		expect(result.current.data?.pages[0].content).toHaveLength(1);
	});

	it('fetches the next page with an incremented page number', async () => {
		mockedGetUserDefaultWishlistItems
			.mockResolvedValueOnce({ content: [{ id: 'item-1' } as any], number: 0, last: false } as any)
			.mockResolvedValueOnce({ content: [{ id: 'item-2' } as any], number: 1, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserDefaultWishlistItems('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		await act(async () => {
			await result.current.fetchNextPage();
		});

		expect(mockedGetUserDefaultWishlistItems).toHaveBeenCalledTimes(2);
		expect(mockedGetUserDefaultWishlistItems).toHaveBeenLastCalledWith('user-1', 1);
	});

	it('exposes hasNextPage as false on the last page', async () => {
		mockedGetUserDefaultWishlistItems.mockResolvedValueOnce({ content: [], number: 0, last: true } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserDefaultWishlistItems('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(result.current.hasNextPage).toBe(false);
	});

	it('surfaces an error when the request fails', async () => {
		mockedGetUserDefaultWishlistItems.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useUserDefaultWishlistItems('user-1'), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});