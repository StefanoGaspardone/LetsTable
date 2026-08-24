import { apiClient } from '@/api/client';

import { CollectionItem, CollectionStatus } from '@/types/collection';
import { PageDTO } from '@/types/page';

export interface ListCollectionParams {
    page: number;
    size?: number;
    gameName?: string;
    played?: boolean;
}

export const listCollection = async (params: ListCollectionParams): Promise<PageDTO<CollectionItem>> => {
    const { data } = await apiClient.get<PageDTO<CollectionItem>>('/collection', {
		params: {
			page: params.page,
			size: params.size ?? 20,
			gameName: params.gameName || undefined,
			played: params.played,
		},
	});
	return data;
}

export const addToCollection = async (gameId: string): Promise<CollectionItem> => {
    const { data } = await apiClient.post<CollectionItem>('/collection', { gameId });
    return data;
}

export const removeFromCollection = async (itemId: string): Promise<void> => {
    await apiClient.delete(`/collection/${itemId}`);
}

export const getGameStatusInCollection = async (gameId: string): Promise<CollectionStatus> => {
    const { data } = await apiClient.get<CollectionStatus>('/collection/status', {
        params: { gameId },
    });
    return data;
}