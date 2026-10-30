import { AppState } from 'react-native';
import { isAxiosError } from 'axios';
import { focusManager, MutationCache, QueryClient } from '@tanstack/react-query';

const STALE_TIME_MS = 30000;
const MAX_RETRIES = 2;

focusManager.setEventListener(handleFocus => {
	const subscription = AppState.addEventListener('change', status => handleFocus(status === 'active'));
	return () => subscription.remove();
});

const shouldRetry = (failureCount: number, error: unknown) => {
	if(isAxiosError(error) && error.response && error.response.status < 500) return false;
	return failureCount < MAX_RETRIES;
}

export const queryClient: QueryClient = new QueryClient({
	defaultOptions: {
		queries: {
			staleTime: STALE_TIME_MS,
			retry: shouldRetry,
		},
	},
	mutationCache: new MutationCache({
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['achievements'] });
		},
	}),
});