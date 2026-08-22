import { Game } from '@/types/game';

export interface CollectionItem {
    id: string;
    game: Game;
    addedAt: string;
}

export interface ListCollectionParams {
    page: number;
    size?: number;
    gameName?: string;
}

export interface CollectionStatus {
	inCollection: boolean;
	itemId: string | null;
}