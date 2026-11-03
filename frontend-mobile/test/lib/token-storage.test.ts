import type * as SecureStoreModule from 'expo-secure-store';
import type { tokenStorage as TokenStorage } from '@/lib/token-storage';

jest.mock('expo-secure-store');

const ACCESS_TOKEN_KEY = 'lets_table_access_token';
const REFRESH_TOKEN_KEY = 'lets_table_refresh_token';

let SecureStore: jest.Mocked<typeof SecureStoreModule>;
let tokenStorage: typeof TokenStorage;

describe('tokenStorage', () => {
	beforeEach(() => {
		jest.resetModules();

		SecureStore = require('expo-secure-store');
		({ tokenStorage } = require('@/lib/token-storage'));

		SecureStore.setItemAsync.mockResolvedValue(undefined);
		SecureStore.deleteItemAsync.mockResolvedValue(undefined);
	});

	describe('getAccessToken', () => {
		it('reads the access token using the correct key', async () => {
			SecureStore.getItemAsync.mockResolvedValueOnce('access-token-value');

			const result = await tokenStorage.getAccessToken();

			expect(SecureStore.getItemAsync).toHaveBeenCalledWith(ACCESS_TOKEN_KEY);
			expect(result).toBe('access-token-value');
		});

		it('returns null when no access token is stored', async () => {
			SecureStore.getItemAsync.mockResolvedValueOnce(null);

			const result = await tokenStorage.getAccessToken();

			expect(result).toBeNull();
		});

		it('reads from secure storage only once and then uses the cached value', async () => {
			SecureStore.getItemAsync.mockResolvedValueOnce('access-token-value');

			await tokenStorage.getAccessToken();
			const second = await tokenStorage.getAccessToken();

			expect(second).toBe('access-token-value');
			expect(SecureStore.getItemAsync).toHaveBeenCalledTimes(1);
		});

		it('caches a missing token as well', async () => {
			SecureStore.getItemAsync.mockResolvedValueOnce(null);

			await tokenStorage.getAccessToken();
			const second = await tokenStorage.getAccessToken();

			expect(second).toBeNull();
			expect(SecureStore.getItemAsync).toHaveBeenCalledTimes(1);
		});
	});

	describe('getRefreshToken', () => {
		it('reads the refresh token using the correct key', async () => {
			SecureStore.getItemAsync.mockResolvedValueOnce('refresh-token-value');

			const result = await tokenStorage.getRefreshToken();

			expect(SecureStore.getItemAsync).toHaveBeenCalledWith(REFRESH_TOKEN_KEY);
			expect(result).toBe('refresh-token-value');
		});

		it('returns null when no refresh token is stored', async () => {
			SecureStore.getItemAsync.mockResolvedValueOnce(null);

			const result = await tokenStorage.getRefreshToken();

			expect(result).toBeNull();
		});

		it('caches the refresh token independently from the access token', async () => {
			SecureStore.getItemAsync
				.mockResolvedValueOnce('access-token-value')
				.mockResolvedValueOnce('refresh-token-value');

			await tokenStorage.getAccessToken();
			await tokenStorage.getRefreshToken();
			await tokenStorage.getRefreshToken();

			expect(SecureStore.getItemAsync).toHaveBeenCalledTimes(2);
		});
	});

	describe('setTokens', () => {
		it('stores both tokens with the correct keys and values', async () => {
			await tokenStorage.setTokens('new-access-token', 'new-refresh-token');

			expect(SecureStore.setItemAsync).toHaveBeenCalledWith(ACCESS_TOKEN_KEY, 'new-access-token');
			expect(SecureStore.setItemAsync).toHaveBeenCalledWith(REFRESH_TOKEN_KEY, 'new-refresh-token');
			expect(SecureStore.setItemAsync).toHaveBeenCalledTimes(2);
		});

		it('updates the cache so the new tokens are returned without reading secure storage', async () => {
			await tokenStorage.setTokens('new-access-token', 'new-refresh-token');

			expect(await tokenStorage.getAccessToken()).toBe('new-access-token');
			expect(await tokenStorage.getRefreshToken()).toBe('new-refresh-token');
			expect(SecureStore.getItemAsync).not.toHaveBeenCalled();
		});
	});

	describe('clearTokens', () => {
		it('deletes both tokens using the correct keys', async () => {
			await tokenStorage.clearTokens();

			expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith(ACCESS_TOKEN_KEY);
			expect(SecureStore.deleteItemAsync).toHaveBeenCalledWith(REFRESH_TOKEN_KEY);
			expect(SecureStore.deleteItemAsync).toHaveBeenCalledTimes(2);
		});

		it('clears the cache so both tokens are null without reading secure storage', async () => {
			await tokenStorage.setTokens('access', 'refresh');
			await tokenStorage.clearTokens();

			expect(await tokenStorage.getAccessToken()).toBeNull();
			expect(await tokenStorage.getRefreshToken()).toBeNull();
			expect(SecureStore.getItemAsync).not.toHaveBeenCalled();
		});
	});
});