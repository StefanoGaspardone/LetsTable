import { Platform } from 'react-native';
import Constants, { ExecutionEnvironment } from 'expo-constants';

import {
	configureNotifications,
	FRIEND_REQUEST_CATEGORY,
	getIsPushNotificationsSupported,
	NOTIFICATION_ACTIONS,
	requestAndRegisterPushToken,
	setupNotificationHandler,
	unregisterCurrentPushToken,
} from '@/lib/push-notifications';
import { registerPushToken, unregisterPushToken } from '@/api/push-token';

jest.mock('@/api/push-token');

jest.mock('expo-constants', () => ({
	__esModule: true,
	default: {
		executionEnvironment: 'bare',
	},
	ExecutionEnvironment: { StoreClient: 'storeClient', Bare: 'bare', Standalone: 'standalone' },
}));

const mockSetNotificationHandler = jest.fn();
const mockSetNotificationChannelAsync = jest.fn();
const mockSetNotificationCategoryAsync = jest.fn();
const mockGetPermissionsAsync = jest.fn();
const mockRequestPermissionsAsync = jest.fn();
const mockGetDevicePushTokenAsync = jest.fn();
const mockAddNotificationReceivedListener = jest.fn();
const mockScheduleNotificationAsync = jest.fn();

jest.mock('expo-notifications', () => ({
	setNotificationHandler: (...args: any[]) => mockSetNotificationHandler(...args),
	setNotificationChannelAsync: (...args: any[]) => mockSetNotificationChannelAsync(...args),
	setNotificationCategoryAsync: (...args: any[]) => mockSetNotificationCategoryAsync(...args),
	getPermissionsAsync: (...args: any[]) => mockGetPermissionsAsync(...args),
	requestPermissionsAsync: (...args: any[]) => mockRequestPermissionsAsync(...args),
	getDevicePushTokenAsync: (...args: any[]) => mockGetDevicePushTokenAsync(...args),
	addNotificationReceivedListener: (...args: any[]) => mockAddNotificationReceivedListener(...args),
	scheduleNotificationAsync: (...args: any[]) => mockScheduleNotificationAsync(...args),
	AndroidImportance: { HIGH: 4 },
}));

let mockIsDevice = true;
let mockManufacturer: string | null = 'Google';
let mockModelName: string | null = 'Pixel 8';

jest.mock('expo-device', () => ({
	get isDevice() { return mockIsDevice; },
	get manufacturer() { return mockManufacturer; },
	get modelName() { return mockModelName; },
}));

const mockedRegisterPushToken = jest.mocked(registerPushToken);
const mockedUnregisterPushToken = jest.mocked(unregisterPushToken);

const FCM_TOKEN = 'fcm-device-token';

const setEnvironment = (os: 'android' | 'ios', executionEnvironment: string = ExecutionEnvironment.Bare) => {
	Platform.OS = os;
	(Constants as any).executionEnvironment = executionEnvironment;
}

const buildNotification = (overrides: { identifier?: string; data?: Record<string, unknown>; categoryIdentifier?: string | null; remoteData?: Record<string, string> | null }) => ({
	request: {
		identifier: overrides.identifier ?? 'remote-1',
		content: {
			title: 'Nuova richiesta di amicizia',
			body: 'elena ti ha inviato una richiesta di amicizia',
			data: overrides.data ?? { type: 'FRIEND_REQUEST', requestId: 'req-1' },
			categoryIdentifier: overrides.categoryIdentifier === undefined ? FRIEND_REQUEST_CATEGORY : overrides.categoryIdentifier,
		},
		trigger: overrides.remoteData === null ? { type: 'push' } : { type: 'push', remoteMessage: { data: overrides.remoteData ?? { channelId: 'friends' } } },
	},
}) as any;

