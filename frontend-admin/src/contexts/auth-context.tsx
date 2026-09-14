/* eslint-disable react-refresh/only-export-components */

import { createContext, useContext, useEffect, useRef, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';

import { logout, me } from '@/apis/auth';
import { clearAuthTokens } from '@/apis/axiosConfig';

import type { User } from '@/types/user';

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

	const navigate = useNavigate();
	const firstRef = useRef<boolean>(true);

	useEffect(() => {
		const fetchMe = async () => {
			try {
				const user = await me();

				if(user && user.role !== 'ADMIN') {
					setUser(null);
					clearAuthTokens();
				} else setUser(user);
			} catch {
				setUser(null);
			} finally {
				setIsLoading(false);
			}
		}

		if(firstRef.current) {
			fetchMe();
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