import '@/global.css';
import '@/lib/calendar';

import { useEffect } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { Stack, DefaultTheme, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { BottomSheetModalProvider } from '@gorhom/bottom-sheet';
import { useFonts, PlayfairDisplay_700Bold } from '@expo-google-fonts/playfair-display';
import { PlusJakartaSans_400Regular, PlusJakartaSans_500Medium, PlusJakartaSans_600SemiBold, PlusJakartaSans_700Bold } from '@expo-google-fonts/plus-jakarta-sans';

import { AuthProvider, useAuth } from '@/contexts/auth-context';
import { ToastProvider } from '@/contexts/toast-context';
import { ConfirmDialogProvider } from '@/contexts/confirm-dialog-context';

import { useHealthCheck } from '@/hooks/use-health-check';

import ServerDownOverlay from '@/components/common/server-down-overlay';

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
		</Stack>
	)
}

const RootLayout = () => {
	const { isHealthy, isChecking, retryNow } = useHealthCheck();

	return (
		<GestureHandlerRootView style = {{ flex: 1 }}>
			<BottomSheetModalProvider>
				<QueryClientProvider client = { queryClient }>
					<AuthProvider>
						<ToastProvider>
							<ConfirmDialogProvider>
								<ThemeProvider value = { DefaultTheme }>
									<RootLayoutNav/>
									{!isHealthy && <ServerDownOverlay isChecking = { isChecking } onRetry = { retryNow }/>}
								</ThemeProvider>
							</ConfirmDialogProvider>
						</ToastProvider>
					</AuthProvider>
				</QueryClientProvider>
			</BottomSheetModalProvider>
		</GestureHandlerRootView>
	)
}

export default RootLayout;