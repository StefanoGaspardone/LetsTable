import { apiClient } from '@/api/client';

import { Game } from '@/types/game';
import { PageDTO } from '@/types/page';

export const searchGame = async (query: string, page: number, size = 20): Promise<PageDTO<Game>> => {
	const { data } = await apiClient.get<PageDTO<Game>>('/games/search', {
		params: { query, page, size },
	});
	return data;
}

export const getHotGames = async (page: number, size = 20): Promise<PageDTO<Game>> => {
	const { data } = await apiClient.get<PageDTO<Game>>('/games/hot', {
		params: { page, size },
	});
	return data;
}

export const getGameByBggId = async (bggId: number): Promise<Game> => {
	const { data } = await apiClient.get<Game>(`/games/${bggId}`);
    return data;
}

export const getGameExpansions = async (bggId: number, page: number, size = 10): Promise<PageDTO<Game>> => {
	const { data } = await apiClient.get<PageDTO<Game>>(`/games/${bggId}/expansions`, {
		params: { page, size },
	});
	return data;
}