import { View, Pressable, Dimensions } from 'react-native';
import { Image } from 'expo-image';
import { Dices, Users, Clock, PuzzleIcon, Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Game } from '@/types/game';

const screenWidth = Dimensions.get('window').width;
const GRID_PADDING = 16;
const GRID_GAP = 12;
const CARD_WIDTH = Math.floor((screenWidth - GRID_PADDING * 2 - GRID_GAP) / 2);
const CARD_PADDING = 8;
const BORDER_WIDTH = 1;
const IMAGE_WIDTH = CARD_WIDTH - CARD_PADDING * 2 - BORDER_WIDTH * 2;
const IMAGE_HEIGHT = Math.round(IMAGE_WIDTH * 0.95);

interface GameGridItemProps {
	game: Game;
	onPress?: () => void;
	showRank?: boolean;
}

const GameGridItem = ({ game, onPress, showRank = false }: GameGridItemProps) => {
	return (
		<Pressable onPress = { onPress } style = {{ width: CARD_WIDTH, padding: CARD_PADDING }} className = 'mb-4 rounded-2xl border border-border bg-card active:scale-[0.98] active:opacity-75'>
			<View style = {{ width: IMAGE_WIDTH, height: IMAGE_HEIGHT }} className = 'relative overflow-hidden rounded-xl bg-secondary'>
				{game.thumbnailUrl ? (
					<Image source = {{ uri: game.thumbnailUrl }} style = {{ width: IMAGE_WIDTH, height: IMAGE_HEIGHT }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 28 } color = '#736E65'/>
					</View>
				)}
				<View className = 'absolute top-1.5 left-1.5 right-1.5 flex-row items-center justify-between pointer-events-none'>
					<View className = 'flex-row items-center gap-1'>
						{game.rank != null && showRank && (
							<View className = 'h-6 min-w-6 items-center justify-center rounded-full bg-primary px-1.5 shadow-sm'>
							<Text className = 'text-xs font-bold text-white'>#{game.rank}</Text>
							</View>
						)}
					</View>
					{game.isExpansion && (
						<View className = 'h-6 w-6 items-center justify-center rounded-full bg-[#C45135] shadow-sm'>
							<PuzzleIcon size = { 12 } color = '#FFFFFF'/>
						</View>
					)}
				</View>
				{game.inCollection && (
					<View className = 'absolute bottom-1.5 right-1.5 flex-row items-center gap-1 rounded-full bg-black/60 pr-2 backdrop-blur-md'>
						<View className = 'h-5 w-5 items-center justify-center rounded-full bg-[#C45135]'>
							<Check size = { 12 } color = '#FFFFFF' strokeWidth = { 3 }/>
						</View>
						<Text className = 'text-xs font-medium text-white'>In possesso</Text>
					</View>
				)}
			</View>
			<View className = 'mt-1'>
				<Text className = 'text-base font-semibold text-foreground' numberOfLines = { 1 }>
					{game.name}
				</Text>
				<View className = 'mt-1.5 flex-row items-center justify-between gap-3'>
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
				</View>
			</View>
		</Pressable>
	)
}

export default GameGridItem;