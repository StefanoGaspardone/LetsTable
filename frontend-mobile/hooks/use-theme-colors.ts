import { useColorScheme } from 'nativewind';

import { COLORS } from '@/lib/theme';

export const useThemeColors = () => {
	const { colorScheme } = useColorScheme();
	const isDark = colorScheme === 'dark';

	return {
		colors: isDark ? COLORS.dark : COLORS.light,
		isDark,
		colorScheme,
	}
}