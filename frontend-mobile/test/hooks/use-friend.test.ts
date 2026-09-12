import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import {useFriends, usePendingReceived, usePendingSent, useSendFriendRequest, useAcceptFriendRequest, useRejectFriendRequest, useCancelFriendRequest, useRemoveFriend } from '@/hooks/use-friend';

import { listFriends, listPendingReceived, listPendingSent, sendFriendRequest, acceptFriendRequest, rejectFriendRequest, cancelFriendRequest, removeFriend } from '@/api/friend';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/friend');

const mockedListFriends = listFriends as jest.MockedFunction<typeof listFriends>;
const mockedListPendingReceived = listPendingReceived as jest.MockedFunction<typeof listPendingReceived>;
const mockedListPendingSent = listPendingSent as jest.MockedFunction<typeof listPendingSent>;
const mockedSendFriendRequest = sendFriendRequest as jest.MockedFunction<typeof sendFriendRequest>;
const mockedAcceptFriendRequest = acceptFriendRequest as jest.MockedFunction<typeof acceptFriendRequest>;
const mockedRejectFriendRequest = rejectFriendRequest as jest.MockedFunction<typeof rejectFriendRequest>;
const mockedCancelFriendRequest = cancelFriendRequest as jest.MockedFunction<typeof cancelFriendRequest>;
const mockedRemoveFriend = removeFriend as jest.MockedFunction<typeof removeFriend>;

describe('useFriends', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches the friends list', async () => {
		mockedListFriends.mockResolvedValueOnce([{ id: '1' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useFriends(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListFriends).toHaveBeenCalled();
		expect(result.current.data).toHaveLength(1);
	});

	it('surfaces an error when the request fails', async () => {
		mockedListFriends.mockRejectedValueOnce(new Error('Network error'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useFriends(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isError).toBe(true));
	});
});

describe('usePendingReceived', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches pending received requests', async () => {
		mockedListPendingReceived.mockResolvedValueOnce([{ id: 'req-1' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => usePendingReceived(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListPendingReceived).toHaveBeenCalled();
		expect(result.current.data).toHaveLength(1);
	});
});

describe('usePendingSent', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('fetches pending sent requests', async () => {
		mockedListPendingSent.mockResolvedValueOnce([{ id: 'req-2' } as any]);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => usePendingSent(), { wrapper: wrapperResult.wrapper });

		await waitFor(() => expect(result.current.isSuccess).toBe(true));

		expect(mockedListPendingSent).toHaveBeenCalled();
		expect(result.current.data).toHaveLength(1);
	});
});

describe('useSendFriendRequest', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls sendFriendRequest with the given receiverId', async () => {
		mockedSendFriendRequest.mockResolvedValueOnce({ id: 'req-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useSendFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('user-123');

		expect(mockedSendFriendRequest).toHaveBeenCalledWith('user-123');
	});

	it('invalidates friends and users search queries on success', async () => {
		mockedSendFriendRequest.mockResolvedValueOnce({ id: 'req-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useSendFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('user-123');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['friends'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['users', 'search'] });
	});

	it('surfaces an error when the mutation fails', async () => {
		mockedSendFriendRequest.mockRejectedValueOnce(new Error('Already friends'));

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useSendFriendRequest(), { wrapper: wrapperResult.wrapper });

		result.current.mutate('user-123');

		await waitFor(() => expect(result.current.isError).toBe(true));

		expect(result.current.error).toEqual(new Error('Already friends'));
	});
});

describe('useAcceptFriendRequest', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls acceptFriendRequest with the given requestId', async () => {
		mockedAcceptFriendRequest.mockResolvedValueOnce({ id: 'req-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useAcceptFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('req-1');

		expect(mockedAcceptFriendRequest).toHaveBeenCalledWith('req-1');
	});

	it('invalidates the friends queries on success', async () => {
		mockedAcceptFriendRequest.mockResolvedValueOnce({ id: 'req-1' } as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useAcceptFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('req-1');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['friends'] });
	});
});

describe('useRejectFriendRequest', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls rejectFriendRequest with the given requestId', async () => {
		mockedRejectFriendRequest.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useRejectFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('req-2');

		expect(mockedRejectFriendRequest).toHaveBeenCalledWith('req-2');
	});

	it('invalidates the friends queries on success', async () => {
		mockedRejectFriendRequest.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useRejectFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('req-2');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['friends'] });
	});
});

describe('useCancelFriendRequest', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls cancelFriendRequest with the given requestId', async () => {
		mockedCancelFriendRequest.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useCancelFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('req-3');

		expect(mockedCancelFriendRequest).toHaveBeenCalledWith('req-3');
	});

	it('invalidates the friends queries on success', async () => {
		mockedCancelFriendRequest.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useCancelFriendRequest(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('req-3');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['friends'] });
	});
});

describe('useRemoveFriend', () => {
	let queryClient: QueryClient;

	beforeEach(() => {
		jest.clearAllMocks();
	});

	afterEach(() => {
		queryClient?.clear();
	});

	it('calls removeFriend with the given friendUserId', async () => {
		mockedRemoveFriend.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => useRemoveFriend(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('friend-1');

		expect(mockedRemoveFriend).toHaveBeenCalledWith('friend-1');
	});

	it('invalidates the friends queries on success', async () => {
		mockedRemoveFriend.mockResolvedValueOnce(undefined as any);

		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => useRemoveFriend(), { wrapper: wrapperResult.wrapper });

		await result.current.mutateAsync('friend-1');

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['friends'] });
	});
});