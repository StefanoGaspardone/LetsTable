import { ReactNode } from 'react';
import { renderHook, waitFor, act } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { AxiosError } from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AuthProvider, useAuth } from '@/contexts/auth-context';
import { setSessionExpiredHandler } from '@/api/client';
import { logout as logoutRequest } from '@/api/auth';
import { getMe } from '@/api/user';
import { tokenStorage } from '@/lib/token-storage';
import { requestAndRegisterPushToken, unregisterCurrentPushToken } from '@/lib/push-notifications';
import { syncWidgets } from '@/widgets/widget-registry';

jest.mock('@/api/client', () => ({
	setSessionExpiredHandler: jest.fn(),
}));

jest.mock('@/api/auth', () => ({
	logout: jest.fn(),
}));

jest.mock('@/api/user', () => ({
	getMe: jest.fn(),
}));

jest.mock('@/lib/token-storage', () => ({
	tokenStorage: {
		getAccessToken: jest.fn(),
		getRefreshToken: jest.fn(),
		setTokens: jest.fn(),
		clearTokens: jest.fn(),
	},
}));

jest.mock('@/lib/push-notifications', () => ({
	requestAndRegisterPushToken: jest.fn(),
	unregisterCurrentPushToken: jest.fn(),
}));

jest.mock('@/widgets/widget-registry', () => ({
	syncWidgets: jest.fn(),
}));

const mockedSetSessionExpiredHandler = jest.mocked(setSessionExpiredHandler);
const mockedLogoutRequest = jest.mocked(logoutRequest);
const mockedGetMe = jest.mocked(getMe);
const mockedGetAccessToken = jest.mocked(tokenStorage.getAccessToken);
const mockedGetRefreshToken = jest.mocked(tokenStorage.getRefreshToken);
const mockedSetTokens = jest.mocked(tokenStorage.setTokens);
const mockedClearTokens = jest.mocked(tokenStorage.clearTokens);
const mockedRequestAndRegisterPushToken = jest.mocked(requestAndRegisterPushToken);
const mockedUnregisterCurrentPushToken = jest.mocked(unregisterCurrentPushToken);
const mockedSyncWidgets = jest.mocked(syncWidgets);

const USER_STORAGE_KEY = '@auth_user';

const sampleUser = { id: 'user-1', username: 'stefano', email: 'stefano@example.com' } as any;
const cachedUser = { id: 'user-1', username: 'stefano-cached', email: 'stefano@example.com' } as any;

const httpError = (status: number) =>
	new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, { status, data: {}, statusText: '', headers: {}, config: {} } as any);

const networkError = () => new AxiosError('Network Error', 'ERR_NETWORK');

const readPersistedUser = async () => {
	const stored = await AsyncStorage.getItem(USER_STORAGE_KEY);
	return stored ? JSON.parse(stored) : null;
}

const createAuthWrapper = () => {
	const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });

	const wrapper = ({ children }: { children: ReactNode }) => (
		<QueryClientProvider client = { queryClient }>
			<AuthProvider>{children}</AuthProvider>
		</QueryClientProvider>
	);

	return { wrapper, queryClient };
}

const renderAuth = async () => {
	const { wrapper, queryClient } = createAuthWrapper();
	const rendered = await renderHook(() => useAuth(), { wrapper });

	return { ...rendered, queryClient };
}

