import { createContext, useCallback, useContext, useEffect, useMemo, useState, ReactNode } from 'react';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { isAxiosError } from 'axios';
import { useQueryClient } from '@tanstack/react-query';

import { setSessionExpiredHandler } from '@/api/client';
import { logout as logoutRequest } from '@/api/auth';
import { getMe } from '@/api/user';

import { tokenStorage } from '@/lib/token-storage';
import { requestAndRegisterPushToken, unregisterCurrentPushToken } from '@/lib/push-notifications';

import { User } from '@/types/user';

const USER_STORAGE_KEY = '@auth_user';

import { syncWidgets } from '@/widgets/widget-registry';

const readCachedUser = async (): Promise<User | null> => {
	try {
		const stored = await AsyncStorage.getItem(USER_STORAGE_KEY);
		return stored ? JSON.parse(stored) as User : null;
	} catch {
		return null;
	}
}

const writeCachedUser = (user: User | null) => {
	const operation = user ? AsyncStorage.setItem(USER_STORAGE_KEY, JSON.stringify(user)) : AsyncStorage.removeItem(USER_STORAGE_KEY);
	operation.catch(error => console.log('Failed to persist user:', error));
}

const isRejectedByServer = (error: unknown) => {
	const status = isAxiosError(error) ? error.response?.status : undefined;
	return !!status && status < 500;
}

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
	const queryClient = useQueryClient();

	const [user, setUser] = useState<User | null>(null);
	const [isLoading, setIsLoading] = useState(true);

	const applyUser = useCallback((nextUser: User | null) => {
		setUser(nextUser);
		writeCachedUser(nextUser);
	}, []);

	const clearSession = useCallback(() => {
		applyUser(null);
		queryClient.clear();
		syncWidgets(null).catch(error => console.log('Failed to reset widgets:', error));
	}, [applyUser, queryClient]);

	useEffect(() => {
		setSessionExpiredHandler(clearSession);
		return () => setSessionExpiredHandler(null);
	}, [clearSession]);

	useEffect(() => {
		const restoreSession = async () => {
			try {
				const accessToken = await tokenStorage.getAccessToken();
				if(!accessToken) return;

				const cachedUser = await readCachedUser();

				if(cachedUser) {
					setUser(cachedUser);
					setIsLoading(false);
				}

				const freshUser = await getMe();
				applyUser(freshUser);

				requestAndRegisterPushToken();
			} catch(error) {
				console.log('Failed to restore session:', error);

				if(isRejectedByServer(error)) {
					await tokenStorage.clearTokens();
					clearSession();
				}
			} finally {
				setIsLoading(false);
			}
		}

		restoreSession();
	}, []);

	const login = useCallback(async (accessToken: string, refreshToken: string, loggedInUser: User) => {
		await tokenStorage.setTokens(accessToken, refreshToken);
		applyUser(loggedInUser);

		requestAndRegisterPushToken();
	}, [applyUser]);

	const logout = useCallback(async () => {
		const refreshToken = await tokenStorage.getRefreshToken();

		await unregisterCurrentPushToken().catch(error => console.log('Failed to unregister push token:', error));

		if(refreshToken) {
			await logoutRequest(refreshToken).catch(error => console.log('Failed to revoke refresh token:', error));
		}

		await tokenStorage.clearTokens();
		clearSession();
	}, [clearSession]);

	const value = useMemo(() => ({
		user,
		isLoading,
		isAuthenticated: !!user,
		login,
		logout,
		updateUser: applyUser,
	}), [user, isLoading, login, logout, applyUser]);

	return (
		<AuthContext.Provider value = { value }>
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