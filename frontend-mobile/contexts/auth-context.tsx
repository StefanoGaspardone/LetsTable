import { createContext, useContext, useEffect, useState, ReactNode } from 'react';

import { apiClient } from '@/api/client';

import { tokenStorage } from '@/lib/token-storage';
import axios from 'axios';

interface User {
	id: string;
	username: string;
	email: string;
	role: string;
}

interface AuthContextValue {
	user: User | null;
	isLoading: boolean;
	isAuthenticated: boolean;
	login: (accessToken: string, refreshToken: string, user: User) => Promise<void>;
	logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
	const [user, setUser] = useState<User | null>(null);
	const [isLoading, setIsLoading] = useState(true);

	useEffect(() => {
		restoreSession();
	}, []);

	const restoreSession = async () => {
		try {
			const accessToken = await tokenStorage.getAccessToken();
			if(!accessToken) {
				setIsLoading(false);
				return;
			}

			const { data } = await apiClient.get<User>('/users/me');
			setUser(data);
		} catch(error) {
			console.log('RESTORE SESSION FAILED:', error);
			if (axios.isAxiosError(error)) {
				console.log('RESTORE SESSION ERROR STATUS:', error.response?.status);
				console.log('RESTORE SESSION ERROR MESSAGE:', error.message);
			}

			await tokenStorage.clearTokens();
			setUser(null);
		} finally {
			setIsLoading(false);
		}
	}

	const login = async (accessToken: string, refreshToken: string, loggedInUser: User) => {
		await tokenStorage.setTokens(accessToken, refreshToken);
		setUser(loggedInUser);
	}

	const logout = async () => {
		const refreshToken = await tokenStorage.getRefreshToken();
		
        try {
			if(refreshToken) {
				await apiClient.post('/auth/logout', { refreshToken });
			}
		} catch {
			// swallow
		} finally {
			await tokenStorage.clearTokens();
			setUser(null);
		}
	}

	return (
		<AuthContext.Provider value = {{ user, isLoading, isAuthenticated: !!user, login, logout }}>
			{children}
		</AuthContext.Provider>
	);
}

export function useAuth() {
	const context = useContext(AuthContext);
	
    if(!context) {
		throw new Error('useAuth must be used within an AuthProvider');
	}
	
    return context;
}