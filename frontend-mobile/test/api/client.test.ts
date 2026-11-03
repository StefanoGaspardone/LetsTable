import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { router } from 'expo-router';

import { apiClient, API_URL, refreshSession, setNetworkErrorHandler, setSessionExpiredHandler } from '@/api/client';
import { tokenStorage } from '@/lib/token-storage';

jest.mock('@/lib/token-storage');
jest.mock('expo-router', () => ({
	router: { replace: jest.fn() },
}));

const mockedGetAccessToken = jest.mocked(tokenStorage.getAccessToken);
const mockedGetRefreshToken = jest.mocked(tokenStorage.getRefreshToken);
const mockedSetTokens = jest.mocked(tokenStorage.setTokens);
const mockedClearTokens = jest.mocked(tokenStorage.clearTokens);
const mockedRouterReplace = jest.mocked(router.replace);

const REFRESH_URL = `${API_URL}/auth/refresh`;

describe('apiClient', () => {
	let clientMock: MockAdapter;
	let globalAxiosMock: MockAdapter;

	beforeEach(() => {
		jest.clearAllMocks();
		clientMock = new MockAdapter(apiClient);
		globalAxiosMock = new MockAdapter(axios);

		mockedSetTokens.mockImplementation(async (accessToken) => {
			mockedGetAccessToken.mockResolvedValue(accessToken);
		});
		mockedClearTokens.mockResolvedValue(undefined);
	});

	afterEach(() => {
		clientMock.restore();
		globalAxiosMock.restore();
		setSessionExpiredHandler(null);
		setNetworkErrorHandler(null);
	});

	describe('request interceptor', () => {
		it('adds the Authorization header when an access token exists', async () => {
			mockedGetAccessToken.mockResolvedValue('my-access-token');
			clientMock.onGet('/ping').reply(200, { ok: true });

			await apiClient.get('/ping');

			expect(clientMock.history.get[0].headers?.Authorization).toBe('Bearer my-access-token');
		});

		it('does not add the Authorization header when there is no access token', async () => {
			mockedGetAccessToken.mockResolvedValue(null);
			clientMock.onGet('/ping').reply(200, { ok: true });

			await apiClient.get('/ping');

			expect(clientMock.history.get[0].headers?.Authorization).toBeUndefined();
		});
	});

	describe('response interceptor - non-auth errors', () => {
		it.each([400, 404, 500])('rejects immediately for status %i without attempting a refresh', async (status) => {
			mockedGetAccessToken.mockResolvedValue('token');
			clientMock.onGet('/broken').reply(status, { message: 'Error' });

			await expect(apiClient.get('/broken')).rejects.toMatchObject({
				response: expect.objectContaining({ status }),
			});

			expect(mockedGetRefreshToken).not.toHaveBeenCalled();
		});
	});

	describe('response interceptor - network errors', () => {
		it('notifies the network error handler when the request gets no response', async () => {
			const handler = jest.fn();
			setNetworkErrorHandler(handler);
			mockedGetAccessToken.mockResolvedValue('token');
			clientMock.onGet('/offline').networkError();

			await expect(apiClient.get('/offline')).rejects.toBeTruthy();

			expect(handler).toHaveBeenCalledTimes(1);
			expect(mockedGetRefreshToken).not.toHaveBeenCalled();
		});

		it('does not notify the network error handler when the request is canceled', async () => {
			const handler = jest.fn();
			setNetworkErrorHandler(handler);
			mockedGetAccessToken.mockResolvedValue('token');
			clientMock.onGet('/cancel').reply(200, {});

			const controller = new AbortController();
			controller.abort();

			await expect(apiClient.get('/cancel', { signal: controller.signal })).rejects.toMatchObject({ code: 'ERR_CANCELED' });

			expect(handler).not.toHaveBeenCalled();
		});

		it('still rejects when no network error handler is registered', async () => {
			mockedGetAccessToken.mockResolvedValue('token');
			clientMock.onGet('/offline').networkError();

			await expect(apiClient.get('/offline')).rejects.toBeTruthy();
		});
	});

	describe('response interceptor - auth errors', () => {
		it.each([401, 403])('refreshes the token and retries the original request with the new token on %i', async (status) => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');

			clientMock
				.onGet('/protected').replyOnce(status, { message: 'Unauthorized' })
				.onGet('/protected').replyOnce(200, { data: 'secret' });

			globalAxiosMock.onPost(REFRESH_URL).reply(200, {
				accessToken: 'new-access-token',
				refreshToken: 'new-refresh-token',
			});

			const response = await apiClient.get('/protected');

			expect(response.data).toEqual({ data: 'secret' });
			expect(mockedSetTokens).toHaveBeenCalledWith('new-access-token', 'new-refresh-token');
			expect(JSON.parse(globalAxiosMock.history.post[0].data)).toEqual({ refreshToken: 'valid-refresh-token' });
			expect(clientMock.history.get[1].headers?.Authorization).toBe('Bearer new-access-token');
		});

		it('does not attempt to refresh when the failing request is /auth/refresh itself', async () => {
			mockedGetAccessToken.mockResolvedValue('token');
			clientMock.onPost('/auth/refresh').reply(401, { message: 'Unauthorized' });

			await expect(apiClient.post('/auth/refresh', {})).rejects.toMatchObject({
				response: expect.objectContaining({ status: 401 }),
			});

			expect(mockedGetRefreshToken).not.toHaveBeenCalled();
		});

		it('does not attempt to refresh when the failing request is /auth/login', async () => {
			mockedGetAccessToken.mockResolvedValue(null);
			clientMock.onPost('/auth/login').reply(401, { message: 'Invalid credentials' });

			await expect(apiClient.post('/auth/login', {})).rejects.toMatchObject({
				response: expect.objectContaining({ status: 401 }),
			});

			expect(mockedGetRefreshToken).not.toHaveBeenCalled();
		});

		it('expires the session and rejects when there is no refresh token available', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue(null);
			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });

			await expect(apiClient.get('/protected')).rejects.toThrow('Missing refresh token');

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(mockedRouterReplace).toHaveBeenCalledWith('/(auth)/welcome');
			expect(globalAxiosMock.history.post).toHaveLength(0);
		});

		it('calls the session expired handler instead of redirecting when one is registered', async () => {
			const sessionExpiredHandler = jest.fn();
			setSessionExpiredHandler(sessionExpiredHandler);
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue(null);
			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });

			await expect(apiClient.get('/protected')).rejects.toThrow('Missing refresh token');

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(sessionExpiredHandler).toHaveBeenCalledTimes(1);
			expect(mockedRouterReplace).not.toHaveBeenCalled();
		});

		it('expires the session when the refresh is rejected by the server', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('invalid-refresh-token');

			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });
			globalAxiosMock.onPost(REFRESH_URL).reply(401, { message: 'Refresh token invalid' });

			await expect(apiClient.get('/protected')).rejects.toMatchObject({
				response: expect.objectContaining({ status: 401 }),
			});

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(mockedRouterReplace).toHaveBeenCalledWith('/(auth)/welcome');
		});

		it('keeps the session when the refresh fails with a server error', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');

			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });
			globalAxiosMock.onPost(REFRESH_URL).reply(503, { message: 'Unavailable' });

			await expect(apiClient.get('/protected')).rejects.toMatchObject({
				response: expect.objectContaining({ status: 503 }),
			});

			expect(mockedClearTokens).not.toHaveBeenCalled();
			expect(mockedRouterReplace).not.toHaveBeenCalled();
		});

		it('keeps the session when the refresh fails because of the network', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');

			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });
			globalAxiosMock.onPost(REFRESH_URL).networkError();

			await expect(apiClient.get('/protected')).rejects.toBeTruthy();

			expect(mockedClearTokens).not.toHaveBeenCalled();
			expect(mockedRouterReplace).not.toHaveBeenCalled();
		});

		it('does not attempt a second refresh for the same request if it fails again after a successful retry', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');

			globalAxiosMock.onPost(REFRESH_URL).reply(200, {
				accessToken: 'still-bad-token',
				refreshToken: 'still-bad-refresh',
			});

			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });

			await expect(apiClient.get('/protected')).rejects.toMatchObject({
				response: expect.objectContaining({ status: 401 }),
			});

			const refreshCalls = globalAxiosMock.history.post.filter(request => request.url === REFRESH_URL);
			expect(refreshCalls).toHaveLength(1);
		});

		it('shares a single refresh between concurrent requests and resolves them all with the new token', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');

			globalAxiosMock.onPost(REFRESH_URL).reply(200, {
				accessToken: 'new-access-token',
				refreshToken: 'new-refresh-token',
			});

			clientMock
				.onGet('/first').replyOnce(401, { message: 'Unauthorized' })
				.onGet('/first').replyOnce(200, { data: 'first-ok' })
				.onGet('/second').replyOnce(401, { message: 'Unauthorized' })
				.onGet('/second').replyOnce(200, { data: 'second-ok' });

			const [first, second] = await Promise.all([
				apiClient.get('/first'),
				apiClient.get('/second'),
			]);

			expect(first.data).toEqual({ data: 'first-ok' });
			expect(second.data).toEqual({ data: 'second-ok' });

			const refreshCalls = globalAxiosMock.history.post.filter(request => request.url === REFRESH_URL);
			expect(refreshCalls).toHaveLength(1);
		});
	});

	describe('refreshSession', () => {
		it('returns the same pending refresh to concurrent callers', async () => {
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');
			globalAxiosMock.onPost(REFRESH_URL).reply(200, {
				accessToken: 'new-access-token',
				refreshToken: 'new-refresh-token',
			});

			const [first, second] = await Promise.all([refreshSession(), refreshSession()]);

			expect(first).toBe('new-access-token');
			expect(second).toBe('new-access-token');
			expect(globalAxiosMock.history.post).toHaveLength(1);
		});

		it('starts a new refresh once the previous one has completed', async () => {
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');
			globalAxiosMock.onPost(REFRESH_URL).reply(200, {
				accessToken: 'new-access-token',
				refreshToken: 'new-refresh-token',
			});

			await refreshSession();
			await refreshSession();

			expect(globalAxiosMock.history.post).toHaveLength(2);
		});

		it('allows a new refresh after a failed one', async () => {
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');
			globalAxiosMock
				.onPost(REFRESH_URL).replyOnce(503)
				.onPost(REFRESH_URL).replyOnce(200, { accessToken: 'new-access-token', refreshToken: 'new-refresh-token' });

			await expect(refreshSession()).rejects.toBeTruthy();
			await expect(refreshSession()).resolves.toBe('new-access-token');
		});
	});
});