describe('push-notifications', () => {
	beforeEach(() => {
		jest.clearAllMocks();
		mockIsDevice = true;
		mockManufacturer = 'Google';
		mockModelName = 'Pixel 8';
		setEnvironment('android');

		mockGetPermissionsAsync.mockResolvedValue({ status: 'granted' });
		mockGetDevicePushTokenAsync.mockResolvedValue({ type: 'android', data: FCM_TOKEN });
		mockSetNotificationChannelAsync.mockResolvedValue(null);
		mockSetNotificationCategoryAsync.mockResolvedValue(null);
		mockScheduleNotificationAsync.mockResolvedValue('local-id');
		mockedRegisterPushToken.mockResolvedValue(undefined as any);
		mockedUnregisterPushToken.mockResolvedValue(undefined as any);
	});

	describe('getIsPushNotificationsSupported', () => {
		it('is false in Expo Go on Android', () => {
			setEnvironment('android', ExecutionEnvironment.StoreClient);
			expect(getIsPushNotificationsSupported()).toBe(false);
		});

		it('is true in Expo Go on iOS', () => {
			setEnvironment('ios', ExecutionEnvironment.StoreClient);
			expect(getIsPushNotificationsSupported()).toBe(true);
		});

		it('is true in a development or production build on Android', () => {
			setEnvironment('android', ExecutionEnvironment.Bare);
			expect(getIsPushNotificationsSupported()).toBe(true);
		});
	});

	describe('configureNotifications', () => {
		it('creates the friends, wishlists and achievements channels with high importance on Android', async () => {
			await configureNotifications();

			expect(mockSetNotificationChannelAsync).toHaveBeenCalledTimes(3);
			['friends', 'wishlists', 'achievements'].forEach(channelId => {
				expect(mockSetNotificationChannelAsync).toHaveBeenCalledWith(channelId, expect.objectContaining({ importance: 4, lightColor: '#C45135' }));
			});
		});

		it('does not create channels on iOS', async () => {
			setEnvironment('ios');

			await configureNotifications();

			expect(mockSetNotificationChannelAsync).not.toHaveBeenCalled();
		});

		it('registers the friend request category with accept and reject actions', async () => {
			await configureNotifications();

			expect(mockSetNotificationCategoryAsync).toHaveBeenCalledWith(FRIEND_REQUEST_CATEGORY, [
				expect.objectContaining({ identifier: NOTIFICATION_ACTIONS.accept, buttonTitle: 'Accetta', options: { opensAppToForeground: true } }),
				expect.objectContaining({ identifier: NOTIFICATION_ACTIONS.reject, buttonTitle: 'Rifiuta', options: { opensAppToForeground: true, isDestructive: true } }),
			]);
		});

		it('does nothing when push notifications are not supported', async () => {
			setEnvironment('android', ExecutionEnvironment.StoreClient);

			await configureNotifications();

			expect(mockSetNotificationChannelAsync).not.toHaveBeenCalled();
			expect(mockSetNotificationCategoryAsync).not.toHaveBeenCalled();
		});
	});

	describe('requestAndRegisterPushToken', () => {
		it('returns null and skips setup when running in Expo Go on Android', async () => {
			setEnvironment('android', ExecutionEnvironment.StoreClient);
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			const result = await requestAndRegisterPushToken();

			expect(result).toBeNull();
			expect(mockGetDevicePushTokenAsync).not.toHaveBeenCalled();
			expect(mockedRegisterPushToken).not.toHaveBeenCalled();
			consoleLog.mockRestore();
		});

		it('returns null when not a physical device', async () => {
			mockIsDevice = false;
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			const result = await requestAndRegisterPushToken();

			expect(result).toBeNull();
			expect(mockedRegisterPushToken).not.toHaveBeenCalled();
			consoleLog.mockRestore();
		});

		it('requests permission when not already granted', async () => {
			mockGetPermissionsAsync.mockResolvedValueOnce({ status: 'undetermined' });
			mockRequestPermissionsAsync.mockResolvedValueOnce({ status: 'granted' });
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			const result = await requestAndRegisterPushToken();

			expect(mockRequestPermissionsAsync).toHaveBeenCalled();
			expect(result).toBe(FCM_TOKEN);
			consoleLog.mockRestore();
		});

		it('does not ask for permission again when it is already granted', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			await requestAndRegisterPushToken();

			expect(mockRequestPermissionsAsync).not.toHaveBeenCalled();
			consoleLog.mockRestore();
		});

		it('returns null when permission is denied', async () => {
			mockGetPermissionsAsync.mockResolvedValueOnce({ status: 'denied' });
			mockRequestPermissionsAsync.mockResolvedValueOnce({ status: 'denied' });
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			const result = await requestAndRegisterPushToken();

			expect(result).toBeNull();
			expect(mockGetDevicePushTokenAsync).not.toHaveBeenCalled();
			expect(mockedRegisterPushToken).not.toHaveBeenCalled();
			consoleLog.mockRestore();
		});

		it('configures channels and categories before registering', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			await requestAndRegisterPushToken();

			expect(mockSetNotificationChannelAsync).toHaveBeenCalled();
			expect(mockSetNotificationCategoryAsync).toHaveBeenCalled();
			consoleLog.mockRestore();
		});

		it('registers the native device token with the device name and returns it', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			const result = await requestAndRegisterPushToken();

			expect(result).toBe(FCM_TOKEN);
			expect(mockedRegisterPushToken).toHaveBeenCalledWith({
				token: FCM_TOKEN,
				deviceName: 'Google Pixel 8',
			});
			consoleLog.mockRestore();
		});

		it('sends a null device name when manufacturer and model are unknown', async () => {
			mockManufacturer = null;
			mockModelName = null;
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			await requestAndRegisterPushToken();

			expect(mockedRegisterPushToken).toHaveBeenCalledWith({ token: FCM_TOKEN, deviceName: null });
			consoleLog.mockRestore();
		});

		it('returns null without registering when the device token is not a string', async () => {
			mockGetDevicePushTokenAsync.mockResolvedValueOnce({ type: 'web', data: { endpoint: 'x' } });

			const result = await requestAndRegisterPushToken();

			expect(result).toBeNull();
			expect(mockedRegisterPushToken).not.toHaveBeenCalled();
		});

		it('returns null without throwing when registering the token with the backend fails', async () => {
			mockedRegisterPushToken.mockRejectedValueOnce(new Error('Network error'));
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			await expect(requestAndRegisterPushToken()).resolves.toBeNull();

			expect(consoleLog).toHaveBeenCalledWith('Push notification registration failed:', expect.any(Error));
			consoleLog.mockRestore();
		});
	});

	describe('unregisterCurrentPushToken', () => {
		it('does nothing when push notifications are not supported', async () => {
			setEnvironment('android', ExecutionEnvironment.StoreClient);

			await unregisterCurrentPushToken();

			expect(mockedUnregisterPushToken).not.toHaveBeenCalled();
		});

		it('unregisters the current device token', async () => {
			await unregisterCurrentPushToken();

			expect(mockedUnregisterPushToken).toHaveBeenCalledWith(FCM_TOKEN);
		});

		it('does not throw when unregistering fails', async () => {
			mockedUnregisterPushToken.mockRejectedValueOnce(new Error('Network error'));
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});

			await expect(unregisterCurrentPushToken()).resolves.toBeUndefined();

			expect(consoleLog).toHaveBeenCalledWith('Failed to unregister push token:', expect.any(Error));
			consoleLog.mockRestore();
		});
	});

	describe('setupNotificationHandler', () => {
		const setupAndGetListener = () => {
			setupNotificationHandler();
			return mockAddNotificationReceivedListener.mock.calls[0][0] as (notification: any) => void;
		}

		it('does nothing when push notifications are not supported', () => {
			setEnvironment('android', ExecutionEnvironment.StoreClient);

			setupNotificationHandler();

			expect(mockSetNotificationHandler).not.toHaveBeenCalled();
			expect(mockAddNotificationReceivedListener).not.toHaveBeenCalled();
		});

		it('shows notifications with banner, list and sound while the app is in the foreground', async () => {
			setupNotificationHandler();

			const { handleNotification } = mockSetNotificationHandler.mock.calls[0][0];

			await expect(handleNotification(buildNotification({}))).resolves.toEqual({
				shouldPlaySound: true,
				shouldSetBadge: false,
				shouldShowBanner: true,
				shouldShowList: true,
			});
		});

		it('presents a local copy of a remote notification on the channel sent by the backend', () => {
			const listener = setupAndGetListener();

			listener(buildNotification({ identifier: 'remote-1', remoteData: { channelId: 'friends' } }));

			expect(mockScheduleNotificationAsync).toHaveBeenCalledWith({
				identifier: 'local-remote-1',
				content: {
					title: 'Nuova richiesta di amicizia',
					body: 'elena ti ha inviato una richiesta di amicizia',
					data: { type: 'FRIEND_REQUEST', requestId: 'req-1' },
					sound: 'default',
					categoryIdentifier: FRIEND_REQUEST_CATEGORY,
				},
				trigger: { channelId: 'friends' },
			});
		});

		it.each([
			['WISHLIST_UPDATED', 'wishlists'],
			['ACHIEVEMENT_UNLOCKED', 'achievements'],
			['FRIEND_ACCEPTED', 'friends'],
		])('falls back to the channel of the %s type when the backend sends none', (type, channelId) => {
			const listener = setupAndGetListener();

			listener(buildNotification({ data: { type }, categoryIdentifier: null, remoteData: {} }));

			expect(mockScheduleNotificationAsync).toHaveBeenCalledWith(expect.objectContaining({
				trigger: { channelId },
			}));
		});

		it('uses an immediate trigger without channel for unknown types', () => {
			const listener = setupAndGetListener();

			listener(buildNotification({ data: { type: 'SOMETHING_ELSE' }, categoryIdentifier: null, remoteData: null }));

			expect(mockScheduleNotificationAsync).toHaveBeenCalledWith(expect.objectContaining({ trigger: null }));
		});

		it('omits the category when the notification has none', () => {
			const listener = setupAndGetListener();

			listener(buildNotification({ data: { type: 'FRIEND_ACCEPTED' }, categoryIdentifier: null }));

			const { content } = mockScheduleNotificationAsync.mock.calls[0][0];
			expect(content).not.toHaveProperty('categoryIdentifier');
		});

		it('ignores its own local copies to avoid presenting them twice', () => {
			const listener = setupAndGetListener();

			listener(buildNotification({ identifier: 'local-remote-1' }));

			expect(mockScheduleNotificationAsync).not.toHaveBeenCalled();
		});

		it('logs when presenting the local copy fails', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
			mockScheduleNotificationAsync.mockRejectedValueOnce(new Error('Schedule failed'));
			const listener = setupAndGetListener();

			listener(buildNotification({}));
			await Promise.resolve();
			await Promise.resolve();

			expect(consoleLog).toHaveBeenCalledWith('Failed to present notification:', expect.any(Error));
			consoleLog.mockRestore();
		});
	});
});