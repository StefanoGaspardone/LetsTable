import { renderHook } from '@testing-library/react-native';
import { useFocusEffect } from 'expo-router';

import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('expo-router', () => ({
	useFocusEffect: jest.fn(),
}));

const mockedUseFocusEffect = jest.mocked(useFocusEffect);

const lastFocusCallback = () => mockedUseFocusEffect.mock.calls.at(-1)?.[0] as () => void;

describe('useRefetchOnFocus', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('registers a focus effect callback', async () => {
		const { wrapper } = createWrapper();

		await renderHook(() => useRefetchOnFocus(['friends']), { wrapper });

		expect(mockedUseFocusEffect).toHaveBeenCalledWith(expect.any(Function));
	});

	it('refetches only active and stale queries for the given key when the screen gains focus', async () => {
		const { wrapper, queryClient } = createWrapper();
		const refetchSpy = jest.spyOn(queryClient, 'refetchQueries');

		await renderHook(() => useRefetchOnFocus(['friends']), { wrapper });

		lastFocusCallback()();

		expect(refetchSpy).toHaveBeenCalledWith({ queryKey: ['friends'], type: 'active', stale: true });
	});

	it('uses the exact query key provided, including nested segments', async () => {
		const { wrapper, queryClient } = createWrapper();
		const refetchSpy = jest.spyOn(queryClient, 'refetchQueries');

		await renderHook(() => useRefetchOnFocus(['matches', 'detail', 'match-123']), { wrapper });

		lastFocusCallback()();

		expect(refetchSpy).toHaveBeenCalledWith({ queryKey: ['matches', 'detail', 'match-123'], type: 'active', stale: true });
	});

	it('does not invalidate queries', async () => {
		const { wrapper, queryClient } = createWrapper();
		const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

		await renderHook(() => useRefetchOnFocus(['friends']), { wrapper });

		lastFocusCallback()();

		expect(invalidateSpy).not.toHaveBeenCalled();
	});

	it('keeps the same callback when the query key is equal by value', async () => {
		const { wrapper } = createWrapper();

		const { rerender } = await renderHook(
			({ queryKey }: { queryKey: string[] }) => useRefetchOnFocus(queryKey),
			{ wrapper, initialProps: { queryKey: ['friends'] } }
		);

		const firstCallback = lastFocusCallback();

		await rerender({ queryKey: ['friends'] });

		expect(lastFocusCallback()).toBe(firstCallback);
	});

	it('creates a new callback that refetches the new key when the query key changes', async () => {
		const { wrapper, queryClient } = createWrapper();
		const refetchSpy = jest.spyOn(queryClient, 'refetchQueries');

		const { rerender } = await renderHook(
			({ queryKey }: { queryKey: string[] }) => useRefetchOnFocus(queryKey),
			{ wrapper, initialProps: { queryKey: ['collection', 'status', 'game-1'] } }
		);

		const firstCallback = lastFocusCallback();

		await rerender({ queryKey: ['collection', 'status', 'game-2'] });

		const secondCallback = lastFocusCallback();
		expect(secondCallback).not.toBe(firstCallback);

		secondCallback();

		expect(refetchSpy).toHaveBeenCalledWith({ queryKey: ['collection', 'status', 'game-2'], type: 'active', stale: true });
	});
});