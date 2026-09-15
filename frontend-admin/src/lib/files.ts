import { BASE_URL } from '@/apis/axiosConfig';

export const getRuleFileDownloadUrl = (gameId: string, fileId: string): string =>
    `${BASE_URL}/games/${gameId}/rules/${fileId}/download`;


export const getAvatarUrl = (avatarId: string | null, username: string): string => {
	if(avatarId) return `${BASE_URL}/avatars/${avatarId}`;
	return `https://api.dicebear.com/9.x/initials/svg?seed=${username}`;
}