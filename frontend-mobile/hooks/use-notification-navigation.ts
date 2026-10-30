import { useEffect, useRef } from 'react';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { getIsPushNotificationsSupported } from '@/lib/push-notifications';

type NotificationData = Record<string, unknown> | undefined;

let lastHandledNotificationId: string | null = null;

export const useNotificationNavigation = (enabled: boolean) => {
	const router = useNavigationStack();
	const routerRef = useRef(router);
	routerRef.current = router;

	useEffect(() => {
		if(!enabled || !getIsPushNotificationsSupported()) return;

		const navigate = (identifier: string, data: NotificationData) => {
			if(identifier === lastHandledNotificationId) return;
			lastHandledNotificationId = identifier;

			switch(data?.type) {
				case 'FRIEND_REQUEST':
					routerRef.current.push('/(tabs)/friends?tab=requests');
					break;
				case 'FRIEND_ACCEPTED':
					routerRef.current.push('/(tabs)/friends');
					break;
				case 'WISHLIST_MEMBER_ADDED':
					if(typeof data.wishlistId === 'string') {
						routerRef.current.push(`/wishlist/${data.wishlistId}`);
					}
					break;
			}
		}

		let subscription: { remove: () => void } | undefined;
		let cancelled = false;

		import('expo-notifications').then(async Notifications => {
			if(cancelled) return;

			const lastResponse = await Notifications.getLastNotificationResponseAsync();

			if(lastResponse) {
				navigate(lastResponse.notification.request.identifier, lastResponse.notification.request.content.data);
			}

			subscription = Notifications.addNotificationResponseReceivedListener(response => {
				navigate(response.notification.request.identifier, response.notification.request.content.data);
			});
		}).catch(() => {
			console.log('expo-notifications not available in this environment');
		});

		return () => {
			cancelled = true;
			subscription?.remove();
		};
	}, [enabled]);
}