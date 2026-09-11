import { renderHook, act } from '@testing-library/react-native';

import { useDebounce } from '@/hooks/use-debounce';

describe('useDebounce', () => {
	beforeEach(() => {
		jest.useFakeTimers();
	});

	afterEach(() => {
		jest.useRealTimers();
	});

	it('returns the initial value immediately', async () => {
		const { result } = await renderHook(() => useDebounce('initial'));

		expect(result.current).toBe('initial');
	});

	it('does not update the value before the delay has passed', async () => {
		const { result, rerender } = await renderHook(({ value }: { value: string }) => useDebounce(value), {
			initialProps: { value: 'first' },
		});

		await rerender({ value: 'second' });

		await act(async () => {
			jest.advanceTimersByTime(399);
		});

		expect(result.current).toBe('first');
	});

	it('updates the value after the default delay (400ms)', async () => {
		const { result, rerender } = await renderHook(({ value }: { value: string }) => useDebounce(value), {
			initialProps: { value: 'first' },
		});

		await rerender({ value: 'second' });

		await act(async () => {
			jest.advanceTimersByTime(400);
		});

		expect(result.current).toBe('second');
	});

	it('respects a custom delay', async () => {
		const { result, rerender } = await renderHook(({ value }: { value: string }) => useDebounce(value, 1000), {
			initialProps: { value: 'first' },
		});

		await rerender({ value: 'second' });

		await act(async () => {
			jest.advanceTimersByTime(400);
		});
		expect(result.current).toBe('first');

		await act(async () => {
			jest.advanceTimersByTime(600);
		});
		expect(result.current).toBe('second');
	});

	it('resets the timer when the value changes again before the delay elapses', async () => {
		const { result, rerender } = await renderHook(({ value }: { value: string }) => useDebounce(value), {
			initialProps: { value: 'first' },
		});

		await rerender({ value: 'second' });

		await act(async () => {
			jest.advanceTimersByTime(300);
		});

		await rerender({ value: 'third' });

		await act(async () => {
			jest.advanceTimersByTime(300);
		});
		expect(result.current).toBe('first');

		await act(async () => {
			jest.advanceTimersByTime(100);
		});
		expect(result.current).toBe('third');
	});
});