import '@/global.css';

import { useEffect } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { Stack, DefaultTheme, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useFonts, PlayfairDisplay_700Bold } from '@expo-google-fonts/playfair-display';
import { PlusJakartaSans_400Regular, PlusJakartaSans_500Medium, PlusJakartaSans_600SemiBold, PlusJakartaSans_700Bold } from '@expo-google-fonts/plus-jakarta-sans';

import { AuthProvider, useAuth } from '@/contexts/auth-context';
import { ToastProvider } from '@/contexts/toast-context';
import { ConfirmDialogProvider } from '@/contexts/confirm-dialog-context';

SplashScreen.preventAutoHideAsync();

const queryClient = new QueryClient();

const RootLayoutNav = () => {
	const { isLoading } = useAuth();
	const [fontsLoaded] = useFonts({
		PlayfairDisplay_700Bold,
		PlusJakartaSans_400Regular,
		PlusJakartaSans_500Medium,
		PlusJakartaSans_600SemiBold,
		PlusJakartaSans_700Bold,
	});

	const isReady = !isLoading && fontsLoaded;

	useEffect(() => {
		if(isReady) SplashScreen.hideAsync();
	}, [isReady]);

	if(!isReady) {
		return null;
	}

	return (
		<Stack screenOptions = {{ headerShown: false }}>
			<Stack.Screen name = 'index'/>
			<Stack.Screen name = '(auth)'/>
			<Stack.Screen name = '(tabs)'/>
			<Stack.Screen name = 'browse' options = {{ presentation: 'modal' }}/>
			<Stack.Screen name = 'game/[bggId]'/>
			<Stack.Screen name = 'playlist-picker' options = {{ presentation: 'modal' }}/>
			<Stack.Screen name = 'my-wishlists' options = {{ presentation: 'modal' }}/>
			<Stack.Screen name = 'wishlist/[id]'/>
			<Stack.Screen name = 'match/[id]'/>
			<Stack.Screen name = 'match/new'/>
			<Stack.Screen name = 'match/[id]/edit'/>
			<Stack.Screen name = 'match/[id]/finish'/>
		</Stack>
	)
}

const RootLayout = () => {
	return (
		<GestureHandlerRootView style = {{ flex: 1 }}>
			<QueryClientProvider client = { queryClient }>
				<AuthProvider>
					<ToastProvider>
						<ConfirmDialogProvider>
							<ThemeProvider value = { DefaultTheme }>
								<RootLayoutNav/>
							</ThemeProvider>
						</ConfirmDialogProvider>
					</ToastProvider>
				</AuthProvider>
			</QueryClientProvider>
		</GestureHandlerRootView>
	)
}

export default RootLayout;