import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import { router } from 'expo-router';

import { tokenStorage } from '@/lib/token-storage';

export const API_URL = process.env.EXPO_PUBLIC_API_URL ?? 'https://letstable-backend-1040192390713.europe-west1.run.app/api/v1';

export const REQUEST_TIMEOUT_MS = 15000;

export const apiClient = axios.create({
	baseURL: API_URL,
	timeout: REQUEST_TIMEOUT_MS,
});

type RetriableRequest = InternalAxiosRequestConfig & { _retry?: boolean };

let refreshPromise: Promise<string> | null = null;
let sessionExpiredHandler: (() => void) | null = null;
let networkErrorHandler: (() => void) | null = null;

export const setSessionExpiredHandler = (handler: (() => void) | null) => {
	sessionExpiredHandler = handler;
}

export const setNetworkErrorHandler = (handler: (() => void) | null) => {
	networkErrorHandler = handler;
}

const isAuthEndpoint = (url?: string) => !!url && (url.includes('/auth/refresh') || url.includes('/auth/login'));

const expireSession = async () => {
	await tokenStorage.clearTokens();

	if(sessionExpiredHandler) sessionExpiredHandler();
	else router.replace('/(auth)/welcome');
}

const refreshAccessToken = async (): Promise<string> => {
	const refreshToken = await tokenStorage.getRefreshToken();

	if(!refreshToken) {
		await expireSession();
		throw new Error('Missing refresh token');
	}

	try {
		const { data } = await axios.post(`${API_URL}/auth/refresh`, { refreshToken }, { timeout: REQUEST_TIMEOUT_MS });
		await tokenStorage.setTokens(data.accessToken, data.refreshToken);

		return data.accessToken;
	} catch(error) {
		const status = axios.isAxiosError(error) ? error.response?.status : undefined;
		if(status && status < 500) await expireSession();

		throw error;
	}
}

export const refreshSession = (): Promise<string> => {
	refreshPromise ??= refreshAccessToken().finally(() => {
		refreshPromise = null;
	});

	return refreshPromise;
}

apiClient.interceptors.request.use(async (config) => {
	const accessToken = await tokenStorage.getAccessToken();

	if(accessToken) {
		config.headers.Authorization = `Bearer ${accessToken}`;
	}

	return config;
});

apiClient.interceptors.response.use(
	(response) => response,
	async (error: AxiosError) => {
		if(!error.response) {
			if(error.code !== 'ERR_CANCELED') networkErrorHandler?.();
			throw error;
		}

		const originalRequest = error.config as RetriableRequest | undefined;
		const status = error.response.status;
		const isAuthError = status === 401 || status === 403;

		if(!originalRequest || !isAuthError || originalRequest._retry || isAuthEndpoint(originalRequest.url)) {
			throw error;
		}

		originalRequest._retry = true;

		const accessToken = await refreshSession();
		originalRequest.headers.Authorization = `Bearer ${accessToken}`;

		return apiClient(originalRequest);
	}
);