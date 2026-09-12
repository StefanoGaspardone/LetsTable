import { apiClient } from '@/api/client';

export interface UploadedAvatarResponse {
	id: string;
	fileName: string;
	contentType: string;
	size: number;
}

export const uploadAvatar = async (fileUri: string, fileName: string, mimeType: string): Promise<UploadedAvatarResponse> => {
	const formData = new FormData();
	formData.append('file', {
		uri: fileUri,
		name: fileName,
		type: mimeType,
	} as any);

	const { data } = await apiClient.post<UploadedAvatarResponse>('/users/me/avatar', formData, {
		headers: { 'Content-Type': 'multipart/form-data' },
	});

	return data;
}

export const deleteAvatar = async (fileId: string): Promise<void> => {
	await apiClient.delete(`/users/me/avatar/${fileId}`);
}