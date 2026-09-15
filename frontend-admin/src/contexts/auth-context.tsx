/* eslint-disable react-hooks/set-state-in-effect */
/* eslint-disable react-refresh/only-export-components */

import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';

import { logout } from '@/apis/auth';
import axiosInstance, { clearAuthTokens, getAccessToken } from '@/apis/axiosConfig';

import type { User } from '@/types/user';
import axios from 'axios';

interface AuthContextType {
	user: User | null;
	setUser: (user: User | null) => void;
	logout: () => void;
	isLoading: boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider = ({ children }: { children: ReactNode }) => {
	const [user, setUser] = useState<User | null>(null);
	const [isLoading, setIsLoading] = useState<boolean>(true);

	const firstRef = useRef<boolean>(true);

	const navigate = useNavigate();

	const restoreSession = async () => {
		try {
			const accessToken = getAccessToken();
			
			if(!accessToken) {
				setIsLoading(false);
				return;
			}

			const { data } = await axiosInstance.get<User>('/users/me');
			setUser(data);
		} catch(error) {
			console.log('RESTORE SESSION FAILED:', error);
			
			if(axios.isAxiosError(error)) {
				console.log('RESTORE SESSION ERROR STATUS:', error.response?.status);
				console.log('RESTORE SESSION ERROR MESSAGE:', error.message);
			}

			clearAuthTokens();
			setUser(null);
		} finally {
			setIsLoading(false);
		}
	}

	useEffect(() => {
		if(firstRef.current) {
			restoreSession();
			firstRef.current = false;
		}
	}, []);

	const handleLogout = async () => {
		try {
			await logout();
		} catch {
			// swallow
		} finally {
			setUser(null);
			clearAuthTokens();

			navigate('/');
		}
	}

	return (
		<AuthContext.Provider value = {{ user, setUser, logout: handleLogout, isLoading }}>
			{children}
		</AuthContext.Provider>
	)
}

export const useAuth = () => {
	const context = useContext(AuthContext);

	if(context === undefined) throw new Error('useAuth must be used inside an AuthProvider');
    
	return context;
}