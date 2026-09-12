import { Platform } from 'react-native';
import Constants, { ExecutionEnvironment } from 'expo-constants';

import { requestAndRegisterPushToken, unregisterCurrentPushToken } from '@/lib/push-notifications';
import { registerPushToken, unregisterPushToken } from '@/api/push-token';

jest.mock('@/api/push-token');
jest.mock('expo-constants', () => ({
	__esModule: true,
	default: {
		executionEnvironment: 'bare',
		expoConfig: { extra: { eas: { projectId: 'test-project-id' } } },
	},
	ExecutionEnvironment: { StoreClient: 'storeClient', Bare: 'bare', Standalone: 'standalone' },
}));

const mockSetNotificationHandler = jest.fn();
const mockSetNotificationChannelAsync = jest.fn();
const mockGetPermissionsAsync = jest.fn();
const mockRequestPermissionsAsync = jest.fn();
const mockGetExpoPushTokenAsync = jest.fn();

jest.mock('expo-notifications', () => ({
	setNotificationHandler: (...args: any[]) => mockSetNotificationHandler(...args),
	setNotificationChannelAsync: (...args: any[]) => mockSetNotificationChannelAsync(...args),
	getPermissionsAsync: (...args: any[]) => mockGetPermissionsAsync(...args),
	requestPermissionsAsync: (...args: any[]) => mockRequestPermissionsAsync(...args),
	getExpoPushTokenAsync: (...args: any[]) => mockGetExpoPushTokenAsync(...args),
	AndroidImportance: { DEFAULT: 3 },
}));

let mockIsDevice = true;

jest.mock('expo-device', () => ({
	get isDevice() { return mockIsDevice; },
	manufacturer: 'Google',
	modelName: 'Pixel 8',
}));

const mockedRegisterPushToken = registerPushToken as jest.MockedFunction<typeof registerPushToken>;
const mockedUnregisterPushToken = unregisterPushToken as jest.MockedFunction<typeof unregisterPushToken>;

describe('requestAndRegisterPushToken', () => {
	beforeEach(() => {
        jest.clearAllMocks();
        mockIsDevice = true;
        (Constants as any).executionEnvironment = 'bare';
        mockGetPermissionsAsync.mockResolvedValue({ status: 'granted' });
        mockGetExpoPushTokenAsync.mockResolvedValue({ data: 'ExponentPushToken[abc123]' });
    });

	it('returns null and skips setup when running in Expo Go on Android', async () => {
		Platform.OS = 'android';
		(Constants as any).executionEnvironment = ExecutionEnvironment.StoreClient;

		const result = await requestAndRegisterPushToken();

		expect(result).toBeNull();
		expect(mockedRegisterPushToken).not.toHaveBeenCalled();
	});

	it('proceeds normally when running in Expo Go on iOS', async () => {
		Platform.OS = 'ios';
		(Constants as any).executionEnvironment = ExecutionEnvironment.StoreClient;
		mockedRegisterPushToken.mockResolvedValueOnce(undefined as any);

		const result = await requestAndRegisterPushToken();

		expect(result).toBe('ExponentPushToken[abc123]');
	});

	it('returns null when not a physical device', async () => {
        Platform.OS = 'ios';
        mockIsDevice = false;

        const result = await requestAndRegisterPushToken();

        expect(result).toBeNull();
        expect(mockedRegisterPushToken).not.toHaveBeenCalled();
    });

	it('requests permission when not already granted', async () => {
		Platform.OS = 'ios';
		mockGetPermissionsAsync.mockResolvedValueOnce({ status: 'undetermined' });
		mockRequestPermissionsAsync.mockResolvedValueOnce({ status: 'granted' });
		mockedRegisterPushToken.mockResolvedValueOnce(undefined as any);

		await requestAndRegisterPushToken();

		expect(mockRequestPermissionsAsync).toHaveBeenCalled();
	});

	it('returns null when permission is denied', async () => {
		Platform.OS = 'ios';
		mockGetPermissionsAsync.mockResolvedValueOnce({ status: 'denied' });
		mockRequestPermissionsAsync.mockResolvedValueOnce({ status: 'denied' });

		const result = await requestAndRegisterPushToken();

		expect(result).toBeNull();
		expect(mockedRegisterPushToken).not.toHaveBeenCalled();
	});

	it('sets up the Android notification channel only on Android', async () => {
		Platform.OS = 'android';
		mockedRegisterPushToken.mockResolvedValueOnce(undefined as any);

		await requestAndRegisterPushToken();

		expect(mockSetNotificationChannelAsync).toHaveBeenCalledWith('default', expect.objectContaining({ name: 'default' }));
	});

	it('does not set up the Android notification channel on iOS', async () => {
		Platform.OS = 'ios';
		mockedRegisterPushToken.mockResolvedValueOnce(undefined as any);

		await requestAndRegisterPushToken();

		expect(mockSetNotificationChannelAsync).not.toHaveBeenCalled();
	});

	it('registers the token with the backend, including device name', async () => {
		Platform.OS = 'ios';
		mockedRegisterPushToken.mockResolvedValueOnce(undefined as any);

		await requestAndRegisterPushToken();

		expect(mockedRegisterPushToken).toHaveBeenCalledWith({
			token: 'ExponentPushToken[abc123]',
			deviceName: 'Google Pixel 8',
		});
	});

	it('does not throw when registering the token with the backend fails', async () => {
		Platform.OS = 'ios';
		mockedRegisterPushToken.mockRejectedValueOnce(new Error('Network error'));

		await expect(requestAndRegisterPushToken()).resolves.toBe('ExponentPushToken[abc123]');
	});
});

describe('unregisterCurrentPushToken', () => {
	beforeEach(() => {
        jest.clearAllMocks();
        mockIsDevice = true;
        (Constants as any).executionEnvironment = 'bare';
        mockGetPermissionsAsync.mockResolvedValue({ status: 'granted' });
        mockGetExpoPushTokenAsync.mockResolvedValue({ data: 'ExponentPushToken[abc123]' });
    });

	it('does nothing when push notifications are not supported', async () => {
		Platform.OS = 'android';
		(Constants as any).executionEnvironment = ExecutionEnvironment.StoreClient;

		await unregisterCurrentPushToken();

		expect(mockedUnregisterPushToken).not.toHaveBeenCalled();
	});

	it('unregisters the current token when supported', async () => {
		Platform.OS = 'ios';
		mockedUnregisterPushToken.mockResolvedValueOnce(undefined);

		await unregisterCurrentPushToken();

		expect(mockedUnregisterPushToken).toHaveBeenCalledWith('ExponentPushToken[abc123]');
	});

	it('does not throw when unregistering fails', async () => {
		Platform.OS = 'ios';
		mockedUnregisterPushToken.mockRejectedValueOnce(new Error('Network error'));

		await expect(unregisterCurrentPushToken()).resolves.toBeUndefined();
	});
});