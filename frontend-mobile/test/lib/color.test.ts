import { PALETTE, getPlayerColor } from '@/lib/colors';

describe('getPlayerColor', () => {
	it('returns the color at the given index', () => {
		expect(getPlayerColor(0)).toBe(PALETTE[0]);
		expect(getPlayerColor(1)).toBe(PALETTE[1]);
		expect(getPlayerColor(5)).toBe(PALETTE[5]);
	});

	it('returns the last color in the palette when index is palette.length - 1', () => {
		expect(getPlayerColor(PALETTE.length - 1)).toBe(PALETTE[PALETTE.length - 1]);
	});

	it('wraps around to the start of the palette when the index exceeds its length', () => {
		expect(getPlayerColor(PALETTE.length)).toBe(PALETTE[0]);
		expect(getPlayerColor(PALETTE.length + 1)).toBe(PALETTE[1]);
	});

	it('wraps around correctly for indices multiple times larger than the palette length', () => {
		const index = PALETTE.length * 3 + 2;
		expect(getPlayerColor(index)).toBe(PALETTE[2]);
	});

	it('returns a valid hex color for every index in the palette', () => {
		PALETTE.forEach((_, index) => {
			expect(getPlayerColor(index)).toMatch(/^#[0-9A-Fa-f]{6}$/);
		});
	});
});

describe('PALETTE', () => {
	it('contains only unique colors', () => {
		const uniqueColors = new Set(PALETTE);
		expect(uniqueColors.size).toBe(PALETTE.length);
	});

	it('contains only valid hex color strings', () => {
		PALETTE.forEach(color => {
			expect(color).toMatch(/^#[0-9A-Fa-f]{6}$/);
		});
	});
});