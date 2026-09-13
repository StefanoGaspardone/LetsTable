import { createContext, useCallback, useContext, useRef, ReactNode, useEffect } from 'react';
import { BackHandler } from 'react-native';
import { router as expoRouter } from 'expo-router';

type NavigationHref = string | { pathname: string; params?: Record<string, any> };

interface NavigationStackContextValue {
	push: (href: NavigationHref) => void;
	replace: (href: NavigationHref) => void;
	back: () => void;
	dismissAll: () => void;
}

const NavigationStackContext = createContext<NavigationStackContextValue | undefined>(undefined);

const HOME_ROUTE = '/(tabs)/home';

export const NavigationStackProvider = ({ children }: { children: ReactNode }) => {
	const stackRef = useRef<NavigationHref[]>([]);

	const push = useCallback((href: NavigationHref) => {
		stackRef.current.push(href);
		expoRouter.push(href as any);
	}, []);

	const replace = useCallback((href: NavigationHref) => {
		stackRef.current = [href];
		expoRouter.replace(href as any);
	}, []);

	const back = useCallback(() => {
		stackRef.current.pop();
		const previous = stackRef.current.at(-1);

		if(previous) {
			expoRouter.replace(previous as any);
		} else {
			expoRouter.replace(HOME_ROUTE as any);
		}
	}, []);

	const dismissAll = useCallback(() => {
		stackRef.current = [];
	}, []);

    useEffect(() => {
        const subscription = BackHandler.addEventListener('hardwareBackPress', () => {
            back();
            return true;
        });

        return () => subscription.remove();
    }, [back]);

	return (
		<NavigationStackContext.Provider value = {{ push, replace, back, dismissAll }}>
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