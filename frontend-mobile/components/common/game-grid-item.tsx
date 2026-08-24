import { View, Pressable, Dimensions } from 'react-native';
import { Image } from 'expo-image';
import { Dices, Users, Clock } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game } from '@/types/game';

const screenWidth = Dimensions.get('window').width;
const GRID_PADDING = 16;
const GRID_GAP = 12;
const CARD_WIDTH = Math.floor((screenWidth - GRID_PADDING * 2 - GRID_GAP) / 2);
const CARD_PADDING = 7;
const BORDER_WIDTH = 1;
const IMAGE_WIDTH = CARD_WIDTH - CARD_PADDING * 2 - BORDER_WIDTH * 2;
const IMAGE_HEIGHT = Math.round(IMAGE_WIDTH * 0.85);

interface GameGridItemProps {
	game: Game;
	onPress?: () => void;
}

const GameGridItem = ({ game, onPress }: GameGridItemProps) => {
	return (
		<Pressable onPress = { onPress } style = {{ width: CARD_WIDTH, padding: CARD_PADDING }} className = 'mb-4 rounded-2xl border border-border bg-card active:bg-[#DDD8CE]'>
			<View style = {{ width: IMAGE_WIDTH, height: IMAGE_HEIGHT }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{game.thumbnailUrl ? (
					<Image source = {{ uri: game.thumbnailUrl }} style = {{ width: IMAGE_WIDTH, height: IMAGE_HEIGHT }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 28 } color = '#736E65'/>
					</View>
				)}
			</View>
			<View className = 'mt-1'>
				<Text className = 'text-base text-foreground font-semibold' numberOfLines = { 1 }>
					{game.name}
				</Text>
				<View className = 'mt-1.5 flex-row items-center justify-between gap-3'>
					{game.minPlayers && game.maxPlayers && (
						<View className = 'flex-row items-center gap-1'>
							<Users size = { 14 } color = '#736E65'/>
							<Text className = 'text-xs text-muted-foreground'>
								{game.minPlayers}-{game.maxPlayers}
							</Text>
						</View>
					)}
					{game.playingTimeMinutes && (
						<View className = 'flex-row items-center gap-1'>
							<Clock size = { 14 } color = '#736E65'/>
							<Text className = 'text-xs text-muted-foreground'>{game.playingTimeMinutes} min</Text>
						</View>
					)}
				</View>
			</View>
		</Pressable>
	)
}

export default GameGridItem;