import { AxiosError } from 'axios';

import { apiClient } from '@/api/client';
import { getLatestAppVersion } from '@/api/app-version';

jest.mock('@/api/client', () => ({
	apiClient: {
		get: jest.fn(),
	},
}));

const mockedGet = jest.mocked(apiClient.get);

const httpError = (status: number) =>
	new AxiosError('Request failed', 'ERR_BAD_REQUEST', undefined, undefined, { status, data: {}, statusText: '', headers: {}, config: {} } as any);

describe('getLatestAppVersion', () => {
	beforeEach(() => {
		jest.resetAllMocks();
	});

	it('calls GET /app/version and returns the latest version info', async () => {
		const info = { latestVersion: '2.3.0', minSupportedVersion: '2.3.0', downloadUrl: 'https://example.com/app.apk', releaseNotes: null, publishedAt: '2026-11-03T14:27:22Z' };
		mockedGet.mockResolvedValueOnce({ data: info } as any);

		const result = await getLatestAppVersion();

		expect(mockedGet).toHaveBeenCalledWith('/app/version');
		expect(result).toEqual(info);
	});

	it('returns null when no version has been published yet', async () => {
		mockedGet.mockRejectedValueOnce(httpError(404));

		await expect(getLatestAppVersion()).resolves.toBeNull();
	});

	it('rethrows other HTTP errors', async () => {
		const error = httpError(500);
		mockedGet.mockRejectedValueOnce(error);

		await expect(getLatestAppVersion()).rejects.toBe(error);
	});

	it('rethrows non-HTTP errors', async () => {
		mockedGet.mockRejectedValueOnce(new Error('Network down'));

		await expect(getLatestAppVersion()).rejects.toThrow('Network down');
	});
});