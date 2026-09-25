import { renderHook, act } from '@testing-library/react-native';
import { QueryClient } from '@tanstack/react-query';

import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';

import { createWrapper } from '@/test/helpers/test-utils';

describe('usePullToRefresh', () => {
	let queryClient: QueryClient;

	afterEach(() => {
		queryClient?.clear();
	});

	it('starts with refreshing set to false', async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;

		const { result } = await renderHook(() => usePullToRefresh([['matches']]), { wrapper: wrapperResult.wrapper });

		expect(result.current.refreshing).toBe(false);
	});

	it('sets refreshing to true during the refresh and back to false when done', async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => usePullToRefresh([['matches']]), { wrapper: wrapperResult.wrapper });

		await act(async () => {
			await result.current.onRefresh();
		});

		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['matches'] });
		expect(result.current.refreshing).toBe(false);
	});

	it('invalidates all provided query keys', async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(
			() => usePullToRefresh([['matches'], ['wishlists', 'mine'], ['friends']]),
			{ wrapper: wrapperResult.wrapper }
		);

		await act(async () => {
			await result.current.onRefresh();
		});

		expect(invalidateSpy).toHaveBeenCalledTimes(3);
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['matches'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['wishlists', 'mine'] });
		expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['friends'] });
	});

	it('handles an empty list of query keys without error', async () => {
		const wrapperResult = createWrapper();
		queryClient = wrapperResult.queryClient;
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		const { result } = await renderHook(() => usePullToRefresh([]), { wrapper: wrapperResult.wrapper });

		await act(async () => {
			await result.current.onRefresh();
		});

		expect(invalidateSpy).not.toHaveBeenCalled();
		expect(result.current.refreshing).toBe(false);
	});
});