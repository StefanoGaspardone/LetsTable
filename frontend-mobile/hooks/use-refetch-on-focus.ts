import { useFocusEffect } from 'expo-router';
import { useCallback } from 'react';
import { QueryKey, useQueryClient } from '@tanstack/react-query';

export const useRefetchOnFocus = (queryKey: QueryKey) => {
	const queryClient = useQueryClient();

	useFocusEffect(
		useCallback(() => {
			queryClient.invalidateQueries({ queryKey });
		}, [JSON.stringify(queryKey)])
	);
}