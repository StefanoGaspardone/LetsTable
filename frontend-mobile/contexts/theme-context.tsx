import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';

export type ThemePreference = 'light' | 'dark' | 'system';

const STORAGE_KEY = '@theme_preference';

interface ThemeContextValue {
	themePreference: ThemePreference;
	setThemePreference: (preference: ThemePreference) => void;
	isLoading: boolean;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
	const [themePreference, setThemePreferenceState] = useState<ThemePreference>('system');
	const [isLoading, setIsLoading] = useState(true);

	useEffect(() => {
		AsyncStorage.getItem(STORAGE_KEY).then(stored => {
			if(stored === 'light' || stored === 'dark' || stored === 'system') {
				setThemePreferenceState(stored);
			}
			setIsLoading(false);
		});
	}, []);

	const setThemePreference = (preference: ThemePreference) => {
		setThemePreferenceState(preference);
		AsyncStorage.setItem(STORAGE_KEY, preference);
	}

	return (
		<ThemeContext.Provider value = {{ themePreference, setThemePreference, isLoading }}>
			{children}
		</ThemeContext.Provider>
	)
}

export const useTheme = () => {
	const context = useContext(ThemeContext);

	if(!context) throw new Error('useTheme must be used within a ThemeProvider');
	
    return context;
}