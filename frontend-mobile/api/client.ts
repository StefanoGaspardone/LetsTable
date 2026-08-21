import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import Constants from 'expo-constants';

import { tokenStorage } from '@/lib/token-storage';

const API_URL = Constants.expoConfig?.extra?.apiUrl as string;

export const apiClient = axios.create({
	baseURL: API_URL,
	timeout: 15000,
});

apiClient.interceptors.request.use(async (config) => {
	const accessToken = await tokenStorage.getAccessToken();
	if(accessToken) {
		config.headers.Authorization = `Bearer ${accessToken}`;
	}
	
	return config;
});

let isRefreshing = false;
let refreshQueue: Array<(token: string | null) => void> = [];

const resolveQueue = (token: string | null) => {
	refreshQueue.forEach((callback) => callback(token));
	refreshQueue = [];
}

apiClient.interceptors.response.use(
	(response) => response,
	async (error: AxiosError) => {
		const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

		if(error.response?.status !== 401 || originalRequest._retry || originalRequest.url?.includes('/auth/refresh')) {
			throw error;
		}

		if(isRefreshing) {
			return new Promise((resolve, reject) => {
				refreshQueue.push((token) => {
					if(token) {
						originalRequest.headers.Authorization = `Bearer ${token}`;
						resolve(apiClient(originalRequest));
					} else {
						reject(error);
					}
				});
			});
		}

		originalRequest._retry = true;
		isRefreshing = true;

		try {
			const refreshToken = await tokenStorage.getRefreshToken();
			if(!refreshToken) {
				throw error;
			}

			const { data } = await axios.post(`${API_URL}/auth/refresh`, { refreshToken });
			await tokenStorage.setTokens(data.accessToken, data.refreshToken);

			resolveQueue(data.accessToken);

			originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
			return apiClient(originalRequest);
		} catch(refreshError) {
			resolveQueue(null);
			await tokenStorage.clearTokens();
			
            throw refreshError;
		} finally {
			isRefreshing = false;
		}
	}
);