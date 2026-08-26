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
	expansions: number | null;
	isExpansion: boolean | null;
	rank: number | null;
	inCollection: boolean | null;
	baseGame: Game | null;
	difficulty: number | null;
	designers: string[];
	artists: string[];
	publishers: string[];
}