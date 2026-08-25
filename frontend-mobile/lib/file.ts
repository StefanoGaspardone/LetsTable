import { API_URL } from '@/api/client';

export const getRuleFileDownloadUrl = (gameId: string, fileId: string): string =>
    `${API_URL}/games/${gameId}/rules/${fileId}/download`;