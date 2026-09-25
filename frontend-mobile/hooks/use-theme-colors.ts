import { useColorScheme } from 'nativewind';

const PALETTE = {
    light: {
        primary: '#C45135',          // HSL 12 57% 49%
        primaryForeground: '#FFFFFF',
        background: '#F2EFE9',       // HSL 40 24% 93%
        foreground: '#1E1C1A',       // HSL 30 7% 11%
        card: '#FFFFFF',
        cardForeground: '#1E1C1A',
        secondary: '#E8E3D8',       // HSL 38 24% 89%
        mutedForeground: '#736E65',  // HSL 38 7% 42%
        border: '#DBD5C9',          // HSL 40 18% 84%
        destructive: '#C73E3E',
        tabIconDefault: '#8A847A',
    },
    dark: {
        primary: '#2AABEE',          // HSL 203 89% 53% (Telegram Blue)
        primaryForeground: '#FFFFFF',
        background: '#17212B',       // HSL 215 25% 12% (Telegram Dark)
        foreground: '#F0F3F7',       // HSL 210 20% 98%
        card: '#1D2733',             // HSL 216 24% 15%
        cardForeground: '#F0F3F7',
        secondary: '#242F3D',       // HSL 215 20% 20%
        mutedForeground: '#8D9CAE',  // HSL 215 15% 65%
        border: '#2A3645',          // HSL 215 18% 22%
        destructive: '#E55353',
        tabIconDefault: '#6C7883',
    },
}

export const useThemeColors = () => {
    const { colorScheme } = useColorScheme();
    const isDark = colorScheme === 'dark';

    const colors = isDark ? PALETTE.dark : PALETTE.light;

    return {
        colors,
        isDark,
        colorScheme,
    }
}