import { Platform } from 'react-native';
import Constants, { ExecutionEnvironment } from 'expo-constants';

import { registerPushToken, unregisterPushToken } from '@/api/push-token';

const getIsPushNotificationsSupported = () => {
	const isExpoGo = Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
	return !(isExpoGo && Platform.OS === 'android');
}

export const requestAndRegisterPushToken = async (): Promise<string | null> => {
	if(!getIsPushNotificationsSupported()) {
		console.log('Push notifications are not supported in Expo Go on Android (SDK 53+). Use a development build to test them.');
		return null;
	}

	const Device = await import('expo-device');
	const Notifications = await import('expo-notifications');

	Notifications.setNotificationHandler({
		handleNotification: async () => ({
			shouldShowAlert: true,
			shouldPlaySound: true,
			shouldSetBadge: false,
			shouldShowBanner: true,
			shouldShowList: true,
		}),
	});

	if(!Device.isDevice) {
		console.log('Push notifications require a physical device');
		return null;
	}

	if(Platform.OS === 'android') {
		await Notifications.setNotificationChannelAsync('default', {
			name: 'default',
			importance: Notifications.AndroidImportance.DEFAULT,
			vibrationPattern: [0, 250, 250, 250],
			lightColor: '#C45135',
		});
	}

	const { status: existingStatus } = await Notifications.getPermissionsAsync();
	let finalStatus = existingStatus;

	if(existingStatus !== 'granted') {
		const { status } = await Notifications.requestPermissionsAsync();
		finalStatus = status;
	}

	if(finalStatus !== 'granted') {
		console.log('Push notification permission not granted');
		return null;
	}

	const projectId = Constants.expoConfig?.extra?.eas?.projectId;
	const tokenResponse = await Notifications.getExpoPushTokenAsync(
		projectId ? { projectId } : undefined
	);
	const expoPushToken = tokenResponse.data;

	try {
		await registerPushToken({
			token: expoPushToken,
			deviceName: `${Device.manufacturer ?? ''} ${Device.modelName ?? ''}`.trim() || null,
		});
	} catch(error) {
		console.log('Failed to register push token:', error);
	}

	return expoPushToken;
}

export const unregisterCurrentPushToken = async (): Promise<void> => {
	if(!getIsPushNotificationsSupported()) return;

	try {
		const Notifications = await import('expo-notifications');
		const tokenResponse = await Notifications.getExpoPushTokenAsync();
		await unregisterPushToken(tokenResponse.data);
	} catch(error) {
		console.log('Failed to unregister push token:', error);
	}
}