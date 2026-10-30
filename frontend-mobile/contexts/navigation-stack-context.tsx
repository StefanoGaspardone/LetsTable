import { createContext, useCallback, useContext, useEffect, useMemo, useRef, ReactNode } from 'react';
import { BackHandler } from 'react-native';
import { router as expoRouter } from 'expo-router';

import { useAuth } from '@/contexts/auth-context';

type NavigationHref = string | { pathname: string; params?: Record<string, any> };

interface NavigationStackContextValue {
	push: (href: NavigationHref) => void;
	replace: (href: NavigationHref) => void;
	back: () => void;
	dismissAll: () => void;
}

const NavigationStackContext = createContext<NavigationStackContextValue | undefined>(undefined);

const HOME_ROUTE = '/(tabs)/home';
const WELCOME_ROUTE = '/(auth)/welcome';

const isRootRoute = (href: NavigationHref) => href === HOME_ROUTE || href === WELCOME_ROUTE;

export const NavigationStackProvider = ({ children }: { children: ReactNode }) => {
	const { isAuthenticated } = useAuth();

	const stackRef = useRef<NavigationHref[]>([]);
	const isAuthenticatedRef = useRef(isAuthenticated);
	isAuthenticatedRef.current = isAuthenticated;

	const push = useCallback((href: NavigationHref) => {
		stackRef.current.push(href);
		expoRouter.push(href as any);
	}, []);

	const replace = useCallback((href: NavigationHref) => {
		stackRef.current = isRootRoute(href) ? [] : [href];
		expoRouter.replace(href as any);
	}, []);

	const back = useCallback(() => {
		stackRef.current.pop();

		const previous = stackRef.current.at(-1);
		const fallback = isAuthenticatedRef.current ? HOME_ROUTE : WELCOME_ROUTE;

		expoRouter.replace((previous ?? fallback) as any);
	}, []);

	const dismissAll = useCallback(() => {
		stackRef.current = [];
	}, []);

	useEffect(() => {
		const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
			if(stackRef.current.length === 0) return false;

			back();
			return true;
		});

		return () => subscription.remove();
	}, [back]);

	const value = useMemo(() => ({ push, replace, back, dismissAll }), [push, replace, back, dismissAll]);

	return (
		<NavigationStackContext.Provider value = { value }>
			{children}
		</NavigationStackContext.Provider>
	)
}

export const useNavigationStack = () => {
	const context = useContext(NavigationStackContext);

	if(!context) {
		throw new Error('useNavigationStack must be used within a NavigationStackProvider');
	}

	return context;
}