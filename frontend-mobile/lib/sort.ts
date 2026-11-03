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