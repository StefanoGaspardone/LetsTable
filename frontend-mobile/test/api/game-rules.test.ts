import * as FileSystem from 'expo-file-system/legacy';

import { apiClient, refreshSession } from '@/api/client';
import { listGameRules, uploadGameRule, deleteGameRule, downloadRuleFile } from '@/api/game-rules';

import { tokenStorage } from '@/lib/token-storage';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		post: jest.fn(),
		delete: jest.fn(),
	},
	refreshSession: jest.fn(),
}));

jest.mock('@/lib/token-storage', () => ({
	tokenStorage: {
		getAccessToken: jest.fn(),
	},
}));

jest.mock('@/lib/file', () => ({
	getRuleFileDownloadUrl: (gameId: string, fileId: string) => `https://api.test/games/${gameId}/rules/${fileId}/download`,
}));

jest.mock('expo-file-system/legacy', () => ({
	cacheDirectory: 'file:///cache/',
	getInfoAsync: jest.fn(),
	makeDirectoryAsync: jest.fn(),
	downloadAsync: jest.fn(),
	deleteAsync: jest.fn(),
}));

const mockedGet = jest.mocked(apiClient.get);
const mockedPost = jest.mocked(apiClient.post);
const mockedDelete = jest.mocked(apiClient.delete);
const mockedRefreshSession = jest.mocked(refreshSession);
const mockedGetAccessToken = jest.mocked(tokenStorage.getAccessToken);
const mockedGetInfoAsync = jest.mocked(FileSystem.getInfoAsync);
const mockedMakeDirectoryAsync = jest.mocked(FileSystem.makeDirectoryAsync);
const mockedDownloadAsync = jest.mocked(FileSystem.downloadAsync);
const mockedDeleteAsync = jest.mocked(FileSystem.deleteAsync);

const DOWNLOAD_URL = 'https://api.test/games/game-1/rules/file-1/download';
const LOCAL_DIRECTORY = 'file:///cache/rules/file-1/';
const LOCAL_URI = `${LOCAL_DIRECTORY}rules.pdf`;

const downloadResult = (status: number) => ({ status, uri: LOCAL_URI, headers: {}, md5: undefined, mimeType: null }) as any;

