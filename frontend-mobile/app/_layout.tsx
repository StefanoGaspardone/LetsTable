import '@/global.css';

import { useEffect } from 'react';
import { DarkTheme, DefaultTheme, ThemeProvider, Stack } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { useColorScheme } from 'react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { AuthProvider, useAuth } from '@/contexts/auth-context';

SplashScreen.preventAutoHideAsync();

const queryClient = new QueryClient();

const RootLayoutNav = () => {
	const { isLoading } = useAuth();

	useEffect(() => {
		if(!isLoading) {
			SplashScreen.hideAsync();
		}
	}, [isLoading]);

	if(isLoading) {
		return null;
	}

	return (
		<Stack screenOptions={{ headerShown: false }}>
			<Stack.Screen name = 'index'/>
			<Stack.Screen name = '(auth)'/>
			<Stack.Screen name = '(tabs)'/>
			<Stack.Screen name = 'browse' options={{ presentation: 'modal' }}/>
			<Stack.Screen name = 'game/[bggId]'/>
			<Stack.Screen name = 'playlist-picker' options={{ presentation: 'modal' }}/>
			<Stack.Screen name = 'my-wishlists' options={{ presentation: 'modal' }}/>
			<Stack.Screen name = 'wishlist/[id]'/>
			<Stack.Screen name = 'match/[id]'/>
			<Stack.Screen name = 'match/new'/>
			<Stack.Screen name = 'match/[id]/edit'/>
			<Stack.Screen name = 'match/[id]/finish'/>
		</Stack>
	)
}

const RootLayout = () => {
	const colorScheme = useColorScheme();

	return (
		<QueryClientProvider client = { queryClient }>
			<AuthProvider>
				<ThemeProvider value = { colorScheme === 'dark' ? DarkTheme : DefaultTheme }>
					<RootLayoutNav/>
				</ThemeProvider>
			</AuthProvider>
		</QueryClientProvider>
	)
}

export default RootLayout;