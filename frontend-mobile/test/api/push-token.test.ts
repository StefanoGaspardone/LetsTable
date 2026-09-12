import { apiClient } from '@/api/client';
import { registerPushToken, unregisterPushToken } from '@/api/push-token';

jest.mock('@/api/client', () => ({
	apiClient: {
		post: jest.fn(),
		delete: jest.fn(),
	},
}));

const mockedPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;
const mockedDelete = apiClient.delete as jest.MockedFunction<typeof apiClient.delete>;

describe('push-token API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('registerPushToken', () => {
		it('posts to /push-tokens with the given payload', async () => {
			mockedPost.mockResolvedValueOnce({} as any);

			const payload = { token: 'ExponentPushToken[abc123]', deviceName: 'Pixel 8' };
			await registerPushToken(payload);

			expect(mockedPost).toHaveBeenCalledWith('/push-tokens', payload);
		});

		it('posts with a null deviceName when not provided', async () => {
			mockedPost.mockResolvedValueOnce({} as any);

			const payload = { token: 'ExponentPushToken[abc123]', deviceName: null };
			await registerPushToken(payload);

			expect(mockedPost).toHaveBeenCalledWith('/push-tokens', payload);
		});
	});

	describe('unregisterPushToken', () => {
		it('calls DELETE on /push-tokens with the token as a query param', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await unregisterPushToken('ExponentPushToken[abc123]');

			expect(mockedDelete).toHaveBeenCalledWith('/push-tokens', {
				params: { token: 'ExponentPushToken[abc123]' },
			});
		});
	});
});