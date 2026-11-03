import { renderHook, waitFor, act } from '@testing-library/react-native';
import * as Notifications from 'expo-notifications';

import { useNotificationNavigation } from '@/hooks/use-notification-navigation';

jest.mock('expo-notifications', () => ({
	getLastNotificationResponseAsync: jest.fn(),
	addNotificationResponseReceivedListener: jest.fn(),
	clearLastNotificationResponseAsync: jest.fn(),
	dismissNotificationAsync: jest.fn(),
}));

const mockPush = jest.fn();
const mockShowToast = jest.fn();
const mockAccept = jest.fn();
const mockReject = jest.fn();

jest.mock('@/contexts/navigation-stack-context', () => ({
	useNavigationStack: () => ({ push: mockPush }),
}));

jest.mock('@/contexts/toast-context', () => ({
	useToast: () => ({ showToast: mockShowToast }),
}));

jest.mock('@/hooks/use-friend', () => ({
	useAcceptFriendRequest: () => ({ mutateAsync: mockAccept }),
	useRejectFriendRequest: () => ({ mutateAsync: mockReject }),
}));

jest.mock('@/lib/push-notifications', () => ({
	NOTIFICATION_ACTIONS: { accept: 'accept', reject: 'reject' },
}));

const mockedGetLastResponse = jest.mocked(Notifications.getLastNotificationResponseAsync);
const mockedAddResponseListener = jest.mocked(Notifications.addNotificationResponseReceivedListener);
const mockedClearLastResponse = jest.mocked(Notifications.clearLastNotificationResponseAsync);
const mockedDismissNotification = jest.mocked(Notifications.dismissNotificationAsync);

const DEFAULT_ACTION = 'expo.modules.notifications.actions.DEFAULT';

const buildResponse = (data: Record<string, unknown>, actionIdentifier = DEFAULT_ACTION, identifier = 'notification-1') => ({
	actionIdentifier,
	notification: {
		request: {
			identifier,
			content: { data },
		},
	},
}) as any;

let responseListener: ((response: any) => void) | null = null;
const mockRemoveListener = jest.fn();

const emit = async (response: any) => {
	await act(async () => {
		responseListener?.(response);
	});
}

