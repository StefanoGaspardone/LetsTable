import { AppState } from 'react-native';
import { AxiosError } from 'axios';
import { focusManager, MutationObserver, type QueryClient } from '@tanstack/react-query';

const mockRemove = jest.fn();
let appStateListener: (status: string) => void;
let queryClient: QueryClient;

beforeAll(() => {
	jest.spyOn(AppState, 'addEventListener').mockImplementation((_type, listener) => {
		appStateListener = listener as (status: string) => void;
		return { remove: mockRemove } as any;
	});

	({ queryClient } = require('@/lib/query-client'));
});

afterAll(() => {
	queryClient.clear();
	jest.restoreAllMocks();
});

const axiosError = (status?: number) => new AxiosError('Request failed', 'ERR_BAD_RESPONSE', undefined, undefined, status ? { status } as any : undefined);

const getRetry = () => queryClient.getDefaultOptions().queries?.retry as (failureCount: number, error: unknown) => boolean;

describe('queryClient', () => {
	it('keeps data fresh for 30 seconds', () => {
		expect(queryClient.getDefaultOptions().queries?.staleTime).toBe(30000);
	});

	describe('retry', () => {
		it.each([400, 401, 403, 404, 409, 422])('does not retry client errors (%i)', status => {
			expect(getRetry()(0, axiosError(status))).toBe(false);
		});

		it.each([500, 502, 503])('retries server errors (%i) up to two times', status => {
			expect(getRetry()(0, axiosError(status))).toBe(true);
			expect(getRetry()(1, axiosError(status))).toBe(true);
			expect(getRetry()(2, axiosError(status))).toBe(false);
		});

		it('retries network errors without a response', () => {
			expect(getRetry()(0, axiosError())).toBe(true);
			expect(getRetry()(2, axiosError())).toBe(false);
		});

		it('retries non-axios errors', () => {
			expect(getRetry()(1, new Error('boom'))).toBe(true);
			expect(getRetry()(2, new Error('boom'))).toBe(false);
		});
	});

	describe('mutation cache', () => {
		it('invalidates the achievements after every successful mutation', async () => {
			const invalidateQueries = jest.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
			const observer = new MutationObserver(queryClient, { mutationFn: async () => 'ok' });

			await observer.mutate();

			expect(invalidateQueries).toHaveBeenCalledWith({ queryKey: ['achievements'] });
			invalidateQueries.mockRestore();
		});

		it('does not invalidate the achievements when a mutation fails', async () => {
			const invalidateQueries = jest.spyOn(queryClient, 'invalidateQueries').mockResolvedValue();
			const observer = new MutationObserver(queryClient, { mutationFn: async () => { throw new Error('boom'); } });

			await expect(observer.mutate()).rejects.toThrow('boom');

			expect(invalidateQueries).not.toHaveBeenCalled();
			invalidateQueries.mockRestore();
		});
	});

	describe('focus manager', () => {
		it('subscribes to the app state changes', () => {
			expect(AppState.addEventListener).toHaveBeenCalledWith('change', expect.any(Function));
		});

		it('marks the app as focused only when it becomes active', () => {
			appStateListener('background');
			expect(focusManager.isFocused()).toBe(false);

			appStateListener('active');
			expect(focusManager.isFocused()).toBe(true);
		});

		it('removes the app state subscription on cleanup', () => {
			focusManager.setEventListener(() => undefined);

			expect(mockRemove).toHaveBeenCalledTimes(1);
		});
	});
});