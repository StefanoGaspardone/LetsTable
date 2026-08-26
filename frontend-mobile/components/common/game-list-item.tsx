import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Calendar, ChevronRight, Clock, Dices, Users, PuzzleIcon, Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game } from '@/types/game';

interface GameListItemProps {
	game: Game;
	onPress?: () => void;
	showRank?: boolean;
}

const GameListItem = ({ game, onPress, showRank }: GameListItemProps) => {
	return (
		<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3 active:bg-[#DDD8CE]'>
			{game.rank != null && showRank && (
				<Text className = 'font-display text-lg text-primary'>{game.rank}</Text>
			)}
			<View style = {{ width: 56, height: 56 }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{game.thumbnailUrl ? (
					<Image source = {{ uri: game.thumbnailUrl }} style = {{ width: 56, height: 56 }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 22 } color = '#736E65'/>
					</View>
				)}
			</View>
			<View className = 'flex-1 gap-1'>
				<View className = 'flex-row items-center justify-between gap-2'>
					<View className = 'flex-1 flex-row items-center gap-1.5'>
						<Text className = 'shrink text-base font-semibold text-foreground' numberOfLines = { 1 }>
							{game.name}
						</Text>
						{game.inCollection && (
							<View className = 'h-4 w-4 shrink-0 items-center justify-center rounded-full bg-[#C45135]'>
								<Check size = { 11 } color = '#FFFFFF' strokeWidth = { 3 }/>
							</View>
						)}
					</View>
					{game.isExpansion && (
						<View className = 'flex-row items-center gap-1 rounded-full bg-[#C45135]/10 px-2 py-0.5 shrink-0'>
							<PuzzleIcon size = { 10 } color = '#C45135'/>
							<Text className = 'text-[9px] font-sans-medium text-[#C45135]'>Espansione</Text>
						</View>
					)}
				</View>
				<View className = 'flex-row items-center gap-3'>
					{game.minPlayers != null && game.maxPlayers != null && (
						<View className = 'flex-row items-center gap-1'>
							<Users size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
							<Text className = 'text-sm font-medium text-muted-foreground'>
								{game.minPlayers === game.maxPlayers ? game.minPlayers : `${game.minPlayers}-${game.maxPlayers}`}
							</Text>
						</View>
					)}
					{game.playingTimeMinutes != null && (
						<View className = 'flex-row items-center gap-1'>
							<Clock size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
							<Text className = 'text-sm font-medium text-muted-foreground'>{game.playingTimeMinutes} min</Text>
						</View>
					)}
					{game.yearPublished != null && (
						<View className = 'flex-row items-center gap-1'>
							<Calendar size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
							<Text className = 'text-sm font-medium text-muted-foreground'>{game.yearPublished}</Text>
						</View>
					)}
				</View>
			</View>
			<ChevronRight size = { 18 } color = '#736E65'/>
		</Pressable>
	)
}

export default GameListItem;