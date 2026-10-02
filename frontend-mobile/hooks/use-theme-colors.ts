import { useColorScheme } from 'nativewind';

const PALETTE = {
    light: {
        primary: '#C45135',
        primaryForeground: '#FFFFFF',
        background: '#F2EFE9',
        foreground: '#1E1C1A',
        card: '#FFFFFF',
        cardForeground: '#1E1C1A',
        secondary: '#E8E3D8',
        mutedForeground: '#736E65',
        border: '#DBD5C9',
        destructive: '#C73E3E',
        tabIconDefault: '#8A847A',
    },
    dark: {
        primary: '#2AABEE',
        primaryForeground: '#FFFFFF',
        background: '#17212B',
        foreground: '#F0F3F7',
        card: '#1D2733',
        cardForeground: '#F0F3F7',
        secondary: '#242F3D',
        mutedForeground: '#8D9CAE',
        border: '#2A3645', 
        destructive: '#E55353',
        tabIconDefault: '#7A858F',
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