describe('AuthProvider / useAuth', () => {
	beforeEach(async () => {
		jest.clearAllMocks();
		await AsyncStorage.clear();

		mockedGetRefreshToken.mockResolvedValue(null);
		mockedSetTokens.mockResolvedValue(undefined);
		mockedClearTokens.mockResolvedValue(undefined);
		mockedLogoutRequest.mockResolvedValue(undefined);
		mockedRequestAndRegisterPushToken.mockResolvedValue(null);
		mockedUnregisterCurrentPushToken.mockResolvedValue(undefined);
		mockedSyncWidgets.mockResolvedValue(undefined);
	});

	describe('restoreSession on mount', () => {
		it('resolves to unauthenticated when no access token is stored', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			const { result } = await renderAuth();

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			expect(result.current.isAuthenticated).toBe(false);
			expect(result.current.user).toBeNull();
			expect(mockedGetMe).not.toHaveBeenCalled();
		});

		it('restores the user session when a valid access token exists and persists the user', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');
			mockedGetMe.mockResolvedValueOnce(sampleUser);

			const { result } = await renderAuth();

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			expect(result.current.isAuthenticated).toBe(true);
			expect(result.current.user).toEqual(sampleUser);
			await waitFor(async () => expect(await readPersistedUser()).toEqual(sampleUser));
		});

		it('shows the cached user immediately and replaces it with the fresh one', async () => {
			await AsyncStorage.setItem(USER_STORAGE_KEY, JSON.stringify(cachedUser));
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');

			let resolveMe: (user: any) => void = () => undefined;
			mockedGetMe.mockReturnValueOnce(new Promise(resolve => { resolveMe = resolve; }));

			const { result } = await renderAuth();

			await waitFor(() => expect(result.current.user).toEqual(cachedUser));
			expect(result.current.isLoading).toBe(false);

			await act(async () => {
				resolveMe(sampleUser);
			});

			await waitFor(() => expect(result.current.user).toEqual(sampleUser));
		});

		it('registers for push notifications after successfully restoring the session', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');
			mockedGetMe.mockResolvedValueOnce(sampleUser);

			const { result } = await renderAuth();

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			expect(mockedRequestAndRegisterPushToken).toHaveBeenCalledTimes(1);
		});

		it.each([401, 403])('clears the session when the server rejects the stored token with %i', async (status) => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
			await AsyncStorage.setItem(USER_STORAGE_KEY, JSON.stringify(cachedUser));
			mockedGetAccessToken.mockResolvedValueOnce('expired-token');
			mockedGetMe.mockRejectedValueOnce(httpError(status));

			const { result } = await renderAuth();

			await waitFor(() => expect(mockedClearTokens).toHaveBeenCalled());
			await waitFor(() => expect(result.current.user).toBeNull());

			expect(result.current.isAuthenticated).toBe(false);
			expect(result.current.isLoading).toBe(false);
			expect(mockedSyncWidgets).toHaveBeenCalledWith(null);
			expect(mockedRequestAndRegisterPushToken).not.toHaveBeenCalled();
			await waitFor(async () => expect(await readPersistedUser()).toBeNull());
			consoleLog.mockRestore();
		});

		it.each([
			['a network error', networkError()],
			['a server error', httpError(503)],
		])('keeps the cached session when restoring fails with %s', async (_, error) => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
			await AsyncStorage.setItem(USER_STORAGE_KEY, JSON.stringify(cachedUser));
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');
			mockedGetMe.mockRejectedValueOnce(error);

			const { result } = await renderAuth();

			await waitFor(() => expect(result.current.isLoading).toBe(false));
			await waitFor(() => expect(consoleLog).toHaveBeenCalledWith('Failed to restore session:', error));

			expect(result.current.user).toEqual(cachedUser);
			expect(result.current.isAuthenticated).toBe(true);
			expect(mockedClearTokens).not.toHaveBeenCalled();
			expect(mockedRequestAndRegisterPushToken).not.toHaveBeenCalled();
			consoleLog.mockRestore();
		});
	});

	describe('login', () => {
		it('stores the tokens, sets and persists the user and registers for push notifications', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			const { result } = await renderAuth();

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			await act(async () => {
				await result.current.login('access-token', 'refresh-token', sampleUser);
			});

			expect(mockedSetTokens).toHaveBeenCalledWith('access-token', 'refresh-token');
			expect(result.current.user).toEqual(sampleUser);
			expect(result.current.isAuthenticated).toBe(true);
			expect(mockedRequestAndRegisterPushToken).toHaveBeenCalledTimes(1);
			await waitFor(async () => expect(await readPersistedUser()).toEqual(sampleUser));
		});
	});

	describe('logout', () => {
		const loginFirst = async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			const rendered = await renderAuth();

			await waitFor(() => expect(rendered.result.current.isLoading).toBe(false));

			await act(async () => {
				await rendered.result.current.login('access-token', 'refresh-token', sampleUser);
			});

			return rendered;
		}

		it('unregisters the push token, revokes the refresh token, clears tokens, cache and widgets', async () => {
			const { result, queryClient } = await loginFirst();
			const clearSpy = jest.spyOn(queryClient, 'clear');
			mockedGetRefreshToken.mockResolvedValueOnce('refresh-token');

			await act(async () => {
				await result.current.logout();
			});

			expect(mockedUnregisterCurrentPushToken).toHaveBeenCalled();
			expect(mockedLogoutRequest).toHaveBeenCalledWith('refresh-token');
			expect(mockedClearTokens).toHaveBeenCalled();
			expect(clearSpy).toHaveBeenCalled();
			expect(mockedSyncWidgets).toHaveBeenCalledWith(null);
			expect(result.current.user).toBeNull();
			expect(result.current.isAuthenticated).toBe(false);
			await waitFor(async () => expect(await readPersistedUser()).toBeNull());
		});

		it('does not call the logout endpoint when there is no refresh token', async () => {
			const { result } = await loginFirst();
			mockedGetRefreshToken.mockResolvedValueOnce(null);

			await act(async () => {
				await result.current.logout();
			});

			expect(mockedLogoutRequest).not.toHaveBeenCalled();
			expect(mockedClearTokens).toHaveBeenCalled();
			expect(result.current.user).toBeNull();
		});

		it('still logs out when the logout endpoint call fails', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
			const { result } = await loginFirst();
			mockedGetRefreshToken.mockResolvedValueOnce('refresh-token');
			mockedLogoutRequest.mockRejectedValueOnce(new Error('Network error'));

			await act(async () => {
				await result.current.logout();
			});

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(result.current.user).toBeNull();
			expect(consoleLog).toHaveBeenCalledWith('Failed to revoke refresh token:', expect.any(Error));
			consoleLog.mockRestore();
		});

		it('still logs out when unregistering the push token fails', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
			const { result } = await loginFirst();
			mockedUnregisterCurrentPushToken.mockRejectedValueOnce(new Error('Push error'));

			await act(async () => {
				await result.current.logout();
			});

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(result.current.user).toBeNull();
			consoleLog.mockRestore();
		});
	});

	describe('session expired handler', () => {
		it('registers a handler that clears the session and removes it on unmount', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			const { result, unmount, queryClient } = await renderAuth();
			const clearSpy = jest.spyOn(queryClient, 'clear');

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			await act(async () => {
				await result.current.login('access-token', 'refresh-token', sampleUser);
			});

			const handler = mockedSetSessionExpiredHandler.mock.calls.at(-1)?.[0] as () => void;
			expect(handler).toEqual(expect.any(Function));

			await act(async () => {
				handler();
			});

			expect(result.current.user).toBeNull();
			expect(clearSpy).toHaveBeenCalled();
			expect(mockedSyncWidgets).toHaveBeenCalledWith(null);

			await act(async () => {
				await unmount();
			});

			expect(mockedSetSessionExpiredHandler).toHaveBeenLastCalledWith(null);
		});
	});

	describe('updateUser', () => {
		it('updates and persists the current user', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');
			mockedGetMe.mockResolvedValueOnce(sampleUser);

			const { result } = await renderAuth();

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			const updatedUser = { ...sampleUser, username: 'new-username' };

			await act(() => {
				result.current.updateUser(updatedUser);
			});

			expect(result.current.user).toEqual(updatedUser);
			await waitFor(async () => expect(await readPersistedUser()).toEqual(updatedUser));
		});
	});

	describe('useAuth outside AuthProvider', () => {
		it('throws when used without a provider', async () => {
			await expect(renderHook(() => useAuth())).rejects.toThrow(
				'useAuth must be used within an AuthProvider'
			);
		});
	});
});