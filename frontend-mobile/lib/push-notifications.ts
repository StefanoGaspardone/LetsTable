import { Platform } from 'react-native';
import Constants, { ExecutionEnvironment } from 'expo-constants';

import { registerPushToken, unregisterPushToken } from '@/api/push-token';

export const FRIEND_REQUEST_CATEGORY = 'friend_request';

export const NOTIFICATION_ACTIONS = {
	accept: 'accept',
	reject: 'reject',
} as const;

const NOTIFICATION_CHANNELS = [
	{ id: 'friends', name: 'Amici', description: 'Richieste di amicizia e nuove amicizie' },
	{ id: 'matches', name: 'Partite', description: 'Partite a cui hai partecipato e risultati' },
	{ id: 'wishlists', name: 'Wishlist', description: 'Novità nelle wishlist condivise' },
];

export const getIsPushNotificationsSupported = () => {
	const isExpoGo = Constants.executionEnvironment === ExecutionEnvironment.StoreClient;
	return !(isExpoGo && Platform.OS === 'android');
}

const LOCAL_COPY_PREFIX = 'local-';

const CHANNEL_BY_TYPE_PREFIX: Record<string, string> = {
	FRIEND: 'friends',
	MATCH: 'matches',
	WISHLIST: 'wishlists',
};

type Notification = import('expo-notifications').Notification;

const resolveChannelId = (notification: Notification): string | undefined => {
	const { trigger, content } = notification.request;

	if(trigger && 'remoteMessage' in trigger) {
		const channelId = (trigger.remoteMessage as { data?: Record<string, string> } | null)?.data?.channelId;
		if(channelId) return channelId;
	}

	const type = typeof content.data?.type === 'string' ? content.data.type : '';
	return CHANNEL_BY_TYPE_PREFIX[type.split('_')[0]];
}

export const setupNotificationHandler = () => {
	if(!getIsPushNotificationsSupported()) return;

	const Notifications = require('expo-notifications') as typeof import('expo-notifications');

	Notifications.setNotificationHandler({
		handleNotification: () => Promise.resolve({
			shouldPlaySound: true,
			shouldSetBadge: false,
			shouldShowBanner: true,
			shouldShowList: true,
		}),
	});

	Notifications.addNotificationReceivedListener(notification => {
		const { identifier, content } = notification.request;

		if(identifier.startsWith(LOCAL_COPY_PREFIX)) return;

		const channelId = resolveChannelId(notification);

		Notifications.scheduleNotificationAsync({
			identifier: `${LOCAL_COPY_PREFIX}${identifier}`,
			content: {
				title: content.title,
				body: content.body,
				data: content.data,
				sound: 'default',
				...(content.categoryIdentifier ? { categoryIdentifier: content.categoryIdentifier } : {}),
			},
			trigger: channelId ? { channelId } : null,
		}).catch(error => console.log('Failed to present notification:', error));
	});
}

export const configureNotifications = async (): Promise<void> => {
	if(!getIsPushNotificationsSupported()) return;

	const Notifications = await import('expo-notifications');

	if(Platform.OS === 'android') {
		await Promise.all(NOTIFICATION_CHANNELS.map(channel =>
			Notifications.setNotificationChannelAsync(channel.id, {
				name: channel.name,
				description: channel.description,
				importance: Notifications.AndroidImportance.HIGH,
				vibrationPattern: [0, 250, 250, 250],
				lightColor: '#C45135',
			})
		));
	}

	await Notifications.setNotificationCategoryAsync(FRIEND_REQUEST_CATEGORY, [
		{ identifier: NOTIFICATION_ACTIONS.accept, buttonTitle: 'Accetta', options: { opensAppToForeground: true } },
		{ identifier: NOTIFICATION_ACTIONS.reject, buttonTitle: 'Rifiuta', options: { opensAppToForeground: true, isDestructive: true } },
	]);
}

const getDevicePushToken = async (): Promise<string | null> => {
	const Notifications = await import('expo-notifications');
	const { data } = await Notifications.getDevicePushTokenAsync();

	return typeof data === 'string' ? data : null;
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

		const token = await getDevicePushToken();

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
		const token = await getDevicePushToken();

		if(token) await unregisterPushToken(token);
	} catch(error) {
		console.log('Failed to unregister push token:', error);
	}
}