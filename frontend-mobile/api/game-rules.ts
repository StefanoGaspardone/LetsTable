import * as FileSystem from 'expo-file-system/legacy';
import { fromByteArray } from 'base64-js';

import { apiClient } from '@/api/client';

import { UploadedFile } from '@/types/file';

export const listGameRules = async (gameId: string): Promise<UploadedFile[]> => {
	const { data } = await apiClient.get<UploadedFile[]>(`/games/${gameId}/rules`);
	return data;
}

export const uploadGameRule = async (gameId: string, fileUri: string, fileName: string): Promise<UploadedFile> => {
	const formData = new FormData();
	formData.append('file', {
		uri: fileUri,
		name: fileName,
		type: 'application/pdf',
	} as any);

	try {
	const { data } = await apiClient.post<UploadedFile>(`/games/${gameId}/rules`, formData, {
	headers: { 'Content-Type': 'multipart/form-data' },
});
	return data;
} catch (error) {
	console.log('UPLOAD ERROR:', JSON.stringify(error, null, 2));
	throw error;
}
}

export const deleteGameRule = async (gameId: string, fileId: string): Promise<void> => {
	await apiClient.delete(`/games/${gameId}/rules/${fileId}`);
}

export const downloadRuleFile = async (gameId: string, fileId: string, fileName: string): Promise<string> => {
	const response = await apiClient.get(`/games/${gameId}/rules/${fileId}/download`, {
		responseType: 'arraybuffer',
	});

	const base64 = fromByteArray(new Uint8Array(response.data));
	const localUri = `${FileSystem.cacheDirectory}${fileName}`;

	await FileSystem.writeAsStringAsync(localUri, base64, {
		encoding: FileSystem.EncodingType.Base64,
	});

	return localUri;
}