import { getRuleFileDownloadUrl } from '@/lib/file';

import { API_URL } from '@/api/client';

describe('getRuleFileDownloadUrl', () => {
	it('builds the correct download URL from gameId and fileId', () => {
		const result = getRuleFileDownloadUrl('game-123', 'file-456');

		expect(result).toBe(`${API_URL}/games/game-123/rules/file-456/download`);
	});

	it('includes the API_URL as the base of the returned URL', () => {
		const result = getRuleFileDownloadUrl('game-1', 'file-1');

		expect(result.startsWith(API_URL)).toBe(true);
	});

	it('places gameId and fileId in the correct positions within the path', () => {
		const result = getRuleFileDownloadUrl('abc', 'xyz');

		expect(result).toContain('/games/abc/');
		expect(result).toContain('/rules/xyz/download');
	});

	it('works with UUID-shaped ids', () => {
		const gameId = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
		const fileId = '7c9e6679-7425-40de-944b-e07fc1f90ae7';

		const result = getRuleFileDownloadUrl(gameId, fileId);

		expect(result).toBe(`${API_URL}/games/${gameId}/rules/${fileId}/download`);
	});
});