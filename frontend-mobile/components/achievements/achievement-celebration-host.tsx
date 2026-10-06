import { useEffect, useRef, useState } from 'react';
import { AppState, Modal, Pressable, View } from 'react-native';
import { useQueryClient } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';
import AchievementIcon from '@/components/achievements/achievement-icon';

import { useAuth } from '@/contexts/auth-context';

import { useMarkAchievementsSeen, useUnseenAchievements } from '@/hooks/use-achievement';

import { Achievement } from '@/types/achievement';

const AchievementCelebrationHost = () => {
	const { isAuthenticated } = useAuth();

	const queryClient = useQueryClient();
	const { data: unseen } = useUnseenAchievements(isAuthenticated);
	const { mutate: markSeen } = useMarkAchievementsSeen();

	const [queue, setQueue] = useState<Achievement[]>([]);
	const knownCodes = useRef<Set<string>>(new Set());

	useEffect(() => {
		if(!isAuthenticated) {
			knownCodes.current.clear();
			setQueue([]);
			return;
		}

		if(!unseen?.length) return;

		const fresh = unseen.filter(a => !knownCodes.current.has(a.code));
		if(fresh.length === 0) return;

		fresh.forEach(a => knownCodes.current.add(a.code));
		setQueue(prev => [...prev, ...fresh]);
	}, [unseen, isAuthenticated]);

	useEffect(() => {
		if(!isAuthenticated) return;

		const subscription = AppState.addEventListener('change', state => {
			if(state === 'active') {
				queryClient.invalidateQueries({ queryKey: ['achievements', 'unseen'] });
			}
		});

		return () => subscription.remove();
	}, [isAuthenticated, queryClient]);

	const current = queue[0];

	const handleClose = () => {
		if(!current) return;

		markSeen([current.code]);
		setQueue(prev => prev.slice(1));
	}

	return (
		<Modal visible = { !!current } transparent animationType = 'fade' statusBarTranslucent onRequestClose = { handleClose }>
			<View className = 'flex-1 items-center justify-center bg-black/80 px-8'>
				{current && (
					<View className = 'w-full items-center rounded-3xl border border-border bg-card p-8'>
						<Text className = 'mb-6 text-xs font-bold uppercase tracking-widest text-muted-foreground'>Traguardo sbloccato</Text>
						<View className = 'mb-6'>
							<AchievementIcon icon = { current.icon } tier = { current.tier } unlocked size = { 44 }/>
						</View>
						<Text className = 'mb-2 text-center font-display text-2xl text-foreground'>{ current.title }</Text>
						<Text className = 'mb-8 text-center text-sm text-muted-foreground'>{ current.description }</Text>
						<Pressable onPress = { handleClose } className = 'w-full items-center rounded-2xl bg-primary py-3.5 active:opacity-80'>
							<Text className = 'text-sm font-semibold text-primary-foreground'>{ queue.length > 1 ? `Avanti (altri ${queue.length - 1})` : 'Fantastico!' }</Text>
						</Pressable>
					</View>
				)}
			</View>
		</Modal>
	)
}

export default AchievementCelebrationHost;