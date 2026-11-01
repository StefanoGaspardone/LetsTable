import { useEffect, useRef } from 'react';
import * as Notifications from 'expo-notifications';

import { useNavigationStack } from '@/contexts/navigation-stack-context';
import { useToast } from '@/contexts/toast-context';

import { useAcceptFriendRequest, useRejectFriendRequest } from '@/hooks/use-friend';

import { NOTIFICATION_ACTIONS } from '@/lib/push-notifications';

interface NotificationData {
	type?: string;
	requestId?: string;
	wishlistId?: string;
}

const getResponseKey = (response: Notifications.NotificationResponse) => `${response.notification.request.identifier}:${response.actionIdentifier}`;

export const useNotificationNavigation = (enabled: boolean) => {
	const router = useNavigationStack();
	const { showToast } = useToast();

	const acceptRequest = useAcceptFriendRequest();
	const rejectRequest = useRejectFriendRequest();

	const handledKeysRef = useRef(new Set<string>());
	const handleResponseRef = useRef<(response: Notifications.NotificationResponse) => Promise<void>>(async () => undefined);

	const handleFriendRequestAction = async (action: string, requestId: string) => {
		const isAccept = action === NOTIFICATION_ACTIONS.accept;

		try {
			if(isAccept) await acceptRequest.mutateAsync(requestId);
			else await rejectRequest.mutateAsync(requestId);

			showToast(isAccept ? 'Richiesta di amicizia accettata' : 'Richiesta di amicizia rifiutata', isAccept ? 'success' : 'info');
		} catch(error: any) {
			const message = error?.response?.status === 404 ? 'Questa richiesta non è più valida' : 'Impossibile completare l\'operazione';
			showToast(message, 'error');
		}

		router.push(isAccept ? '/(tabs)/friends' : '/(tabs)/friends?tab=requests');
	}

	const navigateTo = (data: NotificationData) => {
		switch(data.type) {
			case 'FRIEND_REQUEST':
				router.push('/(tabs)/friends?tab=requests');
				break;
			case 'FRIEND_ACCEPTED':
				router.push('/(tabs)/friends');
				break;
			case 'WISHLIST_MEMBER_ADDED':
			case 'WISHLIST_UPDATED':
				if(data.wishlistId) router.push(`/wishlist/${data.wishlistId}`);
				break;
		}
	}

	handleResponseRef.current = async (response) => {
		const key = getResponseKey(response);
		if(handledKeysRef.current.has(key)) return;
		handledKeysRef.current.add(key);

		Notifications.clearLastNotificationResponseAsync().catch(() => undefined);

		const data = response.notification.request.content.data as NotificationData;
		const action = response.actionIdentifier;
		const isFriendRequestAction = action === NOTIFICATION_ACTIONS.accept || action === NOTIFICATION_ACTIONS.reject;

		if(data.type === 'FRIEND_REQUEST' && data.requestId && isFriendRequestAction) {
			await Notifications.dismissNotificationAsync(response.notification.request.identifier).catch(() => undefined);
			await handleFriendRequestAction(action, data.requestId);
			return;
		}

		navigateTo(data);
	}

	useEffect(() => {
		if(!enabled) return;

		Notifications.getLastNotificationResponseAsync()
			.then(response => {
				if(response) handleResponseRef.current(response);
			})
			.catch(error => console.log('Failed to read last notification response:', error));

		const subscription = Notifications.addNotificationResponseReceivedListener(response => {
			handleResponseRef.current(response);
		});

		return () => subscription.remove();
	}, [enabled]);
}