import { apiClient } from '@/api/client';

import { CollectionItem, ListCollectionParams } from '@/types/collection';
import { PageDTO } from '@/types/page';

export const listCollection = async (params: ListCollectionParams): Promise<PageDTO<CollectionItem>> => {
    const { data } = await apiClient.get<PageDTO<CollectionItem>>('/collection', {
        params: {
            page: params.page,
            size: params.size ?? 20,
            gameName: params.gameName || undefined,
        },
    });
    return data;
}