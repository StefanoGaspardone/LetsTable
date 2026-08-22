import { Game } from '@/types/game';

export interface CollectionItem {
    id: string;
    game: Game;
    addedAt: string;
}

export interface CollectionStatus {
	inCollection: boolean;
	itemId: string | null;
}