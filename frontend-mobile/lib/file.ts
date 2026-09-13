import { MaterialCommunityIcons } from '@expo/vector-icons';

import { API_URL } from '@/api/client';

export const getRuleFileDownloadUrl = (gameId: string, fileId: string): string =>
    `${API_URL}/games/${gameId}/rules/${fileId}/download`;


export const getAvatarUrl = (avatarId: string | null, username: string): string => {
	if(avatarId) return `${API_URL}/avatars/${avatarId}`;
	return `https://api.dicebear.com/9.x/initials/svg?seed=${username}`;
}

export const getFileIconName = (contentType: string): keyof typeof MaterialCommunityIcons.glyphMap => {
	if(contentType === 'application/pdf') return 'file-pdf-box';
	if(contentType === 'application/msword' || contentType.includes('wordprocessingml')) return 'file-word-box';
	if(contentType === 'application/vnd.ms-excel' || contentType.includes('spreadsheetml')) return 'file-excel-box';
	if(contentType === 'application/vnd.ms-powerpoint' || contentType.includes('presentationml')) return 'file-powerpoint-box';
	if(contentType.startsWith('image/')) return 'file-image';
	if(contentType.startsWith('video/')) return 'file-video';
	return 'file-document-outline';
}

export const getFileIconColor = (contentType: string): string => {
	if(contentType === 'application/pdf') return '#EF4444';
	if(contentType === 'application/msword' || contentType.includes('wordprocessingml')) return '#2563EB';
	if(contentType === 'application/vnd.ms-excel' || contentType.includes('spreadsheetml')) return '#16A34A';
	if(contentType === 'application/vnd.ms-powerpoint' || contentType.includes('presentationml')) return '#EA580C';
	if(contentType.startsWith('image/')) return '#8B5CF6';
	if(contentType.startsWith('video/')) return '#06B6D4';
	return '#6B7280';
}