describe('useNotificationNavigation', () => {
	beforeEach(() => {
		jest.clearAllMocks();

		responseListener = null;
		mockedGetLastResponse.mockResolvedValue(null);
		mockedClearLastResponse.mockResolvedValue(undefined as any);
		mockedDismissNotification.mockResolvedValue(undefined);
		mockedAddResponseListener.mockImplementation(listener => {
			responseListener = listener;
			return { remove: mockRemoveListener } as any;
		});
		mockAccept.mockResolvedValue(undefined);
		mockReject.mockResolvedValue(undefined);
	});

	it('does nothing while disabled', async () => {
		await renderHook(() => useNotificationNavigation(false));

		expect(mockedGetLastResponse).not.toHaveBeenCalled();
		expect(mockedAddResponseListener).not.toHaveBeenCalled();
	});

	it('starts listening once enabled', async () => {
		const { rerender } = await renderHook(({ enabled }: { enabled: boolean }) => useNotificationNavigation(enabled), { initialProps: { enabled: false } });

		expect(mockedAddResponseListener).not.toHaveBeenCalled();

		await rerender({ enabled: true });

		expect(mockedAddResponseListener).toHaveBeenCalledTimes(1);
		expect(mockedGetLastResponse).toHaveBeenCalledTimes(1);
	});

	describe('tapping a notification', () => {
		it.each([
			['FRIEND_REQUEST', { requestId: 'req-1' }, '/(tabs)/friends?tab=requests'],
			['FRIEND_ACCEPTED', {}, '/(tabs)/friends'],
			['WISHLIST_MEMBER_ADDED', { wishlistId: 'w-1' }, '/wishlist/w-1'],
			['WISHLIST_UPDATED', { wishlistId: 'w-2' }, '/wishlist/w-2'],
		])('navigates to the right screen for %s', async (type, extra, route) => {
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type, ...extra }));

			await waitFor(() => expect(mockPush).toHaveBeenCalledWith(route));
			expect(mockedClearLastResponse).toHaveBeenCalled();
		});

		it('does not navigate for a wishlist notification without wishlistId', async () => {
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'WISHLIST_UPDATED' }));

			expect(mockPush).not.toHaveBeenCalled();
		});

		it('does not navigate for types without a dedicated screen', async () => {
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'ACHIEVEMENT_UNLOCKED', codes: ['FIRST_WIN'] }));

			expect(mockPush).not.toHaveBeenCalled();
		});
	});

	describe('friend request actions', () => {
		it('accepts the request, dismisses the notification, shows a toast and opens the friends list', async () => {
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'FRIEND_REQUEST', requestId: 'req-1' }, 'accept'));

			await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/(tabs)/friends'));
			expect(mockAccept).toHaveBeenCalledWith('req-1');
			expect(mockReject).not.toHaveBeenCalled();
			expect(mockedDismissNotification).toHaveBeenCalledWith('notification-1');
			expect(mockShowToast).toHaveBeenCalledWith('Richiesta di amicizia accettata', 'success');
		});

		it('rejects the request, shows a toast and opens the requests tab', async () => {
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'FRIEND_REQUEST', requestId: 'req-1' }, 'reject'));

			await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/(tabs)/friends?tab=requests'));
			expect(mockReject).toHaveBeenCalledWith('req-1');
			expect(mockAccept).not.toHaveBeenCalled();
			expect(mockShowToast).toHaveBeenCalledWith('Richiesta di amicizia rifiutata', 'info');
		});

		it('tells the user when the request no longer exists', async () => {
			mockAccept.mockRejectedValueOnce({ response: { status: 404 } });
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'FRIEND_REQUEST', requestId: 'req-1' }, 'accept'));

			await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('Questa richiesta non è più valida', 'error'));
			expect(mockPush).toHaveBeenCalledWith('/(tabs)/friends');
		});

		it('shows a generic error for other failures', async () => {
			mockReject.mockRejectedValueOnce(new Error('Network error'));
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'FRIEND_REQUEST', requestId: 'req-1' }, 'reject'));

			await waitFor(() => expect(mockShowToast).toHaveBeenCalledWith('Impossibile completare l\'operazione', 'error'));
		});

		it('treats an action without requestId as a normal tap', async () => {
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'FRIEND_REQUEST' }, 'accept'));

			await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/(tabs)/friends?tab=requests'));
			expect(mockAccept).not.toHaveBeenCalled();
		});
	});

	describe('cold start and deduplication', () => {
		it('handles the response that opened the app', async () => {
			mockedGetLastResponse.mockResolvedValueOnce(buildResponse({ type: 'FRIEND_ACCEPTED' }));

			await renderHook(() => useNotificationNavigation(true));

			await waitFor(() => expect(mockPush).toHaveBeenCalledWith('/(tabs)/friends'));
		});

		it('handles the same response only once even if delivered twice', async () => {
			const response = buildResponse({ type: 'FRIEND_ACCEPTED' });
			mockedGetLastResponse.mockResolvedValueOnce(response);

			await renderHook(() => useNotificationNavigation(true));

			await waitFor(() => expect(mockPush).toHaveBeenCalledTimes(1));

			await emit(response);

			expect(mockPush).toHaveBeenCalledTimes(1);
		});

		it('handles a different action on the same notification separately', async () => {
			await renderHook(() => useNotificationNavigation(true));

			await emit(buildResponse({ type: 'FRIEND_REQUEST', requestId: 'req-1' }));
			await emit(buildResponse({ type: 'FRIEND_REQUEST', requestId: 'req-1' }, 'accept'));

			await waitFor(() => expect(mockPush).toHaveBeenCalledTimes(2));
		});

		it('logs when reading the last response fails', async () => {
			const consoleLog = jest.spyOn(console, 'log').mockImplementation(() => {});
			mockedGetLastResponse.mockRejectedValueOnce(new Error('Native error'));

			await renderHook(() => useNotificationNavigation(true));

			await waitFor(() => expect(consoleLog).toHaveBeenCalledWith('Failed to read last notification response:', expect.any(Error)));
			consoleLog.mockRestore();
		});
	});

	it('removes the listener on unmount', async () => {
		const { unmount } = await renderHook(() => useNotificationNavigation(true));

		await act(async () => {
			await unmount();
		});

		expect(mockRemoveListener).toHaveBeenCalled();
	});
});