describe('game-rules API', () => {
	beforeEach(() => {
		jest.clearAllMocks();
	});

	describe('listGameRules', () => {
		it('calls GET /games/:gameId/rules and returns the response data', async () => {
			const responseData = [{ id: 'file-1', fileName: 'rules.pdf' }];
			mockedGet.mockResolvedValueOnce({ data: responseData } as any);

			const result = await listGameRules('game-1');

			expect(mockedGet).toHaveBeenCalledWith('/games/game-1/rules');
			expect(result).toEqual(responseData);
		});
	});

	describe('uploadGameRule', () => {
		it('posts a FormData payload with multipart headers and returns the response data', async () => {
			const responseData = { id: 'file-1', fileName: 'rules.pdf' };
			mockedPost.mockResolvedValueOnce({ data: responseData } as any);

			const result = await uploadGameRule('game-1', 'file:///local/rules.pdf', 'rules.pdf', 'application/pdf');

			expect(mockedPost).toHaveBeenCalledWith(
				'/games/game-1/rules',
				expect.any(FormData),
				{ headers: { 'Content-Type': 'multipart/form-data' } }
			);
			expect(result).toEqual(responseData);
		});

		it('rethrows the error when the upload fails', async () => {
			mockedPost.mockRejectedValueOnce(new Error('Upload failed'));

			await expect(uploadGameRule('game-1', 'file:///local/rules.pdf', 'rules.pdf', 'application/pdf')).rejects.toThrow('Upload failed');
		});
	});

	describe('deleteGameRule', () => {
		it('calls DELETE on /games/:gameId/rules/:fileId', async () => {
			mockedDelete.mockResolvedValueOnce({} as any);

			await deleteGameRule('game-1', 'file-1');

			expect(mockedDelete).toHaveBeenCalledWith('/games/game-1/rules/file-1');
		});
	});

	describe('downloadRuleFile', () => {
		beforeEach(() => {
			mockedGetInfoAsync.mockResolvedValue({ exists: false } as any);
			mockedGetAccessToken.mockResolvedValue('access-token');
			mockedDownloadAsync.mockResolvedValue(downloadResult(200));
		});

		it('returns the cached file without downloading it again', async () => {
			mockedGetInfoAsync.mockResolvedValueOnce({ exists: true } as any);

			const result = await downloadRuleFile('game-1', 'file-1', 'rules.pdf');

			expect(result).toBe(LOCAL_URI);
			expect(mockedGetInfoAsync).toHaveBeenCalledWith(LOCAL_URI);
			expect(mockedMakeDirectoryAsync).not.toHaveBeenCalled();
			expect(mockedDownloadAsync).not.toHaveBeenCalled();
		});

		it('creates the cache directory and downloads the file with the access token', async () => {
			const result = await downloadRuleFile('game-1', 'file-1', 'rules.pdf');

			expect(result).toBe(LOCAL_URI);
			expect(mockedMakeDirectoryAsync).toHaveBeenCalledWith(LOCAL_DIRECTORY, { intermediates: true });
			expect(mockedDownloadAsync).toHaveBeenCalledWith(DOWNLOAD_URL, LOCAL_URI, {
				headers: { Authorization: 'Bearer access-token' },
			});
		});

		it('downloads without an Authorization header when there is no access token', async () => {
			mockedGetAccessToken.mockResolvedValueOnce(null);

			await downloadRuleFile('game-1', 'file-1', 'rules.pdf');

			expect(mockedDownloadAsync).toHaveBeenCalledWith(DOWNLOAD_URL, LOCAL_URI, { headers: {} });
		});

		it.each([401, 403])('refreshes the session and retries once when the server responds %i', async (status) => {
			mockedGetAccessToken
				.mockResolvedValueOnce('expired-token')
				.mockResolvedValueOnce('fresh-token');
			mockedDownloadAsync
				.mockResolvedValueOnce(downloadResult(status))
				.mockResolvedValueOnce(downloadResult(200));
			mockedRefreshSession.mockResolvedValueOnce(undefined as any);

			const result = await downloadRuleFile('game-1', 'file-1', 'rules.pdf');

			expect(result).toBe(LOCAL_URI);
			expect(mockedRefreshSession).toHaveBeenCalledTimes(1);
			expect(mockedDownloadAsync).toHaveBeenCalledTimes(2);
			expect(mockedDownloadAsync).toHaveBeenLastCalledWith(DOWNLOAD_URL, LOCAL_URI, {
				headers: { Authorization: 'Bearer fresh-token' },
			});
			expect(mockedDeleteAsync).not.toHaveBeenCalled();
		});

		it('deletes the partial file and throws when the download fails', async () => {
			mockedDownloadAsync.mockResolvedValueOnce(downloadResult(500));

			await expect(downloadRuleFile('game-1', 'file-1', 'rules.pdf')).rejects.toThrow('Rule file download failed with status 500');

			expect(mockedRefreshSession).not.toHaveBeenCalled();
			expect(mockedDeleteAsync).toHaveBeenCalledWith(LOCAL_URI, { idempotent: true });
		});

		it('throws when the retry after refreshing the session still fails', async () => {
			mockedDownloadAsync
				.mockResolvedValueOnce(downloadResult(401))
				.mockResolvedValueOnce(downloadResult(401));
			mockedRefreshSession.mockResolvedValueOnce(undefined as any);

			await expect(downloadRuleFile('game-1', 'file-1', 'rules.pdf')).rejects.toThrow('Rule file download failed with status 401');

			expect(mockedRefreshSession).toHaveBeenCalledTimes(1);
			expect(mockedDeleteAsync).toHaveBeenCalledWith(LOCAL_URI, { idempotent: true });
		});

		it('propagates the error when refreshing the session fails', async () => {
			mockedDownloadAsync.mockResolvedValueOnce(downloadResult(401));
			mockedRefreshSession.mockRejectedValueOnce(new Error('Session expired'));

			await expect(downloadRuleFile('game-1', 'file-1', 'rules.pdf')).rejects.toThrow('Session expired');

			expect(mockedDownloadAsync).toHaveBeenCalledTimes(1);
		});
	});
});