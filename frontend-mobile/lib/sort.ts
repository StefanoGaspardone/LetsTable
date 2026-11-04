import { SortOption } from '@/components/common/sort-sheet';

export const MATCH_SORT_OPTIONS: SortOption[] = [
	{ value: 'playedAt-desc', label: 'Più recenti' },
	{ value: 'playedAt-asc', label: 'Meno recenti' },
	{ value: 'durationMinutes-desc', label: 'Più lunghe' },
	{ value: 'durationMinutes-asc', label: 'Più corte' },
	{ value: 'winnerScore-desc', label: 'Punteggio vincitore più alto' },
	{ value: 'winnerScore-asc', label: 'Punteggio vincitore più basso' },
]

export const DEFAULT_MATCH_SORT = 'playedAt-desc';

export const GAME_SORT_OPTIONS = [
	{ value: 'name-asc', label: 'Nome (A-Z)' },
	{ value: 'name-desc', label: 'Nome (Z-A)' },
	{ value: 'bggRank-asc', label: 'Classifica BGG' },
	{ value: 'avgRating-desc', label: 'Voto più alto' },
	{ value: 'yearPublished-desc', label: 'Più recenti' },
	{ value: 'yearPublished-asc', label: 'Meno recenti' },
];

export const DEFAULT_GAME_SORT = 'name-asc';