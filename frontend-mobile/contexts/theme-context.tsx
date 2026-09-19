import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { useColorScheme as useNativeWindColorScheme } from 'nativewind';

export type ThemePreference = 'light' | 'dark' | 'system';

const STORAGE_KEY = '@theme_preference';

interface ThemeContextValue {
	themePreference: ThemePreference;
	setThemePreference: (preference: ThemePreference) => void;
	isLoading: boolean;
}

const ThemeContext = createContext<ThemeContextValue | undefined>(undefined);

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
	const [themePreference, setThemePreferenceState] = useState<ThemePreference>('light');
	const [isLoading, setIsLoading] = useState(true);
	
	const { setColorScheme } = useNativeWindColorScheme();

	useEffect(() => {
		AsyncStorage.getItem(STORAGE_KEY).then((stored) => {
		
			if(stored === 'light' || stored === 'dark' || stored === 'system') {
				const pref = stored as ThemePreference;
				
				setThemePreferenceState(pref);
				setColorScheme(pref);
			}
			
			setIsLoading(false);
		});
	}, []);

	const setThemePreference = (preference: ThemePreference) => {
		setThemePreferenceState(preference);
		setColorScheme(preference);
		
		AsyncStorage.setItem(STORAGE_KEY, preference);
	}

	return (
		<ThemeContext.Provider value={{ themePreference, setThemePreference, isLoading }}>
			{children}
		</ThemeContext.Provider>
	)
}

export const useTheme = () => {
  const context = useContext(ThemeContext);
  if (!context) {
    throw new Error('useTheme must be used within a ThemeProvider');
  }
  return context;
};