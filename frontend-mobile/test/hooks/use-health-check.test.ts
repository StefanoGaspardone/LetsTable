import { renderHook, waitFor, act } from '@testing-library/react-native';
import axios from 'axios';

import { useHealthCheck } from '@/hooks/use-health-check';

jest.mock('axios');

const mockedAxios = axios as jest.Mocked<typeof axios>;

describe('useHealthCheck', () => {
	beforeEach(() => {
		jest.useFakeTimers();
		jest.clearAllMocks();
	});

	afterEach(() => {
		jest.useRealTimers();
	});

	it('starts as healthy and performs an immediate check on mount', async () => {
		mockedAxios.get.mockResolvedValueOnce({ data: 'OK' });

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(1));

		expect(result.current.isHealthy).toBe(true);
	});

	it('sets isHealthy to false when the health check fails', async () => {
		mockedAxios.get.mockRejectedValueOnce(new Error('Network error'));

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isHealthy).toBe(false));
	});

	it('sets isChecking to true while the request is in flight', async () => {
		let resolveRequest: () => void;
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

	it('polls again automatically after the interval elapses', async () => {
		mockedAxios.get.mockResolvedValue({ data: 'OK' });

		await renderHook(() => useHealthCheck());

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(1));

		await act(async () => {
			jest.advanceTimersByTime(10000);
		});

		await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(2));
	});

	it('recovers to healthy on the next successful poll after a failure', async () => {
		mockedAxios.get
			.mockRejectedValueOnce(new Error('Network error'))
			.mockResolvedValueOnce({ data: 'OK' });

		const { result } = await renderHook(() => useHealthCheck());

		await waitFor(() => expect(result.current.isHealthy).toBe(false));

		await act(async () => {
			jest.advanceTimersByTime(10000);
		});

		await waitFor(() => expect(result.current.isHealthy).toBe(true));
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

	it('calls axios.get with the correct timeout option', async () => {
		mockedAxios.get.mockResolvedValueOnce({ data: 'OK' });

		await renderHook(() => useHealthCheck());

		await waitFor(() => {
			expect(mockedAxios.get).toHaveBeenCalledWith(
				expect.any(String),
				expect.objectContaining({ timeout: 8000 })
			);
		});
	});

	it('clears the interval on unmount', async () => {
        mockedAxios.get.mockResolvedValue({ data: 'OK' });

        const { unmount } = await renderHook(() => useHealthCheck());

        await waitFor(() => expect(mockedAxios.get).toHaveBeenCalledTimes(1));

        await act(async () => {
            await unmount();
        });

        jest.advanceTimersByTime(30000);

        expect(mockedAxios.get).toHaveBeenCalledTimes(1);
    });
});