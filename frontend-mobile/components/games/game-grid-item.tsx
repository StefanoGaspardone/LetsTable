import { Pressable, Dimensions } from 'react-native';
import { Image } from 'expo-image';

import { Text } from '@/components/ui/text';

import { Game } from '@/types/game';

const screenWidth = Dimensions.get('window').width;
const GRID_PADDING = 16;
const GRID_GAP = 12;
const ITEM_SIZE = (screenWidth - GRID_PADDING * 2 - GRID_GAP) / 2;

interface GameGridItemProps {
	game: Game;
	onPress?: () => void;
}

const GameGridItem = ({ game, onPress }: GameGridItemProps) => {
	return (
		<Pressable onPress = { onPress } style = {{ width: ITEM_SIZE }} className = 'mb-4'>
			<Image source = {{ uri: game.thumbnailUrl ?? undefined }} style = {{ width: ITEM_SIZE, height: ITEM_SIZE, borderRadius: 12 }} contentFit = 'cover' transition = { 150 }/>
			<Text className = 'mt-1 text-sm text-foreground' numberOfLines = { 2 }>
				{game.name}
			</Text>
		</Pressable>
	)
}

export default GameGridItem;