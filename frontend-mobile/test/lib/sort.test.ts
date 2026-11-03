import { DEFAULT_MATCH_SORT, MATCH_SORT_OPTIONS } from '@/lib/sort';

describe('MATCH_SORT_OPTIONS', () => {
	it('includes the default sort', () => {
		expect(MATCH_SORT_OPTIONS.map(option => option.value)).toContain(DEFAULT_MATCH_SORT);
	});

	it('uses unique values in "field-direction" format', () => {
		const values = MATCH_SORT_OPTIONS.map(option => option.value);

		expect(new Set(values).size).toBe(values.length);
		values.forEach(value => expect(value).toMatch(/^[a-zA-Z]+-(asc|desc)$/));
	});

	it('offers both directions for the winner score', () => {
		const values = MATCH_SORT_OPTIONS.map(option => option.value);

		expect(values).toEqual(expect.arrayContaining(['winnerScore-desc', 'winnerScore-asc']));
	});
});