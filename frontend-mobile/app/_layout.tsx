import '@/global.css';
import '@/lib/calendar';

import { useEffect } from 'react';
import { GestureHandlerRootView } from 'react-native-gesture-handler';
import { Stack, ThemeProvider } from 'expo-router';
import * as SplashScreen from 'expo-splash-screen';
import { KeyboardAvoidingView, Platform, useColorScheme } from 'react-native';
import { QueryClientProvider } from '@tanstack/react-query';
import { BottomSheetModalProvider } from '@gorhom/bottom-sheet';
import { useFonts, PlayfairDisplay_700Bold } from '@expo-google-fonts/playfair-display';
import { PlusJakartaSans_400Regular, PlusJakartaSans_500Medium, PlusJakartaSans_600SemiBold, PlusJakartaSans_700Bold } from '@expo-google-fonts/plus-jakarta-sans';

import { AuthProvider, useAuth } from '@/contexts/auth-context';
import { ToastProvider } from '@/contexts/toast-context';
import { ConfirmDialogProvider } from '@/contexts/confirm-dialog-context';
import { ThemeProvider as AppThemeProvider, useTheme as useAppTheme } from '@/contexts/theme-context';
import { NavigationStackProvider } from '@/contexts/navigation-stack-context';

import AchievementCelebrationHost from '@/components/achievements/achievement-celebration-host';
import ServerDownOverlay from '@/components/common/server-down-overlay';

import { useHealthCheck } from '@/hooks/use-health-check';
import { useNotificationNavigation } from '@/hooks/use-notification-navigation';

import { configureNotifications } from '@/lib/push-notifications';
import { queryClient } from '@/lib/query-client';
import { NAV_THEME } from '@/lib/theme';

SplashScreen.preventAutoHideAsync();

const RootLayoutNav = () => {
	const { isLoading, isAuthenticated } = useAuth();
	const { themePreference, isLoading: isThemeLoading } = useAppTheme();
	const systemColorScheme = useColorScheme();
	const [fontsLoaded] = useFonts({
		PlayfairDisplay_700Bold,
		PlusJakartaSans_400Regular,
		PlusJakartaSans_500Medium,
		PlusJakartaSans_600SemiBold,
		PlusJakartaSans_700Bold,
	});

	const isReady = !isLoading && !isThemeLoading && fontsLoaded;

	useNotificationNavigation(isReady && isAuthenticated);

	useEffect(() => {
		if(isReady) SplashScreen.hideAsync();
	}, [isReady]);

	if(!isReady) {
		return null;
	}

	const resolvedScheme = themePreference === 'system' ? systemColorScheme : themePreference;
	const navigationTheme = NAV_THEME[resolvedScheme === 'dark' ? 'dark' : 'light'];

	return (
		<KeyboardAvoidingView style = {{ flex: 1 }} behavior = { Platform.OS === 'ios' ? 'padding' : 'height' }>
			<ThemeProvider value = { navigationTheme }>
				<Stack screenOptions = {{ headerShown: false }}>
					<Stack.Screen name = 'index'/>
					<Stack.Screen name = '(auth)'/>
					<Stack.Screen name = '(tabs)'/>
				</Stack>
			</ThemeProvider>
		</KeyboardAvoidingView>
	)
}

const RootLayout = () => {
	const { isHealthy, isChecking, retryNow } = useHealthCheck();

	useEffect(() => {
		configureNotifications().catch(error => console.log('Failed to configure notifications:', error));
	}, []);

	return (
		<GestureHandlerRootView style = {{ flex: 1 }}>
			<QueryClientProvider client = { queryClient }>
				<AuthProvider>
					<AppThemeProvider>
						<ToastProvider>
							<ConfirmDialogProvider>
								<NavigationStackProvider>
									<BottomSheetModalProvider>
										<RootLayoutNav/>
										<AchievementCelebrationHost/>
										{!isHealthy && <ServerDownOverlay isChecking = { isChecking } onRetry = { retryNow }/>}
									</BottomSheetModalProvider>
								</NavigationStackProvider>
							</ConfirmDialogProvider>
						</ToastProvider>
					</AppThemeProvider>
				</AuthProvider>
			</QueryClientProvider>
		</GestureHandlerRootView>
	)
}

export default RootLayout;