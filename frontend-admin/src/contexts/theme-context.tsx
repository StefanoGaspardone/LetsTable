/* eslint-disable react-refresh/only-export-components */

import { createContext, useContext, useEffect, useState, useMemo, type ReactNode, type Dispatch, type SetStateAction } from 'react';

interface ThemeContextType {
    theme: string;
    setTheme: Dispatch<SetStateAction<string>>;
    toggleTheme: () => void;
}

const ThemeContext = createContext<ThemeContextType>({
    theme: 'light',
    setTheme: () => {},
    toggleTheme: () => {},
});

export const ThemeProvider = ({ children }: { children: ReactNode }) => {
    const [theme, setTheme] = useState(() => {
        const savedTheme = localStorage.getItem('theme');
        return savedTheme || 'light';
    });

    useEffect(() => {
        document.documentElement.classList.remove('light', 'dark');
        document.documentElement.classList.add(theme);
        
        localStorage.setItem('theme', theme);
    }, [theme]);

    const toggleTheme = () => {
        setTheme(prevTheme => prevTheme === 'light' ? 'dark' : 'light');
    };

    const value = useMemo(() => ({ theme, setTheme, toggleTheme }), [theme]);

    return (
        <ThemeContext.Provider value = { value }>
            {children}
        </ThemeContext.Provider>
    );
}

export const useTheme = () => {
    const context = useContext(ThemeContext);
    
    if(!context) throw new Error('useTheme must be used inside a ThemeProvider');

    return context;
}