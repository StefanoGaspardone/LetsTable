import { DarkTheme, DefaultTheme } from 'expo-router/react-navigation';

import { COLORS, NAV_THEME } from '@/lib/theme';

const HEX_COLOR = /^#[0-9A-F]{6}$/i;
const MODES = ['light', 'dark'] as const;

describe('COLORS', () => {
	it.each(MODES)('uses valid hex colors for every token in %s mode', (mode) => {
		Object.values(COLORS[mode]).forEach(value => {
			expect(value).toMatch(HEX_COLOR);
		});
	});

	it('defines the same tokens in light and dark mode', () => {
		expect(Object.keys(COLORS.dark).sort()).toEqual(Object.keys(COLORS.light).sort());
	});

	it('uses different background and foreground colors between light and dark mode', () => {
		expect(COLORS.light.background).not.toBe(COLORS.dark.background);
		expect(COLORS.light.foreground).not.toBe(COLORS.dark.foreground);
	});
});

describe('NAV_THEME', () => {
	it.each(MODES)('maps the %s palette onto the navigation theme', (mode) => {
		const colors = NAV_THEME[mode].colors;

		expect(colors.primary).toBe(COLORS[mode].primary);
		expect(colors.background).toBe(COLORS[mode].background);
		expect(colors.card).toBe(COLORS[mode].card);
		expect(colors.text).toBe(COLORS[mode].foreground);
		expect(colors.border).toBe(COLORS[mode].border);
		expect(colors.notification).toBe(COLORS[mode].destructive);
	});

	it('builds each variant on the matching base navigation theme', () => {
		expect(NAV_THEME.light.dark).toBe(DefaultTheme.dark);
		expect(NAV_THEME.dark.dark).toBe(DarkTheme.dark);
		expect(NAV_THEME.light.fonts).toEqual(DefaultTheme.fonts);
		expect(NAV_THEME.dark.fonts).toEqual(DarkTheme.fonts);
	});
});