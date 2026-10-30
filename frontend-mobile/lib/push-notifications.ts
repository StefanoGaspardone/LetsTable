import { Platform } from 'react-native';
import Constants, { ExecutionEnvironment } from 'expo-constants';

import { registerPushToken, unregisterPushToken } from '@/api/push-token';

const ANDROID_CHANNEL_ID = 'default';

export const getIsPushNotificationsSupported = () => {
	const isExpoGo = Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
	return !(isExpoGo && Platform.OS === 'android');
}

const getProjectId = (): string | undefined =>
	Constants.expoConfig?.extra?.eas?.projectId ?? Constants.easConfig?.projectId;

export const configureNotifications = async (): Promise<void> => {
	if(!getIsPushNotificationsSupported()) return;

	const Notifications = await import('expo-notifications');

	Notifications.setNotificationHandler({
		handleNotification: async () => ({
			shouldPlaySound: true,
			shouldSetBadge: false,
			shouldShowBanner: true,
			shouldShowList: true,
		}),
	});

	if(Platform.OS === 'android') {
		await Notifications.setNotificationChannelAsync(ANDROID_CHANNEL_ID, {
			name: 'Notifiche',
			importance: Notifications.AndroidImportance.HIGH,
			vibrationPattern: [0, 250, 250, 250],
			lightColor: '#C45135',
		});
	}
}

const getExpoPushToken = async (): Promise<string | null> => {
	const projectId = getProjectId();

	if(!projectId) {
		console.log('Missing EAS projectId, cannot get push token');
		return null;
	}

	const Notifications = await import('expo-notifications');
	const { data } = await Notifications.getExpoPushTokenAsync({ projectId });

	return data;
}

export const requestAndRegisterPushToken = async (): Promise<string | null> => {
	if(!getIsPushNotificationsSupported()) {
		console.log('Push notifications are not supported in Expo Go on Android. Use a development or preview build.');
		return null;
	}

	try {
		const Device = await import('expo-device');
		const Notifications = await import('expo-notifications');

		if(!Device.isDevice) {
			console.log('Push notifications require a physical device');
			return null;
		}

		await configureNotifications();

		const { status: existingStatus } = await Notifications.getPermissionsAsync();
		const finalStatus = existingStatus === 'granted'
			? existingStatus
			: (await Notifications.requestPermissionsAsync()).status;

		if(finalStatus !== 'granted') {
			console.log('Push notification permission not granted');
			return null;
		}

		const token = await getExpoPushToken();

		if(!token) return null;

		await registerPushToken({
			token,
			deviceName: `${Device.manufacturer ?? ''} ${Device.modelName ?? ''}`.trim() || null,
		});

		console.log('Push token registered:', token);
		return token;
	} catch(error) {
		console.log('Push notification registration failed:', error);
		return null;
	}
}

export const unregisterCurrentPushToken = async (): Promise<void> => {
	if(!getIsPushNotificationsSupported()) return;

	try {
		const token = await getExpoPushToken();

		if(token) await unregisterPushToken(token);
	} catch(error) {
		console.log('Failed to unregister push token:', error);
	}
}