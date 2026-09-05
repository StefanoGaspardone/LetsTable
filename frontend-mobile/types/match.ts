import { User } from '@/types/user';
import { Game } from '@/types/game';

export interface MatchPlayerRef {
	id: string;
	user: User | null;
	guestName: string | null;
}

export interface MatchTeam {
	id: string;
	name: string | null;
	color: string;
	score: number;
	isWinner: boolean;
	startingPosition: number | null;
	players: MatchPlayerRef[];
}

export interface MatchPlayer {
	id: string;
	user: User | null;
	guestName: string | null;
	color: string | null;
	score: number | null;
	isWinner: boolean | null;
	startingPosition: number | null;
}

export interface Match {
	id: string;
	game: Game;
	createdBy: User;
	isTeamBased: boolean;
	playedAt: string;
	place: string | null;
	notes: string | null;
	durationMinutes: number | null;
	createdAt: string;
	teams: MatchTeam[] | null;
	players: MatchPlayer[] | null;
}

export interface MatchDayCount {
	date: string;
	count: number;
}

export interface MatchPlayerIdentityPayload {
	userId: string | null;
	guestName: string | null;
}

export interface CreateMatchTeamPayload {
	name: string | null;
	color: string;
	score: number;
	isWinner: boolean;
	players: MatchPlayerIdentityPayload[];
}

export interface MatchIndividualPlayerPayload extends MatchPlayerIdentityPayload {
	color: string;
	score: number;
	isWinner: boolean;
}

export interface CreateMatchPayload {
	gameId: string;
	playedAt: string;
	place: string | null;
	notes: string | null;
	durationMinutes: number | null;
	isTeamBased: boolean;
	teams: CreateMatchTeamPayload[] | null;
	players: MatchIndividualPlayerPayload[] | null;
}

export interface UpdateMatchPayload {
	gameId: string;
	playedAt: string;
	place: string | null;
	notes: string | null;
	isTeamBased: boolean;
	teams: CreateMatchTeamPayload[] | null;
	players: MatchIndividualPlayerPayload[] | null;
}