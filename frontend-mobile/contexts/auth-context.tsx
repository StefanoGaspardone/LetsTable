import { createContext, useContext, useEffect, useState, ReactNode } from 'react';
import axios from 'axios';

import { apiClient } from '@/api/client';

import { tokenStorage } from '@/lib/token-storage';
import { requestAndRegisterPushToken, unregisterCurrentPushToken } from '@/lib/push-notifications';

import { User } from '@/types/user';

interface AuthContextValue {
	user: User | null;
	isLoading: boolean;
	isAuthenticated: boolean;
	login: (accessToken: string, refreshToken: string, user: User) => Promise<void>;
	logout: () => Promise<void>;
	updateUser: (updatedUser: User) => void;
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
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
			
			requestAndRegisterPushToken();
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
		
		requestAndRegisterPushToken();
	}

	const logout = async () => {
		const refreshToken = await tokenStorage.getRefreshToken();

		await unregisterCurrentPushToken();

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

	const updateUser = (updatedUser: User) => {
		setUser(updatedUser);
	}

	return (
		<AuthContext.Provider value = {{ user, isLoading, isAuthenticated: !!user, login, logout, updateUser }}>
			{children}
		</AuthContext.Provider>
	)
}

export const useAuth = () => {
	const context = useContext(AuthContext);
	
    if(!context) {
		throw new Error('useAuth must be used within an AuthProvider');
	}
	
    return context;
}