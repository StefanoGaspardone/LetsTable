import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';

import { getMyAchievements, getUnseenAchievements, getUserAchievements, markAchievementsSeen } from '@/api/achievement';

export const useMyAchievements = () => {
	return useQuery({
		queryKey: ['achievements', 'me'],
		queryFn: getMyAchievements,
	});
}

export const useUserAchievements = (userId: string) => {
	return useQuery({
		queryKey: ['achievements', 'user', userId],
		queryFn: () => getUserAchievements(userId),
	});
}

export const useUnseenAchievements = (enabled: boolean) => {
	return useQuery({
		queryKey: ['achievements', 'unseen'],
		queryFn: getUnseenAchievements,
		enabled,
	});
}

export const useMarkAchievementsSeen = () => {
	const queryClient = useQueryClient();

	return useMutation({
		mutationFn: (codes: string[]) => markAchievementsSeen(codes),
		onSuccess: () => {
			queryClient.invalidateQueries({ queryKey: ['achievements', 'me'] });
		},
	});
}