import { renderHook } from '@testing-library/react-native';
import { useFocusEffect } from 'expo-router';

import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';

import { createWrapper } from '@/test/helpers/test-utils';

jest.mock('expo-router', () => ({
	useFocusEffect: jest.fn(),
}));

const mockedUseFocusEffect = useFocusEffect as jest.MockedFunction<typeof useFocusEffect>;

describe('useRefetchOnFocus', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	it('registers a focus effect callback', async () => {
        const { wrapper } = createWrapper();

        await renderHook(() => useRefetchOnFocus(['friends']), { wrapper });

        expect(mockedUseFocusEffect).toHaveBeenCalledWith(expect.any(Function));
    });

	it('invalidates the given query key when the focus callback runs', async () => {
        const { wrapper, queryClient } = createWrapper();
        const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

        await renderHook(() => useRefetchOnFocus(['friends']), { wrapper });

        const focusCallback = mockedUseFocusEffect.mock.calls[0][0];
        focusCallback();

        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['friends'] });
    });

	it('invalidates using the exact query key provided, including nested segments', async () => {
        const { wrapper, queryClient } = createWrapper();
        const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

        await renderHook(() => useRefetchOnFocus(['matches', 'detail', 'match-123']), { wrapper });

        const focusCallback = mockedUseFocusEffect.mock.calls[0][0];
        focusCallback();

        expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: ['matches', 'detail', 'match-123'] });
    });

	it('re-registers the callback when the queryKey changes', async () => {
        const { wrapper } = createWrapper();

        const { rerender } = await renderHook(
            ({ queryKey }: { queryKey: (string | undefined)[] }) => useRefetchOnFocus(queryKey),
            { wrapper, initialProps: { queryKey: ['collection', 'status', 'game-1'] } }
        );

        expect(mockedUseFocusEffect).toHaveBeenCalledTimes(1);

        await rerender({ queryKey: ['collection', 'status', 'game-2'] });

        expect(mockedUseFocusEffect).toHaveBeenCalledTimes(2);
    });

	it('does not re-register the callback when the queryKey is unchanged', async () => {
        const { wrapper } = createWrapper();

        const { rerender } = await renderHook(
            ({ queryKey }: { queryKey: string[] }) => useRefetchOnFocus(queryKey),
            { wrapper, initialProps: { queryKey: ['friends'] } }
        );

        expect(mockedUseFocusEffect).toHaveBeenCalledTimes(1);

        rerender({ queryKey: ['friends'] });

        expect(mockedUseFocusEffect).toHaveBeenCalledTimes(1);
    });
});