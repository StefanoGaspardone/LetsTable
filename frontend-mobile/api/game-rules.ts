import * as FileSystem from 'expo-file-system/legacy';

import { apiClient, refreshSession } from '@/api/client';

import { tokenStorage } from '@/lib/token-storage';
import { getRuleFileDownloadUrl } from '@/lib/file';

import { UploadedFile } from '@/types/file';

const RULES_CACHE_DIR = `${FileSystem.cacheDirectory}rules/`;

export const listGameRules = async (gameId: string): Promise<UploadedFile[]> => {
	const { data } = await apiClient.get<UploadedFile[]>(`/games/${gameId}/rules`);
	return data;
}

export const uploadGameRule = async (gameId: string, fileUri: string, fileName: string, mimeType: string): Promise<UploadedFile> => {
	const formData = new FormData();
	formData.append('file', {
		uri: fileUri,
		name: fileName,
		type: mimeType,
	} as any);

	const { data } = await apiClient.post<UploadedFile>(`/games/${gameId}/rules`, formData, {
		headers: { 'Content-Type': 'multipart/form-data' },
	});

	return data;
}

export const deleteGameRule = async (gameId: string, fileId: string): Promise<void> => {
	await apiClient.delete(`/games/${gameId}/rules/${fileId}`);
}

const downloadWithAuth = async (url: string, localUri: string) => {
	const accessToken = await tokenStorage.getAccessToken();
	const headers: Record<string, string> = accessToken ? { Authorization: `Bearer ${accessToken}` } : {};

	return FileSystem.downloadAsync(url, localUri, { headers });
}

export const downloadRuleFile = async (gameId: string, fileId: string, fileName: string): Promise<string> => {
	const directory = `${RULES_CACHE_DIR}${fileId}/`;
	const localUri = `${directory}${fileName}`;

	const cached = await FileSystem.getInfoAsync(localUri);
	if(cached.exists) return localUri;

	await FileSystem.makeDirectoryAsync(directory, { intermediates: true });

	const url = getRuleFileDownloadUrl(gameId, fileId);
	let result = await downloadWithAuth(url, localUri);

	if(result.status === 401 || result.status === 403) {
		await refreshSession();
		result = await downloadWithAuth(url, localUri);
	}

	if(result.status < 200 || result.status >= 300) {
		await FileSystem.deleteAsync(localUri, { idempotent: true });
		throw new Error(`Rule file download failed with status ${result.status}`);
	}

	return localUri;
}