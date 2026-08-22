import { apiClient } from '@/api/client';

import { Game, GameSearchResult, HotGame } from '@/types/game';

import { PageDTO } from '@/types/page';

export const searchGame = async (query: string, page: number, size = 20): Promise<PageDTO<GameSearchResult>> => {
    const { data } = await apiClient.get<PageDTO<GameSearchResult>>('/games/search', {
        params: { query, page, size },
    });

    return data;
}

export const getHotGames = async (page: number, size = 20): Promise<PageDTO<HotGame>> => {
    const { data } = await apiClient.get<PageDTO<HotGame>>('/games/hot', {
        params: { page, size },
    });
    return data;
}

export const getGameByBggId = async (bggId: number): Promise<Game> => {
    const { data } = await apiClient.get<Game>(`/games/${bggId}`);
    return data;
}