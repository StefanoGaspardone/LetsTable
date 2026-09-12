import { formatRelativeDays, formatFullDate, formatShortDate } from '@/lib/date';

describe('formatRelativeDays', () => {
	beforeEach(() => {
		jest.useFakeTimers();
		jest.setSystemTime(new Date('2026-09-12T12:00:00'));
	});

	afterEach(() => {
		jest.useRealTimers();
	});

	it('returns "Oggi" when the date is today', () => {
		expect(formatRelativeDays('2026-09-12T08:00:00')).toBe('Oggi');
	});

	it('returns "Oggi" regardless of the time of day, as long as the calendar day matches', () => {
		expect(formatRelativeDays('2026-09-12T23:59:00')).toBe('Oggi');
		expect(formatRelativeDays('2026-09-12T00:00:01')).toBe('Oggi');
	});

	it('returns a relative distance in Italian for a date in the past', () => {
		const result = formatRelativeDays('2026-09-10T12:00:00');
		expect(result).toContain('fa');
	});

	it('returns a relative distance in Italian for a date in the future', () => {
		const result = formatRelativeDays('2026-09-15T12:00:00');
		expect(result).not.toBe('Oggi');
		expect(result).not.toContain('fa');
	});

	it('does not return "Oggi" for yesterday', () => {
		const result = formatRelativeDays('2026-09-11T12:00:00');
		expect(result).not.toBe('Oggi');
	});

	it('does not return "Oggi" for tomorrow', () => {
		const result = formatRelativeDays('2026-09-13T12:00:00');
		expect(result).not.toBe('Oggi');
	});
});

describe('formatFullDate', () => {
	it('formats the date as day, full month name, and year in Italian', () => {
		expect(formatFullDate('2026-09-12T12:00:00')).toBe('12 settembre 2026');
	});

	it('formats a different month correctly', () => {
		expect(formatFullDate('2026-01-05T12:00:00')).toBe('5 gennaio 2026');
	});
});

describe('formatShortDate', () => {
	it('formats the date as day, abbreviated month, and year in Italian', () => {
		expect(formatShortDate('2026-09-12T12:00:00')).toBe('12 set 2026');
	});

	it('formats a different month correctly', () => {
		expect(formatShortDate('2026-01-05T12:00:00')).toBe('5 gen 2026');
	});
});