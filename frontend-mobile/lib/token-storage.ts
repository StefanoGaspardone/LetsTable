import * as SecureStore from 'expo-secure-store';

const ACCESS_TOKEN_KEY = 'lets_table_access_token';
const REFRESH_TOKEN_KEY = 'lets_table_refresh_token';

let cachedAccessToken: string | null | undefined;
let cachedRefreshToken: string | null | undefined;

export const tokenStorage = {
	async getAccessToken(): Promise<string | null> {
		if(cachedAccessToken === undefined) {
			cachedAccessToken = await SecureStore.getItemAsync(ACCESS_TOKEN_KEY);
		}

		return cachedAccessToken;
	},

	async getRefreshToken(): Promise<string | null> {
		if(cachedRefreshToken === undefined) {
			cachedRefreshToken = await SecureStore.getItemAsync(REFRESH_TOKEN_KEY);
		}

		return cachedRefreshToken;
	},

	async setTokens(accessToken: string, refreshToken: string): Promise<void> {
		cachedAccessToken = accessToken;
		cachedRefreshToken = refreshToken;

		await Promise.all([
			SecureStore.setItemAsync(ACCESS_TOKEN_KEY, accessToken),
			SecureStore.setItemAsync(REFRESH_TOKEN_KEY, refreshToken),
		]);
	},

	async clearTokens(): Promise<void> {
		cachedAccessToken = null;
		cachedRefreshToken = null;

		await Promise.all([
			SecureStore.deleteItemAsync(ACCESS_TOKEN_KEY),
			SecureStore.deleteItemAsync(REFRESH_TOKEN_KEY),
		]);
	},
};