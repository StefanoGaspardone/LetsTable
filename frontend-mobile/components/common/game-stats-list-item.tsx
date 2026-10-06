import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Dices, Clock, Trophy, Hash } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { formatDuration } from '@/lib/time';

import { GameMatchStats } from '@/types/match';

interface GameStatsListItemProps {
	stats: GameMatchStats;
}

const GameStatsListItem = ({ stats }: GameStatsListItemProps) => {
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const winRatePercent = Math.round(stats.winRate * 100);

	return (
		<Pressable onPress = { () => router.push(`/game/${stats.bggId}`) } className = 'flex-row items-center gap-3 rounded-2xl border border-border bg-card p-2.5 active:scale-[0.98] active:opacity-75'>
			<View style = {{ width: 56, height: 56 }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{stats.thumbnailUrl ? (
					<Image source = {{ uri: stats.thumbnailUrl }} style = {{ width: 56, height: 56 }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 22 } color = { colors.mutedForeground }/>
					</View>
				)}
			</View>
			<View className = 'flex-1 gap-1.5'>
				<Text className = 'text-sm font-semibold text-foreground' numberOfLines = { 1 }>{stats.name}</Text>
				<View className = 'flex-row flex-wrap items-center gap-x-3 gap-y-1'>
					<View className = 'flex-row items-center gap-1'>
						<Hash size = { 12 } color = { colors.mutedForeground }/>
						<Text className = 'text-xs text-muted-foreground'>{stats.matchCount} {stats.matchCount === 1 ? 'partita' : 'partite'}</Text>
					</View>
					<View className = 'flex-row items-center gap-1'>
						<Clock size = { 12 } color = { colors.mutedForeground }/>
						<Text className = 'text-xs text-muted-foreground'>{formatDuration(stats.totalMinutes)}</Text>
					</View>
					<View className = 'flex-row items-center gap-1'>
						<Trophy size = { 12 } color = { colors.mutedForeground }/>
						<Text className = 'text-xs text-muted-foreground'>{winRatePercent}%</Text>
					</View>
				</View>
			</View>
		</Pressable>
	)
}

export default GameStatsListItem;