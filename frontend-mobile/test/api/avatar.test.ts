import { apiClient } from '@/api/client';
import { uploadAvatar, deleteAvatar } from '@/api/avatar';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		post: jest.fn(),
		delete: jest.fn(),
	},
}));

const mockedPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;
const mockedDelete = apiClient.delete as jest.MockedFunction<typeof apiClient.delete>;

describe('avatar API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('uploadAvatar', () => {
		it('posts a FormData payload to /users/me/avatar with multipart headers', async () => {
			const responseData = {
				id: 'file-1',
				fileName: 'avatar.jpg',
				contentType: 'image/jpeg',
				size: 12345,
			};
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await uploadAvatar('file:///path/avatar.jpg', 'avatar.jpg', 'image/jpeg');

			expect(mockedPost).toHaveBeenCalledWith(
				'/users/me/avatar',
				expect.any(FormData),
				{ headers: { 'Content-Type': 'multipart/form-data' } }
			);
			expect(result).toEqual(responseData);
		});

		it('propagates an error when the upload fails', async () => {
			mockedPost.mockRejectedValueOnce(new Error('Upload failed'));

			await expect(uploadAvatar('file:///path/avatar.jpg', 'avatar.jpg', 'image/jpeg')).rejects.toThrow(
				'Upload failed'
			);
		});
	});

	describe('deleteAvatar', () => {
		it('calls DELETE on /users/me/avatar/:fileId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await deleteAvatar('file-1');

			expect(mockedDelete).toHaveBeenCalledWith('/users/me/avatar/file-1');
		});

		it('propagates an error when the delete fails', async () => {
			mockedDelete.mockRejectedValueOnce(new Error('Delete failed'));

			await expect(deleteAvatar('file-1')).rejects.toThrow('Delete failed');
		});
	});
});