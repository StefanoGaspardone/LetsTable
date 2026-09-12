import * as FileSystem from 'expo-file-system/legacy';

import { apiClient } from '@/api/client';
import { listGameRules, uploadGameRule, deleteGameRule, downloadRuleFile } from '@/api/game-rules';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
		post: jest.fn(),
		delete: jest.fn(),
	},
}));

jest.mock('expo-file-system/legacy', () => ({
	cacheDirectory: 'file:///cache/',
	writeAsStringAsync: jest.fn(),
	EncodingType: { Base64: 'base64' },
}));

const mockedGet = apiClient.get as jest.MockedFunction<typeof apiClient.get>;
const mockedPost = apiClient.post as jest.MockedFunction<typeof apiClient.post>;
const mockedDelete = apiClient.delete as jest.MockedFunction<typeof apiClient.delete>;
const mockedWriteAsStringAsync = FileSystem.writeAsStringAsync as jest.MockedFunction<typeof FileSystem.writeAsStringAsync>;

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

			const result = await uploadGameRule('game-1', 'file:///local/rules.pdf', 'rules.pdf');

			expect(mockedPost).toHaveBeenCalledWith(
				'/games/game-1/rules',
				expect.any(FormData),
				{ headers: { 'Content-Type': 'multipart/form-data' } }
			);
			expect(result).toEqual(responseData);
		});

		it('logs and rethrows the error when the upload fails', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
			const error = new Error('Upload failed');
			mockedPost.mockRejectedValueOnce(error);

			await expect(uploadGameRule('game-1', 'file:///local/rules.pdf', 'rules.pdf')).rejects.toThrow('Upload failed');

			expect(consoleLog).toHaveBeenCalled();
			consoleLog.mockRestore();
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
		it('requests the file as an arraybuffer', async () => {
			const buffer = new Uint8Array([72, 101, 108, 108, 111]).buffer;
			mockedGet.mockResolvedValueOnce({ data: buffer } as any);
			mockedWriteAsStringAsync.mockResolvedValueOnce(undefined);

			await downloadRuleFile('game-1', 'file-1', 'rules.pdf');

			expect(mockedGet).toHaveBeenCalledWith('/games/game-1/rules/file-1/download', {
				responseType: 'arraybuffer',
			});
		});

		it('writes the decoded file to the correct local path', async () => {
			const buffer = new Uint8Array([72, 101, 108, 108, 111]).buffer;
			mockedGet.mockResolvedValueOnce({ data: buffer } as any);
			mockedWriteAsStringAsync.mockResolvedValueOnce(undefined);

			const localUri = await downloadRuleFile('game-1', 'file-1', 'rules.pdf');

			expect(localUri).toBe('file:///cache/rules.pdf');
			expect(mockedWriteAsStringAsync).toHaveBeenCalledWith(
				'file:///cache/rules.pdf',
				expect.any(String),
				{ encoding: 'base64' }
			);
		});

		it('encodes the binary response as base64 before writing', async () => {
			const buffer = new Uint8Array([72, 101, 108, 108, 111]).buffer;
			mockedGet.mockResolvedValueOnce({ data: buffer } as any);
			mockedWriteAsStringAsync.mockResolvedValueOnce(undefined);

			await downloadRuleFile('game-1', 'file-1', 'rules.pdf');

			const writtenBase64 = mockedWriteAsStringAsync.mock.calls[0][1];
			expect(writtenBase64).toBe('SGVsbG8=');
		});
	});
});