import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';
import Constants from 'expo-constants';
import { tokenStorage } from '@/lib/token-storage';

const API_URL = Constants.expoConfig?.extra?.apiUrl as string;

export const apiClient = axios.create({
	baseURL: API_URL,
	timeout: 15000,
});

interface QueuedRequest {
	resolve: (token: string) => void;
	reject: (error: unknown) => void;
}

let isRefreshing = false;
let requestQueue: QueuedRequest[] = [];

const processQueue = (error: unknown, token: string | null) => {
	requestQueue.forEach(({ resolve, reject }) => {
		if(token) resolve(token);
		else reject(error);
	});

	requestQueue = [];
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
	async(error: AxiosError) => {
		const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };
		const status = error.response?.status;
		const isAuthError = status === 401 || status === 403;
		const shouldSkipRefresh = !isAuthError || originalRequest._retry || originalRequest.url?.includes('/auth/refresh') || originalRequest.url?.includes('/auth/login');

		if(shouldSkipRefresh) {
			throw error;
		}

		if(isRefreshing) {
			const newToken = await new Promise<string>((resolve, reject) => {
				requestQueue.push({ resolve, reject });
			});

			originalRequest.headers.Authorization = `Bearer ${newToken}`;
			return apiClient(originalRequest);
		}

		originalRequest._retry = true;
		isRefreshing = true;

		const refreshToken = await tokenStorage.getRefreshToken();
		if(!refreshToken) {
			isRefreshing = false;
			await tokenStorage.clearTokens();
			
			throw error;
		}

		try {
			const { data } = await axios.post(`${API_URL}/auth/refresh`, { refreshToken });
			await tokenStorage.setTokens(data.accessToken, data.refreshToken);
			processQueue(null, data.accessToken);

			originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
			return apiClient(originalRequest);
		} catch(refreshError) {
			processQueue(refreshError, null);
			
			await tokenStorage.clearTokens();
			throw refreshError;
		} finally {
			isRefreshing = false;
		}
	}
);