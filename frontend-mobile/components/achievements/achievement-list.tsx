import { View } from 'react-native';

import { Text } from '@/components/ui/text';
import AchievementIcon from '@/components/achievements/achievement-icon';

import { Achievement, AchievementCategory } from '@/types/achievement';

const CATEGORIES: { key: AchievementCategory; label: string }[] = [
	{ key: 'MATCHES', label: 'Partite' },
	{ key: 'WINS', label: 'Vittorie' },
	{ key: 'STREAK', label: 'Serie' },
	{ key: 'COLLECTION', label: 'Collezione' },
	{ key: 'SOCIAL', label: 'Amici' },
];

const AchievementCard = ({ achievement }: { achievement: Achievement }) => {
	const percentage = Math.min(100, Math.round((achievement.progress / achievement.target) * 100));

	return (
		<View className = 'flex-row items-center gap-3 border-b border-border/40 p-3.5 last:border-b-0'>
			<AchievementIcon icon = { achievement.icon } tier = { achievement.tier } unlocked = { achievement.unlocked }/>
			<View className = 'flex-1'>
				<Text className = { `text-sm font-semibold ${achievement.unlocked ? 'text-foreground' : 'text-muted-foreground'}` }>{ achievement.title }</Text>
				<Text className = 'mb-1.5 text-xs text-muted-foreground'>{ achievement.description }</Text>
				{achievement.unlocked ? (
					<Text className = 'text-xs font-medium text-primary'>
						Sbloccato{ achievement.unlockedAt ? ` il ${new Date(achievement.unlockedAt).toLocaleDateString('it-IT')}` : '' }
					</Text>
				) : (
					<View className = 'flex-row items-center gap-2'>
						<View className = 'h-1.5 flex-1 overflow-hidden rounded-full bg-secondary'>
							<View style = {{ width: `${percentage}%` }} className = 'h-full rounded-full bg-primary'/>
						</View>
						<Text className = 'text-xs text-muted-foreground'>{ achievement.progress }/{ achievement.target }</Text>
					</View>
				)}
			</View>
		</View>
	)
}

const AchievementList = ({ achievements }: { achievements: Achievement[] }) => {
	const unlockedCount = achievements.filter(a => a.unlocked).length;

	return (
		<View>
			<View className = 'mb-6 items-center rounded-2xl border border-border bg-card p-4 shadow-sm'>
				<Text className = 'font-display text-2xl'>{ unlockedCount } / { achievements.length }</Text>
				<Text className = 'text-xs uppercase tracking-wider text-muted-foreground'>Traguardi sbloccati</Text>
			</View>
			{CATEGORIES.map(({ key, label }) => {
				const items = achievements.filter(a => a.category === key);
				if(items.length === 0) return null;

				return (
					<View key = { key }>
						<Text className = 'mb-2 pl-2 text-xs font-bold uppercase tracking-wider text-muted-foreground'>{ label }</Text>
						<View className = 'mb-6 overflow-hidden rounded-2xl border border-border/50 bg-card shadow-sm'>
							{items.map(a => <AchievementCard key = { a.code } achievement = { a }/>)}
						</View>
					</View>
				)
			})}
		</View>
	)
}

export default AchievementList;