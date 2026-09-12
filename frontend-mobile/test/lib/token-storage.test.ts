import * as SecureStore from 'expo-secure-store';

import { tokenStorage } from '@/lib/token-storage';

jest.mock('expo-secure-store');

const mockedGetItemAsync = SecureStore.getItemAsync as jest.MockedFunction<typeof SecureStore.getItemAsync>;
const mockedSetItemAsync = SecureStore.setItemAsync as jest.MockedFunction<typeof SecureStore.setItemAsync>;
const mockedDeleteItemAsync = SecureStore.deleteItemAsync as jest.MockedFunction<typeof SecureStore.deleteItemAsync>;

describe('tokenStorage', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('getAccessToken', () => {
		it('reads the access token using the correct key', async () => {
			mockedGetItemAsync.mockResolvedValueOnce('access-token-value');

			const result = await tokenStorage.getAccessToken();

			expect(mockedGetItemAsync).toHaveBeenCalledWith('lets_table_access_token');
			expect(result).toBe('access-token-value');
		});

		it('returns null when no access token is stored', async () => {
			mockedGetItemAsync.mockResolvedValueOnce(null);

			const result = await tokenStorage.getAccessToken();

			expect(result).toBeNull();
		});
	});

	describe('getRefreshToken', () => {
		it('reads the refresh token using the correct key', async () => {
			mockedGetItemAsync.mockResolvedValueOnce('refresh-token-value');

			const result = await tokenStorage.getRefreshToken();

			expect(mockedGetItemAsync).toHaveBeenCalledWith('lets_table_refresh_token');
			expect(result).toBe('refresh-token-value');
		});

		it('returns null when no refresh token is stored', async () => {
			mockedGetItemAsync.mockResolvedValueOnce(null);

			const result = await tokenStorage.getRefreshToken();

			expect(result).toBeNull();
		});
	});

	describe('setTokens', () => {
		it('stores both tokens with the correct keys and values', async () => {
			await tokenStorage.setTokens('new-access-token', 'new-refresh-token');

			expect(mockedSetItemAsync).toHaveBeenCalledWith('lets_table_access_token', 'new-access-token');
			expect(mockedSetItemAsync).toHaveBeenCalledWith('lets_table_refresh_token', 'new-refresh-token');
		});

		it('calls setItemAsync exactly twice', async () => {
			await tokenStorage.setTokens('access', 'refresh');

			expect(mockedSetItemAsync).toHaveBeenCalledTimes(2);
		});

		it('uses different keys for access and refresh tokens', () => {
			expect(mockedSetItemAsync).not.toHaveBeenCalled();
		});
	});

	describe('clearTokens', () => {
		it('deletes both tokens using the correct keys', async () => {
			await tokenStorage.clearTokens();

			expect(mockedDeleteItemAsync).toHaveBeenCalledWith('lets_table_access_token');
			expect(mockedDeleteItemAsync).toHaveBeenCalledWith('lets_table_refresh_token');
		});

		it('calls deleteItemAsync exactly twice', async () => {
			await tokenStorage.clearTokens();

			expect(mockedDeleteItemAsync).toHaveBeenCalledTimes(2);
		});
	});

	it('uses different storage keys for access and refresh tokens', async () => {
		await tokenStorage.setTokens('access-value', 'refresh-value');

		const accessCall = mockedSetItemAsync.mock.calls.find(call => call[1] === 'access-value');
		const refreshCall = mockedSetItemAsync.mock.calls.find(call => call[1] === 'refresh-value');

		expect(accessCall?.[0]).not.toBe(refreshCall?.[0]);
	});
});