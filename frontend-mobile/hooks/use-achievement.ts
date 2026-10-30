import { useMutation, useQuery } from '@tanstack/react-query';

import { getMyAchievements, getUnseenAchievements, getUserAchievements, markAchievementsSeen } from '@/api/achievement';

export const useMyAchievements = () => {
	return useQuery({
		queryKey: ['achievements', 'me'],
		queryFn: getMyAchievements,
	});
}

export const useUserAchievements = (userId: string, enabled = true) => {
	return useQuery({
		queryKey: ['achievements', 'user', userId],
		queryFn: () => getUserAchievements(userId),
		enabled,
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
	return useMutation({
		mutationFn: (codes: string[]) => markAchievementsSeen(codes),
	});
}