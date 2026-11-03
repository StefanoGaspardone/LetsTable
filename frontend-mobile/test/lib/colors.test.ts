import { PALETTE, getContrastTextColor, getPlayerColor, getRelativeLuminance } from '@/lib/colors';

const DARK_TEXT = '#1E1C1A';
const LIGHT_TEXT = '#FFFFFF';

describe('getPlayerColor', () => {
	it('returns the palette color for the player index', () => {
		expect(getPlayerColor(0)).toBe(PALETTE[0]);
		expect(getPlayerColor(3)).toBe(PALETTE[3]);
	});

	it('wraps around when there are more players than colors', () => {
		expect(getPlayerColor(PALETTE.length)).toBe(PALETTE[0]);
		expect(getPlayerColor(PALETTE.length + 2)).toBe(PALETTE[2]);
	});
});

describe('getRelativeLuminance', () => {
	it.each([
		['#FFFFFF', 1],
		['#000000', 0],
		['#FF0000', 0.2126],
		['#00FF00', 0.7152],
		['#0000FF', 0.0722],
		['#808080', 0.21586],
	])('computes the luminance of %s', (hex, expected) => {
		expect(getRelativeLuminance(hex)).toBeCloseTo(expected, 4);
	});

	it('accepts colors without the leading #', () => {
		expect(getRelativeLuminance('FFFFFF')).toBeCloseTo(1, 4);
	});

	it('expands the 3-digit shorthand', () => {
		expect(getRelativeLuminance('#fff')).toBeCloseTo(1, 4);
		expect(getRelativeLuminance('#f00')).toBeCloseTo(0.2126, 4);
	});

	it('ignores the alpha channel of 8-digit colors', () => {
		expect(getRelativeLuminance('#FF000080')).toBeCloseTo(0.2126, 4);
	});

	it.each(['#12345', '#zzzzzz', '', '#'])('returns null for the invalid color "%s"', hex => {
		expect(getRelativeLuminance(hex)).toBeNull();
	});
});

describe('getContrastTextColor', () => {
	it.each([
		['#FFFFFF', DARK_TEXT],
		['#FFFF00', DARK_TEXT],
		['#00FF00', DARK_TEXT],
		['#FFC0CB', DARK_TEXT],
		['#000000', LIGHT_TEXT],
		['#0000FF', LIGHT_TEXT],
		['#FF0000', LIGHT_TEXT],
		['#808080', LIGHT_TEXT],
	])('uses the right text color on %s', (background, expected) => {
		expect(getContrastTextColor(background)).toBe(expected);
	});

	it('falls back to light text for invalid colors', () => {
		expect(getContrastTextColor('not-a-color')).toBe(LIGHT_TEXT);
	});

	it('returns a readable text color for every palette entry', () => {
		PALETTE.forEach(color => {
			expect([DARK_TEXT, LIGHT_TEXT]).toContain(getContrastTextColor(color));
		});
	});
});