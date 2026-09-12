import { THEME, NAV_THEME } from '@/lib/theme';

describe('THEME', () => {
	it('defines all required color tokens for light mode', () => {
		expect(THEME.light.background).toBeTruthy();
		expect(THEME.light.foreground).toBeTruthy();
		expect(THEME.light.primary).toBeTruthy();
		expect(THEME.light.destructive).toBeTruthy();
		expect(THEME.light.border).toBeTruthy();
	});

	it('defines all required color tokens for dark mode', () => {
		expect(THEME.dark.background).toBeTruthy();
		expect(THEME.dark.foreground).toBeTruthy();
		expect(THEME.dark.primary).toBeTruthy();
		expect(THEME.dark.destructive).toBeTruthy();
		expect(THEME.dark.border).toBeTruthy();
	});

	it('uses valid hsl() color strings for light mode', () => {
		Object.entries(THEME.light).forEach(([key, value]) => {
			if(key === 'radius') return;
			expect(value).toMatch(/^hsl\(/);
		});
	});

	it('uses valid hsl() color strings for dark mode', () => {
		Object.entries(THEME.dark).forEach(([key, value]) => {
			if(key === 'radius') return;
			expect(value).toMatch(/^hsl\(/);
		});
	});

	it('has different background colors between light and dark mode', () => {
		expect(THEME.light.background).not.toBe(THEME.dark.background);
	});
});

describe('NAV_THEME', () => {
	it('maps light theme colors correctly from THEME.light', () => {
		expect(NAV_THEME.light.colors.background).toBe(THEME.light.background);
		expect(NAV_THEME.light.colors.border).toBe(THEME.light.border);
		expect(NAV_THEME.light.colors.card).toBe(THEME.light.card);
		expect(NAV_THEME.light.colors.notification).toBe(THEME.light.destructive);
		expect(NAV_THEME.light.colors.primary).toBe(THEME.light.primary);
		expect(NAV_THEME.light.colors.text).toBe(THEME.light.foreground);
	});

	it('maps dark theme colors correctly from THEME.dark', () => {
		expect(NAV_THEME.dark.colors.background).toBe(THEME.dark.background);
		expect(NAV_THEME.dark.colors.border).toBe(THEME.dark.border);
		expect(NAV_THEME.dark.colors.card).toBe(THEME.dark.card);
		expect(NAV_THEME.dark.colors.notification).toBe(THEME.dark.destructive);
		expect(NAV_THEME.dark.colors.primary).toBe(THEME.dark.primary);
		expect(NAV_THEME.dark.colors.text).toBe(THEME.dark.foreground);
	});

	it('provides both light and dark theme variants', () => {
		expect(NAV_THEME.light).toBeDefined();
		expect(NAV_THEME.dark).toBeDefined();
	});
});