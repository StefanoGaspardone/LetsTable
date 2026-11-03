import { getMatchSummary } from '@/lib/match-summary';

import { Match } from '@/types/match';

const user = (id: string, username: string, avatarId: string | null = null) => ({ id, username, avatarId }) as any;

const individualMatch = (players: Array<{ user?: any; guestName?: string | null; score?: number | null; isWinner?: boolean | null }>, durationMinutes: number | null = 60): Match => ({
	id: 'm-1',
	isTeamBased: false,
	durationMinutes,
	teams: null,
	players: players.map((player, index) => ({
		id: `p-${index}`,
		user: player.user ?? null,
		guestName: player.guestName ?? null,
		color: null,
		score: player.score ?? null,
		isWinner: player.isWinner ?? false,
		isStartingFirst: false,
	})),
}) as unknown as Match;

const teamMatch = (teams: Array<{ name?: string | null; score?: number | null; isWinner?: boolean }>, durationMinutes: number | null = 60): Match => ({
	id: 'm-2',
	isTeamBased: true,
	durationMinutes,
	players: null,
	teams: teams.map((team, index) => ({
		id: `t-${index}`,
		name: team.name === undefined ? `Team ${index}` : team.name,
		color: '#000000',
		score: team.score ?? null,
		isWinner: team.isWinner ?? false,
		isStartingFirst: false,
		players: [],
	})),
}) as unknown as Match;

describe('getMatchSummary', () => {
	describe('individual matches', () => {
		it('returns the winner, the other participants and the margin to the closest opponent', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'marco', 'avatar-1'), score: 145, isWinner: true },
				{ user: user('u-2', 'anna'), score: 132 },
				{ guestName: 'Giulia', score: 98 },
			]));

			expect(summary.winner).toEqual({ key: 'u-1', name: 'marco', avatarId: 'avatar-1', isWinner: true, score: 145 });
			expect(summary.others.map(other => other.name)).toEqual(['anna', 'Giulia']);
			expect(summary.margin).toBe(13);
			expect(summary.tiedWinnersCount).toBe(0);
			expect(summary.isInProgress).toBe(false);
		});

		it('computes the margin in absolute value when the lowest score wins', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'luca'), score: 12, isWinner: true },
				{ user: user('u-2', 'marco'), score: 18 },
				{ user: user('u-3', 'anna'), score: 25 },
			]));

			expect(summary.winner?.score).toBe(12);
			expect(summary.margin).toBe(6);
		});

		it('counts tied winners and picks the first highest scoring one', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'marco'), score: 150, isWinner: true },
				{ user: user('u-2', 'luca'), score: 150, isWinner: true },
				{ user: user('u-3', 'elena'), score: 120 },
			]));

			expect(summary.winner?.name).toBe('marco');
			expect(summary.tiedWinnersCount).toBe(1);
			expect(summary.margin).toBe(30);
		});

		it('picks the highest scoring winner among several winners', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'marco'), score: 80, isWinner: true },
				{ user: user('u-2', 'luca'), score: 95, isWinner: true },
			]));

			expect(summary.winner?.name).toBe('luca');
		});

		it('has no margin when nobody has a score', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'elena'), isWinner: true },
				{ user: user('u-2', 'marco') },
			]));

			expect(summary.winner?.name).toBe('elena');
			expect(summary.winner?.score).toBeNull();
			expect(summary.margin).toBeNull();
		});

		it('has no margin when only the winner has a score', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'elena'), score: 40, isWinner: true },
				{ user: user('u-2', 'marco') },
			]));

			expect(summary.margin).toBeNull();
		});

		it('returns no winner when nobody won', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'elena'), score: 10 },
				{ user: user('u-2', 'marco'), score: 8 },
			]));

			expect(summary.winner).toBeNull();
			expect(summary.margin).toBeNull();
			expect(summary.tiedWinnersCount).toBe(0);
			expect(summary.others).toHaveLength(2);
		});

		it('uses the user id as key, the guest name for guests and a fallback name', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'marco') },
				{ guestName: 'Giulia' },
				{},
			]));

			expect(summary.participants.map(participant => [participant.key, participant.name])).toEqual([
				['u-1', 'marco'],
				['guest-1', 'Giulia'],
				['guest-2', 'Sconosciuto'],
			]);
		});

		it('treats a null isWinner as not winning', () => {
			const summary = getMatchSummary(individualMatch([{ user: user('u-1', 'marco'), isWinner: null }]));

			expect(summary.participants[0].isWinner).toBe(false);
			expect(summary.winner).toBeNull();
		});
	});

	describe('in-progress matches', () => {
		it('hides winner and margin but keeps the participants', () => {
			const summary = getMatchSummary(individualMatch([
				{ user: user('u-1', 'marco'), score: 10, isWinner: true },
				{ user: user('u-2', 'anna'), score: 5 },
			], null));

			expect(summary.isInProgress).toBe(true);
			expect(summary.winner).toBeNull();
			expect(summary.margin).toBeNull();
			expect(summary.participants).toHaveLength(2);
		});
	});

	describe('team matches', () => {
		it('uses the teams as participants', () => {
			const summary = getMatchSummary(teamMatch([
				{ name: 'Impero', score: 12, isWinner: true },
				{ name: 'Ribelli', score: 8 },
				{ name: 'Nomadi', score: 9 },
			]));

			expect(summary.participants.map(participant => participant.key)).toEqual(['team-0', 'team-1', 'team-2']);
			expect(summary.winner).toEqual({ key: 'team-0', name: 'Impero', avatarId: null, isWinner: true, score: 12 });
			expect(summary.margin).toBe(3);
		});

		it('falls back to a default name for unnamed teams', () => {
			const summary = getMatchSummary(teamMatch([{ name: null, isWinner: true }]));

			expect(summary.winner?.name).toBe('Squadra');
		});

		it('handles several winning teams without scores', () => {
			const summary = getMatchSummary(teamMatch([
				{ name: 'Rossa', isWinner: true },
				{ name: 'Blu', isWinner: true },
				{ name: 'Verde' },
			]));

			expect(summary.winner?.name).toBe('Rossa');
			expect(summary.tiedWinnersCount).toBe(1);
			expect(summary.margin).toBeNull();
		});

		it('handles a match with missing teams', () => {
			const summary = getMatchSummary({ ...teamMatch([]), teams: null } as Match);

			expect(summary.participants).toEqual([]);
			expect(summary.winner).toBeNull();
		});
	});
});