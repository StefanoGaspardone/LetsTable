import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Calendar, ChevronRight, Clock, Dices, Users, PuzzleIcon, Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game } from '@/types/game';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface GameListItemProps {
	game: Game;
	onPress?: () => void;
	showRank?: boolean;
	rankField?: 'rank' | 'bggRank';
}

const GameListItem = ({ game, onPress, showRank, rankField = 'rank' }: GameListItemProps) => {
	const { colors } = useThemeColors();

	const displayedRank = rankField === 'bggRank' ? game.bggRank : game.rank;

	return (
		<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 rounded-2xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75'>
			{showRank && (
				<View style = {{ width: 28 }} className = 'items-center justify-center'>
					{displayedRank != null && (
						<Text className = 'font-display text-lg text-primary'>{displayedRank}</Text>
					)}
				</View>
			)}
			<View style = {{ width: 56, height: 56 }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{game.thumbnailUrl ? (
					<Image source = {{ uri: game.thumbnailUrl }} style = {{ width: 56, height: 56 }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 22 } color = { colors.mutedForeground }/>
					</View>
				)}
			</View>
			<View className = 'flex-1 justify-center gap-1'>
				<View className='flex-row items-start justify-between gap-2'>
					<View className='flex-1 flex-row items-center gap-1.5'>
						<Text className='shrink text-base font-semibold text-foreground' numberOfLines = { 2 }>
							{game.name}
						</Text>
						{game.inCollection && (
							<View className='h-4 w-4 shrink-0 items-center justify-center rounded-full bg-primary'>
								<Check size = { 11 } color = '#FFFFFF' strokeWidth = { 3 }/>
							</View>
						)}
					</View>
					{game.isExpansion && (
						<View className='mt-0.5 flex-row items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 shrink-0'>
							<PuzzleIcon size = { 10 } color = { colors.primary }/>
							<Text className='text-[9px] font-sans-medium text-primary'>Espansione</Text>
						</View>
					)}
				</View>
				<View className = 'flex-row items-center gap-3'>
					{game.minPlayers != null && game.maxPlayers != null && (
						<View className = 'flex-row items-center gap-1'>
							<Users size = { 14 } color = { colors.mutedForeground } strokeWidth = { 2.5 }/>
							<Text className = 'text-sm font-medium text-muted-foreground'>
								{game.minPlayers === game.maxPlayers ? game.minPlayers : `${game.minPlayers}-${game.maxPlayers}`}
							</Text>
						</View>
					)}
					{game.playingTimeMinutes != null && (
						<View className = 'flex-row items-center gap-1'>
							<Clock size = { 14 } color = { colors.mutedForeground } strokeWidth = { 2.5 }/>
							<Text className = 'text-sm font-medium text-muted-foreground'>{game.playingTimeMinutes} min</Text>
						</View>
					)}
					{game.yearPublished != null && (
						<View className = 'flex-row items-center gap-1'>
							<Calendar size = { 14 } color = { colors.mutedForeground } strokeWidth = { 2.5 }/>
							<Text className = 'text-sm font-medium text-muted-foreground'>{game.yearPublished}</Text>
						</View>
					)}
				</View>
			</View>
			<View className = 'justify-center'>
				<ChevronRight size = { 18 } color = { colors.mutedForeground }/>
			</View>
		</Pressable>
	)
}

export default GameListItem;