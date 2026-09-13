import { getRuleFileDownloadUrl, getFileIconName, getFileIconColor, getAvatarUrl } from '@/lib/file';

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


describe('getFileIconName', () => {
	it('returns the PDF icon for application/pdf', () => {
		expect(getFileIconName('application/pdf')).toBe('file-pdf-box');
	});

	it('returns the Word icon for application/msword', () => {
		expect(getFileIconName('application/msword')).toBe('file-word-box');
	});

	it('returns the Word icon for the modern docx MIME type', () => {
		expect(getFileIconName('application/vnd.openxmlformats-officedocument.wordprocessingml.document')).toBe('file-word-box');
	});

	it('returns the Excel icon for application/vnd.ms-excel', () => {
		expect(getFileIconName('application/vnd.ms-excel')).toBe('file-excel-box');
	});

	it('returns the Excel icon for the modern xlsx MIME type', () => {
		expect(getFileIconName('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')).toBe('file-excel-box');
	});

	it('returns the PowerPoint icon for application/vnd.ms-powerpoint', () => {
		expect(getFileIconName('application/vnd.ms-powerpoint')).toBe('file-powerpoint-box');
	});

	it('returns the PowerPoint icon for the modern pptx MIME type', () => {
		expect(getFileIconName('application/vnd.openxmlformats-officedocument.presentationml.presentation')).toBe('file-powerpoint-box');
	});

	it('returns the image icon for any image content type', () => {
		expect(getFileIconName('image/png')).toBe('file-image');
		expect(getFileIconName('image/jpeg')).toBe('file-image');
		expect(getFileIconName('image/webp')).toBe('file-image');
	});

	it('returns the video icon for any video content type', () => {
		expect(getFileIconName('video/mp4')).toBe('file-video');
		expect(getFileIconName('video/quicktime')).toBe('file-video');
	});

	it('returns the generic document icon as a fallback for unknown types', () => {
		expect(getFileIconName('application/zip')).toBe('file-document-outline');
		expect(getFileIconName('text/plain')).toBe('file-document-outline');
	});
});

describe('getFileIconColor', () => {
	it('returns the red color for application/pdf', () => {
		expect(getFileIconColor('application/pdf')).toBe('#EF4444');
	});

	it('returns the blue color for Word document types', () => {
		expect(getFileIconColor('application/msword')).toBe('#2563EB');
		expect(getFileIconColor('application/vnd.openxmlformats-officedocument.wordprocessingml.document')).toBe('#2563EB');
	});

	it('returns the green color for Excel document types', () => {
		expect(getFileIconColor('application/vnd.ms-excel')).toBe('#16A34A');
		expect(getFileIconColor('application/vnd.openxmlformats-officedocument.spreadsheetml.sheet')).toBe('#16A34A');
	});

	it('returns the orange color for PowerPoint document types', () => {
		expect(getFileIconColor('application/vnd.ms-powerpoint')).toBe('#EA580C');
		expect(getFileIconColor('application/vnd.openxmlformats-officedocument.presentationml.presentation')).toBe('#EA580C');
	});

	it('returns the purple color for any image content type', () => {
		expect(getFileIconColor('image/png')).toBe('#8B5CF6');
		expect(getFileIconColor('image/jpeg')).toBe('#8B5CF6');
	});

	it('returns the cyan color for any video content type', () => {
		expect(getFileIconColor('video/mp4')).toBe('#06B6D4');
	});

	it('returns the gray color as a fallback for unknown types', () => {
		expect(getFileIconColor('application/zip')).toBe('#6B7280');
		expect(getFileIconColor('text/plain')).toBe('#6B7280');
	});
});

describe('getAvatarUrl', () => {
	it('builds the server URL when avatarId is provided', () => {
		const result = getAvatarUrl('file-123', 'stefano');

		expect(result).toBe(`${API_URL}/avatars/file-123`);
	});

	it('falls back to Dicebear when avatarId is null', () => {
		const result = getAvatarUrl(null, 'stefano');

		expect(result).toBe('https://api.dicebear.com/9.x/initials/svg?seed=stefano');
	});

	it('uses the username as the Dicebear seed', () => {
		const result = getAvatarUrl(null, 'marco');

		expect(result).toContain('seed=marco');
	});

	it('does not use the username when avatarId is provided', () => {
		const result = getAvatarUrl('file-456', 'anna');

		expect(result).not.toContain('anna');
		expect(result).not.toContain('dicebear');
	});

	it('works with UUID-shaped avatarId', () => {
		const avatarId = '3fa85f64-5717-4562-b3fc-2c963f66afa6';
		const result = getAvatarUrl(avatarId, 'stefano');

		expect(result).toBe(`${API_URL}/avatars/${avatarId}`);
	});
});