export interface GamePopularity {
	gameId: string;
	gameName: string;
    gameThumbnailUrl: string;
	count: number;
}

export interface AdminStats {
	totalUsers: number;
	activeUsers: number;
	activationRate: number;
	totalMatches: number;
	mostOwnedGames: GamePopularity[];
	mostPlayedGames: GamePopularity[];
	mostWishedGames: GamePopularity[];
}