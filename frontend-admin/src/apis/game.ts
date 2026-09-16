import axiosInstance from '@/apis/axiosConfig';
import { handleApiError } from '@/apis/axiosError';

import { toast } from '@/components/ui/toast';

import type { AdminGame, AdminUploadedFile, BggRankIndex } from '@/types/game';
import type { Page } from '@/types/page';

export interface ListGamesParams {
	search?: string;
	isExpansion?: boolean;
	sort?: string;
	page?: number;
	size?: number;
}

export const listGames = async (params?: ListGamesParams): Promise<Page<AdminGame>> => {
	try {
		const res = await axiosInstance.get<Page<AdminGame>>('/admin/games', { params });
		return res.data;
	} catch (error) {
		handleApiError(error, {
			fallback: 'Unable to fetch games list.',
			statusMessages: {
				401: 'Session expired. Please sign in again.',
				403: 'Access denied. Admin privileges required.',
			},
			toastError: true,
		});
	}
}

export const forceRefreshGame = async (bggId: number): Promise<AdminGame> => {
	try {
		const res = await axiosInstance.post<AdminGame>(`/admin/games/${bggId}/refresh`);
		toast.add({ type: 'success', description: 'Game refreshed from BoardGameGeek.' });
		return res.data;
	} catch (error) {
		handleApiError(error, {
			fallback: 'Unable to refresh game.',
			statusMessages: {
				401: 'Session expired. Please sign in again.',
				403: 'Access denied. Admin privileges required.',
				404: 'Game not found on BoardGameGeek.',
			},
			toastError: true,
		});
	}
}

export const forceRefreshHotGames = async (): Promise<void> => {
	try {
		await axiosInstance.post('/admin/games/hot-games/refresh');
		toast.add({ type: 'success', description: 'Hot games cache refreshed.' });
	} catch (error) {
		handleApiError(error, {
			fallback: 'Unable to refresh hot games cache.',
			statusMessages: {
				401: 'Session expired. Please sign in again.',
				403: 'Access denied. Admin privileges required.',
			},
			toastError: true,
		});
	}
}

export const listRuleFiles = async (gameId: string): Promise<AdminUploadedFile[]> => {
	try {
		const res = await axiosInstance.get<AdminUploadedFile[]>(`/admin/games/${gameId}/rules`);
		return res.data;
	} catch (error) {
		handleApiError(error, {
			fallback: 'Unable to fetch rule files.',
			statusMessages: {
				401: 'Session expired. Please sign in again.',
				403: 'Access denied. Admin privileges required.',
				404: 'Game not found.',
			},
			toastError: true,
		});
	}
}

export const deleteRuleFile = async (gameId: string, fileId: string): Promise<void> => {
	try {
		await axiosInstance.delete(`/admin/games/${gameId}/rules/${fileId}`);
		toast.add({ type: 'success', description: 'Rule file deleted.' });
	} catch (error) {
		handleApiError(error, {
			fallback: 'Unable to delete rule file.',
			statusMessages: {
				401: 'Session expired. Please sign in again.',
				403: 'Access denied. Admin privileges required.',
				404: 'File not found.',
			},
			toastError: true,
		});
	}
}

export const uploadRankIndex = async (file: File): Promise<BggRankIndex> => {
	try {
		const formData = new FormData();
		formData.append('file', file);

		const res = await axiosInstance.post<BggRankIndex>('/admin/games/rank-index/upload', formData, {
			headers: { 'Content-Type': 'multipart/form-data' },
			timeout: 1800000,
		});

		toast.add({ type: 'success', description: `Rank index aggiornato: ${res.data.count} voci.` });
		return res.data;
	} catch (error) {
		handleApiError(error, {
			fallback: 'Impossibile caricare il file del rank index.',
			statusMessages: {
				400: 'File non valido o non leggibile.',
				401: 'Sessione scaduta. Effettua di nuovo il login.',
				403: 'Accesso negato. Sono richiesti i privilegi di amministratore.',
			},
			toastError: true,
		});
	}
}

export const getGame = async (gameId: string): Promise<AdminGame> => {
	try {
		const res = await axiosInstance.get<AdminGame>(`/admin/games/${gameId}`);
		return res.data;
	} catch (error) {
		handleApiError(error, {
			fallback: 'Unable to fetch game details.',
			statusMessages: {
				401: 'Session expired. Please sign in again.',
				403: 'Access denied. Admin privileges required.',
				404: 'Game not found.',
			},
			toastError: true,
		});
	}
}