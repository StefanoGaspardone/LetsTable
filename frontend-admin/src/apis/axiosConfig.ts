import axios, { type AxiosError, type InternalAxiosRequestConfig } from 'axios';

export const BASE_URL = `${import.meta.env.VITE_SERVER_URL || 'https://letstable.onrender.com'}/api/v1`;

const axiosInstance = axios.create({
	baseURL: BASE_URL,
	timeout: 10000,
	headers: {
		'Content-Type': 'application/json',
	},
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

axiosInstance.interceptors.request.use(
	config => {
		const token = localStorage.getItem('accessToken');
		if(token) config.headers.Authorization = `Bearer ${token}`;

		return config;
	},
	error => Promise.reject(error)
);

axiosInstance.interceptors.response.use(
	response => response,
	async (error: AxiosError) => {
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
			
			return axiosInstance(originalRequest);
		}

		originalRequest._retry = true;
		isRefreshing = true;

		const refreshToken = localStorage.getItem('refreshToken');
		if(!refreshToken) {
			isRefreshing = false;
			
			clearAuthTokens();
			throw error;
		}

		try {
			const { data } = await axios.post(`${BASE_URL}/auth/refresh`, { refreshToken });

			setAuthTokens(data.accessToken, data.refreshToken);
			processQueue(null, data.accessToken);

			originalRequest.headers.Authorization = `Bearer ${data.accessToken}`;
			return axiosInstance(originalRequest);
		} catch(refreshError) {
			processQueue(refreshError, null);
			
			clearAuthTokens();
			throw refreshError;
		} finally {
			isRefreshing = false;
		}
	}
);

export const setAuthTokens = (accessToken: string, refreshToken: string) => {
	localStorage.setItem('accessToken', accessToken);
	localStorage.setItem('refreshToken', refreshToken);
}

export const clearAuthTokens = () => {
	localStorage.removeItem('accessToken');
	localStorage.removeItem('refreshToken');
}

export const getAccessToken = () => {
	return localStorage.getItem('accessToken');
}

export default axiosInstance;