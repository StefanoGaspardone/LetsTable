import { createContext, useCallback, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useColorScheme as useNativeWindColorScheme } from 'nativewind';

export type ThemePreference = 'light' | 'dark' | 'system';

const STORAGE_KEY = '@theme_preference';
const DEFAULT_PREFERENCE: ThemePreference = 'light';

const isThemePreference = (value: string | null): value is ThemePreference => value === 'light' || value === 'dark' || value === 'system';

interface ThemeContextValue {
	themePreference: ThemePreference;
	setThemePreference: (preference: ThemePreference) => void;
	isLoading: boolean;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
	const [themePreference, setThemePreferenceState] = useState<ThemePreference>(DEFAULT_PREFERENCE);
	const [isLoading, setIsLoading] = useState(true);

	const { setColorScheme } = useNativeWindColorScheme();

	useEffect(() => {
		AsyncStorage.getItem(STORAGE_KEY)
			.then((stored) => {
				const preference = isThemePreference(stored) ? stored : DEFAULT_PREFERENCE;

				setThemePreferenceState(preference);
				setColorScheme(preference);
			})
			.catch(error => console.log('Failed to load theme preference:', error))
			.finally(() => setIsLoading(false));
	}, []);

	const setThemePreference = useCallback((preference: ThemePreference) => {
		setThemePreferenceState(preference);
		setColorScheme(preference);

		AsyncStorage.setItem(STORAGE_KEY, preference).catch(error => console.log('Failed to save theme preference:', error));
	}, [setColorScheme]);

	const value = useMemo(() => ({ themePreference, setThemePreference, isLoading }), [themePreference, setThemePreference, isLoading]);

	return (
		<ThemeContext.Provider value = { value }>
			{children}
		</ThemeContext.Provider>
	)
}

export const useTheme = () => {
	const context = useContext(ThemeContext);

	if(!context) {
		throw new Error('useTheme must be used within a ThemeProvider');
	}

	return context;
}