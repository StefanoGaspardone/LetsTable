import { Match } from '@/types/match';

export interface MatchParticipant {
	key: string;
	name: string;
	avatarId: string | null;
	isWinner: boolean;
	score: number | null;
}

export interface MatchSummary {
	participants: MatchParticipant[];
	winner: MatchParticipant | null;
	others: MatchParticipant[];
	tiedWinnersCount: number;
	margin: number | null;
	isInProgress: boolean;
}

const getParticipants = (match: Match): MatchParticipant[] => {
	if(match.isTeamBased) {
		return (match.teams ?? []).map((team, index) => ({
			key: `team-${index}`,
			name: team.name ?? 'Squadra',
			avatarId: null,
			isWinner: !!team.isWinner,
			score: team.score ?? null,
		}))
	}

	return (match.players ?? []).map((player, index) => ({
		key: player.user?.id ?? `guest-${index}`,
		name: player.user?.username ?? player.guestName ?? 'Sconosciuto',
		avatarId: player.user?.avatarId ?? null,
		isWinner: !!player.isWinner,
		score: player.score ?? null,
	}))
}

export const getMatchSummary = (match: Match): MatchSummary => {
	const participants = getParticipants(match);
	const winners = participants.filter(participant => participant.isWinner);
	const others = participants.filter(participant => !participant.isWinner);
	const isInProgress = match.durationMinutes == null;

	const winner = winners.reduce<MatchParticipant | null>((best, current) =>
		!best || (current.score ?? -Infinity) > (best.score ?? -Infinity) ? current : best, null);

	const otherScores = others.map(other => other.score).filter((score): score is number => score != null);
	const margin = winner?.score != null && otherScores.length > 0
		? Math.min(...otherScores.map(score => Math.abs(winner.score! - score)))
		: null;

	return {
		participants,
		winner: isInProgress ? null : winner,
		others,
		tiedWinnersCount: Math.max(winners.length - 1, 0),
		margin: isInProgress ? null : margin,
		isInProgress,
	}
}