import { AppState, AppStateStatus } from 'react-native';
import { renderHook, waitFor, act } from '@testing-library/react-native';
import axios from 'axios';

import { setNetworkErrorHandler } from '@/api/client';
import { useHealthCheck } from '@/hooks/use-health-check';

jest.mock('axios');

jest.mock('@/api/client', () => ({
	API_URL: 'https://api.test/api/v1',
	REQUEST_TIMEOUT_MS: 8000,
	setNetworkErrorHandler: jest.fn(),
}));

const mockedAxios = axios as jest.Mocked<typeof axios>;
const mockedSetNetworkErrorHandler = jest.mocked(setNetworkErrorHandler);

const HEALTH_URL = 'https://api.test/api/v1/health';
const POLL_INTERVAL_MS = 10000;

let appStateListener: ((status: AppStateStatus) => void) | null = null;
const mockRemoveAppStateListener = jest.fn();

describe('useHealthCheck', () => {
	beforeEach(() => {
		jest.useFakeTimers();
		jest.clearAllMocks();

		appStateListener = null;
		jest.spyOn(AppState, 'addEventListener').mockImplementation((_, listener) => {
			appStateListener = listener as (status: AppStateStatus) => void;
			return { remove: mockRemoveAppStateListener } as any;
		});
	});

	afterEach(() => {
		jest.useRealTimers();
		jest.restoreAllMocks();
	});

	it('checks the health endpoint on mount with the request timeout', async () => {
		mockedAxios.get.mockResolvedValueOnce({ data: 'OK' });

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(1));

		expect(mockedAxios.get).toHaveBeenCalledWith(HEALTH_URL, { timeout: 8000 });
		expect(result.current.isHealthy).toBe(true);
	});

	it('sets isHealthy to false when the health check fails', async () => {
		mockedAxios.get.mockRejectedValueOnce(new Error('Network error'));

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isHealthy).toBe(false));
	});

	it('sets isChecking to true while the request is in flight', async () => {
		let resolveRequest: () => void = () => undefined;
		mockedAxios.get.mockReturnValueOnce(
			new Promise((resolve) => {
				resolveRequest = () => resolve({ data: 'OK' });
			})
		);

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isChecking).toBe(true));

		await act(async () => {
			resolveRequest();
		});

		await waitFor(() => expect(result.current.isChecking).toBe(false));
	});

	it('does not poll while the backend is healthy', async () => {
		mockedAxios.get.mockResolvedValue({ data: 'OK' });

		await renderHook(() => useHealthCheck());

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(1));

		await act(async () => {
			jest.advanceTimersByTime(POLL_INTERVAL_MS * 3);
		});

		expect(mockedAxios.get).toHaveBeenCalledTimes(1);
	});

	it('polls every interval while unhealthy and stops once it recovers', async () => {
		mockedAxios.get
			.mockRejectedValueOnce(new Error('Network error'))
			.mockRejectedValueOnce(new Error('Network error'))
			.mockResolvedValue({ data: 'OK' });

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isHealthy).toBe(false));

		await act(async () => {
			jest.advanceTimersByTime(POLL_INTERVAL_MS);
		});

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(2));
		expect(result.current.isHealthy).toBe(false);

		await act(async () => {
			jest.advanceTimersByTime(POLL_INTERVAL_MS);
		});

		await waitFor(() => expect(result.current.isHealthy).toBe(true));
		expect(mockedAxios.get).toHaveBeenCalledTimes(3);

		await act(async () => {
			jest.advanceTimersByTime(POLL_INTERVAL_MS * 3);
		});

		expect(mockedAxios.get).toHaveBeenCalledTimes(3);
	});

	it('retryNow triggers an immediate check without waiting for the interval', async () => {
		mockedAxios.get
			.mockRejectedValueOnce(new Error('Network error'))
			.mockResolvedValueOnce({ data: 'OK' });

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isHealthy).toBe(false));

		await act(async () => {
			await result.current.retryNow();
		});

		expect(result.current.isHealthy).toBe(true);
		expect(mockedAxios.get).toHaveBeenCalledTimes(2);
	});

	it('ignores a new check while one is already in flight', async () => {
		let resolveRequest: () => void = () => undefined;
		mockedAxios.get.mockReturnValueOnce(
			new Promise((resolve) => {
				resolveRequest = () => resolve({ data: 'OK' });
			})
		);

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isChecking).toBe(true));

		await act(async () => {
			result.current.retryNow();
			result.current.retryNow();
		});

		expect(mockedAxios.get).toHaveBeenCalledTimes(1);

		await act(async () => {
			resolveRequest();
		});
	});

	it('checks again when the app returns to the foreground', async () => {
		mockedAxios.get.mockResolvedValue({ data: 'OK' });

		await renderHook(() => useHealthCheck());

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(1));

		await act(async () => {
			appStateListener?.('background');
		});

		expect(mockedAxios.get).toHaveBeenCalledTimes(1);

		await act(async () => {
			appStateListener?.('active');
		});

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(2));
	});

	it('registers itself as the network error handler and clears it on unmount', async () => {
		mockedAxios.get.mockResolvedValue({ data: 'OK' });

		const { unmount } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(1));

		const handler = mockedSetNetworkErrorHandler.mock.calls[0][0] as () => Promise<void>;
		expect(handler).toEqual(expect.any(Function));

		await act(async () => {
			await handler();
		});

		expect(mockedAxios.get).toHaveBeenCalledTimes(2);

		await act(async () => {
			await unmount();
		});

		expect(mockedSetNetworkErrorHandler).toHaveBeenLastCalledWith(null);
		expect(mockRemoveAppStateListener).toHaveBeenCalled();
	});

	it('clears the polling interval on unmount', async () => {
		mockedAxios.get.mockRejectedValue(new Error('Network error'));

		const { result, unmount } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isHealthy).toBe(false));

		await act(async () => {
			await unmount();
		});

		jest.advanceTimersByTime(POLL_INTERVAL_MS * 3);

		expect(mockedAxios.get).toHaveBeenCalledTimes(1);
	});
});