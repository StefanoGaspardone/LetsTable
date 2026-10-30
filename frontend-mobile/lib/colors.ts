export const PALETTE = ['#FF0000', '#FF8000', '#FFFF00', '#00FF00', '#06402B', '#A52A2A', '#0000FF', '#007FFF', '#6A0DAD', '#FFC0CB', '#FFFFFF', '#000000', '#808080'];

const DARK_TEXT = '#1E1C1A';
const LIGHT_TEXT = '#FFFFFF';
const LUMINANCE_THRESHOLD = 0.3;

export const getPlayerColor = (index: number): string => PALETTE[index % PALETTE.length];

const hexToRgb = (hex: string) => {
	const normalized = hex.replace('#', '');
	const full = normalized.length === 3 ? normalized.split('').map(char => char + char).join('') : normalized.slice(0, 6);

	if(full.length !== 6) return null;

	const value = Number.parseInt(full, 16);
	if(Number.isNaN(value)) return null;

	return { r: (value >> 16) & 255, g: (value >> 8) & 255, b: value & 255 };
}

const toLinear = (channel: number) => {
	const normalized = channel / 255;
	return normalized <= 0.03928 ? normalized / 12.92 : ((normalized + 0.055) / 1.055) ** 2.4;
}

export const getRelativeLuminance = (hex: string): number | null => {
	const rgb = hexToRgb(hex);
	if(!rgb) return null;

	return 0.2126 * toLinear(rgb.r) + 0.7152 * toLinear(rgb.g) + 0.0722 * toLinear(rgb.b);
}

export const getContrastTextColor = (background: string): string => {
	const luminance = getRelativeLuminance(background);
	if(luminance == null) return LIGHT_TEXT;

	return luminance > LUMINANCE_THRESHOLD ? DARK_TEXT : LIGHT_TEXT;
}