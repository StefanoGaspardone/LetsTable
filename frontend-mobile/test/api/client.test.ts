import axios from 'axios';
import MockAdapter from 'axios-mock-adapter';
import { router } from 'expo-router';

import { apiClient, API_URL } from '@/api/client';
import { tokenStorage } from '@/lib/token-storage';

jest.mock('@/lib/token-storage');
jest.mock('expo-router', () => ({
	router: { replace: jest.fn() },
}));

const mockedGetAccessToken = tokenStorage.getAccessToken as jest.MockedFunction<typeof tokenStorage.getAccessToken>;
const mockedGetRefreshToken = tokenStorage.getRefreshToken as jest.MockedFunction<typeof tokenStorage.getRefreshToken>;
const mockedSetTokens = tokenStorage.setTokens as jest.MockedFunction<typeof tokenStorage.setTokens>;
const mockedClearTokens = tokenStorage.clearTokens as jest.MockedFunction<typeof tokenStorage.clearTokens>;
const mockedRouterReplace = router.replace as jest.MockedFunction<typeof router.replace>;

describe('apiClient', () => {
	let clientMock: MockAdapter;
	let globalAxiosMock: MockAdapter;

	beforeEach(() => {
		jest.clearAllMocks();
		clientMock = new MockAdapter(apiClient);
		globalAxiosMock = new MockAdapter(axios);
	});

	afterEach(() => {
		clientMock.restore();
		globalAxiosMock.restore();
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
		it('rejects immediately for non-401/403 errors without attempting a refresh', async () => {
			mockedGetAccessToken.mockResolvedValue('token');
			clientMock.onGet('/broken').reply(500, { message: 'Server error' });

			await expect(apiClient.get('/broken')).rejects.toMatchObject({
				response: expect.objectContaining({ status: 500 }),
			});

			expect(mockedGetRefreshToken).not.toHaveBeenCalled();
		});
	});

	describe('response interceptor - 401 handling', () => {
		it('refreshes the token and retries the original request on 401', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');
			mockedSetTokens.mockResolvedValue(undefined);

			clientMock
				.onGet('/protected').replyOnce(401, { message: 'Unauthorized' })
				.onGet('/protected').replyOnce(200, { data: 'secret' });

			globalAxiosMock.onPost(`${API_URL}/auth/refresh`).reply(200, {
				accessToken: 'new-access-token',
				refreshToken: 'new-refresh-token',
			});

			const response = await apiClient.get('/protected');

			expect(response.data).toEqual({ data: 'secret' });
			expect(mockedSetTokens).toHaveBeenCalledWith('new-access-token', 'new-refresh-token');
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

		it('clears tokens and redirects to welcome when there is no refresh token available', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue(null);

			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });

			await expect(apiClient.get('/protected')).rejects.toMatchObject({
				response: expect.objectContaining({ status: 401 }),
			});

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(mockedRouterReplace).toHaveBeenCalledWith('/(auth)/welcome');
		});

		it('clears tokens and redirects to welcome when the refresh call itself fails', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('invalid-refresh-token');

			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });
			globalAxiosMock.onPost(`${API_URL}/auth/refresh`).reply(401, { message: 'Refresh token invalid' });

			await expect(apiClient.get('/protected')).rejects.toBeTruthy();

			expect(mockedClearTokens).toHaveBeenCalled();
			expect(mockedRouterReplace).toHaveBeenCalledWith('/(auth)/welcome');
		});

		it('does not attempt a second refresh for the same request if it fails again after a successful retry', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');
			mockedSetTokens.mockResolvedValue(undefined);

			globalAxiosMock.onPost(`${API_URL}/auth/refresh`).reply(200, {
				accessToken: 'still-bad-token',
				refreshToken: 'still-bad-refresh',
			});

			clientMock.onGet('/protected').reply(401, { message: 'Unauthorized' });

			await expect(apiClient.get('/protected')).rejects.toMatchObject({
				response: expect.objectContaining({ status: 401 }),
			});

			const refreshCalls = globalAxiosMock.history.post.filter(r => r.url === `${API_URL}/auth/refresh`);
			expect(refreshCalls).toHaveLength(1);
		});

		it('queues concurrent requests while a refresh is already in progress and resolves them all with the new token', async () => {
			mockedGetAccessToken.mockResolvedValue('expired-token');
			mockedGetRefreshToken.mockResolvedValue('valid-refresh-token');
			mockedSetTokens.mockResolvedValue(undefined);

			globalAxiosMock.onPost(`${API_URL}/auth/refresh`).reply(200, {
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

			const refreshCalls = globalAxiosMock.history.post.filter(r => r.url === `${API_URL}/auth/refresh`);
			expect(refreshCalls).toHaveLength(1);
		});
	});
});