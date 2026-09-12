import { API_URL } from '@/api/client';

export const getRuleFileDownloadUrl = (gameId: string, fileId: string): string =>
    `${API_URL}/games/${gameId}/rules/${fileId}/download`;


export const getAvatarUrl = (avatarId: string | null, username: string): string => {
	if(avatarId) return `${API_URL}/avatars/${avatarId}`;
	return `https://api.dicebear.com/9.x/initials/svg?seed=${username}`;
}