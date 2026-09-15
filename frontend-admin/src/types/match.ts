import type { User } from '@/types/user';
import type { Game } from '@/types/game';

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
    expansionsUsed: Game[];
}