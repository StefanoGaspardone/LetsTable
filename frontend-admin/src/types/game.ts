export interface AdminGame {
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
	bggRank: number | null;
	avgRating: number | null;
	inCollection: boolean | null;
	baseGame: AdminGame | null;
	difficulty: number | null;
	designers: string[];
	artists: string[];
	publishers: string[];
	sleeves: GameSleeve[];
	lastSyncedAt: string;
	sleevesSyncedAt: string | null;
}

export interface GameSleeve {
	id: string;
	name: string | null;
	height: number | null;
	width: number | null;
	quantity: number | null;
	quantityNote: string | null;
}

export interface AdminUploadedFile {
	id: string;
	fileName: string;
	contentType: string;
	sizeBytes: number;
	uploadedByUsername: string | null;
	createdAt: string;
}

export interface BggRankIndex {
	count: number;
}