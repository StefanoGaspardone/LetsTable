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
}