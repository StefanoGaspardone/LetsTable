import { useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { QueryKey, useQueryClient } from '@tanstack/react-query';

export const useRefetchOnFocus = (queryKey: QueryKey) => {
	const queryClient = useQueryClient();
	const serializedKey = JSON.stringify(queryKey);

	useFocusEffect(
		useCallback(() => {
			queryClient.refetchQueries({ queryKey, type: 'active', stale: true });
		}, [queryClient, serializedKey])
	);
}