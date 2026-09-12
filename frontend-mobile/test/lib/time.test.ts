import { formatDuration } from '@/lib/time';

describe('formatDuration', () => {
	it('returns an empty string for null', () => {
		expect(formatDuration(null)).toBe('');
	});

	it('returns an empty string for undefined', () => {
		expect(formatDuration(undefined)).toBe('');
	});

	it('returns an empty string for zero', () => {
		expect(formatDuration(0)).toBe('');
	});

	it('returns an empty string for negative values', () => {
		expect(formatDuration(-10)).toBe('');
	});

	it('formats minutes only when under an hour', () => {
		expect(formatDuration(1)).toBe('1m');
		expect(formatDuration(45)).toBe('45m');
		expect(formatDuration(59)).toBe('59m');
	});

	it('formats whole hours with no remaining minutes', () => {
		expect(formatDuration(60)).toBe('1h');
		expect(formatDuration(120)).toBe('2h');
		expect(formatDuration(180)).toBe('3h');
	});

	it('formats hours and minutes together', () => {
		expect(formatDuration(90)).toBe('1h 30m');
		expect(formatDuration(125)).toBe('2h 5m');
		expect(formatDuration(61)).toBe('1h 1m');
	});

	it('handles large durations correctly', () => {
		expect(formatDuration(600)).toBe('10h');
		expect(formatDuration(725)).toBe('12h 5m');
	});

	it('handles the boundary of exactly 60 minutes', () => {
		expect(formatDuration(59)).toBe('59m');
		expect(formatDuration(60)).toBe('1h');
		expect(formatDuration(61)).toBe('1h 1m');
	});
});