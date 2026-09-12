import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { useUserSearch, useUpdateMe } from '@/hooks/use-user';
import { searchUser, updateMe } from '@/api/user';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('@/api/user');

const mockedSearchUser = searchUser as jest.MockedFunction<typeof searchUser>;
const mockedUpdateMe = updateMe as jest.MockedFunction<typeof updateMe>;

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