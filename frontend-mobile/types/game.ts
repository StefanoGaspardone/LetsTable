export interface Game {
	id: string;
	bggId: number;
	name: string;
	yearPublished: number | null;
	thumbnailUrl: string | null;
	imageUrl: string | null;
	minPlayers: number | null;
	maxPlayers: number | null;
	playingTimeMinutes: number | null;
	description: string | null;
	bestWith: string | null;
	recommendedWith: string | null;
	expansions: number;
}

export interface GameSearchResult {
	bggId: number;
	name: string;
	yearPublished: number | null;
}

export interface HotGame {
	bggId: number;
	rank: number;
	name: string;
	thumbnailUrl: string | null;
	yearPublished: number | null;
}