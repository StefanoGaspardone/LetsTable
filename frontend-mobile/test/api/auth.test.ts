import { apiClient } from '@/api/client';
import { signup, activate, resendActivationOtp, login, logout, forgotPassword, resetPassword } from '@/api/auth';

jest.mock('@/api/client', () => ({
	apiClient: {
		post: jest.fn(),
	},
}));

const mockedPost = jest.mocked(apiClient.post);

describe('auth API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('signup', () => {
		it('posts to /auth/signup with the given payload and returns the response data', async () => {
			const payload = { username: 'stefano', email: 'stefano@example.com', password: 'password123' };
			const responseData = { email: 'stefano@example.com' };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await signup(payload as any);

			expect(mockedPost).toHaveBeenCalledWith('/auth/signup', payload);
			expect(result).toEqual(responseData);
		});
	});

	describe('activate', () => {
		it('posts to /auth/activate with identifier and otpCode', async () => {
			mockedPost.mockResolvedValueOnce({} as any);

			await activate('stefano@example.com', '123456');

			expect(mockedPost).toHaveBeenCalledWith('/auth/activate', {
				identifier: 'stefano@example.com',
				otpCode: '123456',
			});
		});
	});

	describe('resendActivationOtp', () => {
		it('posts to /auth/activate/resend with the identifier', async () => {
			mockedPost.mockResolvedValueOnce({} as any);

			await resendActivationOtp('stefano@example.com');

			expect(mockedPost).toHaveBeenCalledWith('/auth/activate/resend', {
				identifier: 'stefano@example.com',
			});
		});
	});

	describe('login', () => {
		it('posts to /auth/login with the given payload and returns the response data', async () => {
			const payload = { identifier: 'stefano@example.com', password: 'password123' };
			const responseData = { accessToken: 'token', refreshToken: 'refresh', user: { id: 'user-1' } };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await login(payload as any);

			expect(mockedPost).toHaveBeenCalledWith('/auth/login', payload);
			expect(result).toEqual(responseData);
		});
	});

	describe('logout', () => {
		it('posts to /auth/logout with the refresh token', async () => {
			mockedPost.mockResolvedValueOnce({} as any);

			await logout('refresh-token');

			expect(mockedPost).toHaveBeenCalledWith('/auth/logout', { refreshToken: 'refresh-token' });
		});
	});

	describe('forgotPassword', () => {
		it('posts to /auth/password/forgot with the given payload', async () => {
			const payload = { identifier: 'stefano@example.com' };
			mockedPost.mockResolvedValueOnce({} as any);

			await forgotPassword(payload as any);

			expect(mockedPost).toHaveBeenCalledWith('/auth/password/forgot', payload);
		});
	});

	describe('resetPassword', () => {
		it('posts to /auth/password/reset with the given payload', async () => {
			const payload = { identifier: 'stefano@example.com', otpCode: '123456', newPassword: 'newPassword123' };
			mockedPost.mockResolvedValueOnce({} as any);

			await resetPassword(payload as any);

			expect(mockedPost).toHaveBeenCalledWith('/auth/password/reset', payload);
		});
	});

	it('propagates errors from the API client', async () => {
		mockedPost.mockRejectedValueOnce(new Error('Network error'));

		await expect(login({ identifier: 'stefano', password: 'x' } as any)).rejects.toThrow('Network error');
	});
});