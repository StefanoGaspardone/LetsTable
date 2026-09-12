import { ReactNode } from 'react';
import { renderHook, waitFor, act } from '@testing-library/react-native';
import axios from 'axios';

import { AuthProvider, useAuth } from '@/contexts/auth-context';
import { apiClient } from '@/api/client';
import { tokenStorage } from '@/lib/token-storage';
import { requestAndRegisterPushToken, unregisterCurrentPushToken } from '@/lib/push-notifications';

jest.mock('@/api/client');
jest.mock('@/lib/token-storage');
jest.mock('@/lib/push-notifications');
jest.mock('axios', () => ({
	...jest.requireActual('axios'),
	isAxiosError: jest.fn(),
}));

const mockedApiClientGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;
const mockedApiClientPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;
const mockedGetAccessToken = tokenStorage.getAccessToken as jest.MockedFunction<typeof tokenStorage.getAccessToken>;
const mockedGetRefreshToken = tokenStorage.getRefreshToken as jest.MockedFunction<typeof tokenStorage.getRefreshToken>;
const mockedSetTokens = tokenStorage.setTokens as jest.MockedFunction<typeof tokenStorage.setTokens>;
const mockedClearTokens = tokenStorage.clearTokens as jest.MockedFunction<typeof tokenStorage.clearTokens>;
const mockedRequestAndRegisterPushToken = requestAndRegisterPushToken as jest.MockedFunction<typeof requestAndRegisterPushToken>;
const mockedUnregisterCurrentPushToken = unregisterCurrentPushToken as jest.MockedFunction<typeof unregisterCurrentPushToken>;
const mockedIsAxiosError = axios.isAxiosError as unknown as jest.MockedFunction<(error: unknown) => boolean>;

const wrapper = ({ children }: { children: ReactNode }) => <AuthProvider>{children}</AuthProvider>;

const sampleUser = { id: 'user-1', username: 'stefano', email: 'stefano@example.com' } as any;

describe('AuthProvider / useAuth', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		mockedIsAxiosError.mockReturnValue(false);
	});

	describe('restoreSession on mount', () => {
		it('resolves to unauthenticated when no access token is stored', async () => {
            mockedGetAccessToken.mockResolvedValueOnce(null);

            const { result } = await renderHook(() => useAuth(), { wrapper });

            await waitFor(() => expect(result.current.isLoading).toBe(false));

            expect(result.current.isAuthenticated).toBe(false);
            expect(result.current.user).toBeNull();
            expect(mockedApiClientGet).not.toHaveBeenCalled();
        });

		it('restores the user session when a valid access token exists', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');
			mockedApiClientGet.mockResolvedValueOnce({ data: sampleUser } as any);

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			expect(result.current.isAuthenticated).toBe(true);
			expect(result.current.user).toEqual(sampleUser);
			expect(mockedApiClientGet).toHaveBeenCalledWith('/users/me');
		});

		it('registers for push notifications after successfully restoring the session', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');
			mockedApiClientGet.mockResolvedValueOnce({ data: sampleUser } as any);

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			expect(mockedRequestAndRegisterPushToken).toHaveBeenCalled();
		});

		it('clears tokens and logs out when restoring the session fails', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('expired-token');
			mockedApiClientGet.mockRejectedValueOnce(new Error('Unauthorized'));

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			expect(result.current.isAuthenticated).toBe(false);
			expect(result.current.user).toBeNull();
			expect(mockedClearTokens).toHaveBeenCalled();
		});

		it('does not register push notifications when restoring the session fails', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('expired-token');
			mockedApiClientGet.mockRejectedValueOnce(new Error('Unauthorized'));

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			expect(mockedRequestAndRegisterPushToken).not.toHaveBeenCalled();
		});
	});

	describe('login', () => {
		it('stores the tokens and sets the user', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			await act(async () => {
				await result.current.login('access-token', 'refresh-token', sampleUser);
			});

			expect(mockedSetTokens).toHaveBeenCalledWith('access-token', 'refresh-token');
			expect(result.current.user).toEqual(sampleUser);
			expect(result.current.isAuthenticated).toBe(true);
		});

		it('registers for push notifications after login', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			await act(async () => {
				await result.current.login('access-token', 'refresh-token', sampleUser);
			});

			expect(mockedRequestAndRegisterPushToken).toHaveBeenCalled();
		});
	});

	describe('logout', () => {
		it('unregisters the push token, calls the logout endpoint, and clears tokens', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);
			mockedGetRefreshToken.mockResolvedValueOnce('refresh-token');
			mockedApiClientPost.mockResolvedValueOnce({} as any);

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			await act(async () => {
				await result.current.logout();
			});

			expect(mockedUnregisterCurrentPushToken).toHaveBeenCalled();
			expect(mockedApiClientPost).toHaveBeenCalledWith('/auth/logout', { refreshToken: 'refresh-token' });
			expect(mockedClearTokens).toHaveBeenCalled();
			expect(result.current.user).toBeNull();
			expect(result.current.isAuthenticated).toBe(false);
		});

		it('does not call the logout endpoint when there is no refresh token', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);
			mockedGetRefreshToken.mockResolvedValueOnce(null);

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			await act(async () => {
				await result.current.logout();
			});

			expect(mockedApiClientPost).not.toHaveBeenCalled();
			expect(mockedClearTokens).toHaveBeenCalled();
		});

		it('still clears tokens and logs out even if the logout endpoint call fails', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);
			mockedGetRefreshToken.mockResolvedValueOnce('refresh-token');
			mockedApiClientPost.mockRejectedValueOnce(new Error('Network error'));

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			await act(async () => {
				await result.current.logout();
			});

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(result.current.user).toBeNull();
		});
	});

	describe('updateUser', () => {
		it('updates the current user in state', async () => {
			mockedGetAccessToken.mockResolvedValueOnce('valid-token');
			mockedApiClientGet.mockResolvedValueOnce({ data: sampleUser } as any);

			const { result } = await renderHook(() => useAuth(), { wrapper });

			await waitFor(() => expect(result.current.isLoading).toBe(false));

			const updatedUser = { ...sampleUser, username: 'new-username' };

			await act(() => {
				result.current.updateUser(updatedUser);
			});

			expect(result.current.user).toEqual(updatedUser);
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