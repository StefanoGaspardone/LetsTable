import { View, Pressable, Dimensions } from 'react-native';
import { Image } from 'expo-image';
import { Dices } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game, HotGame, GameSearchResult } from '@/types/game';

type GameGridItemGame = Game | HotGame | GameSearchResult;

const screenWidth = Dimensions.get('window').width;
const GRID_PADDING = 16;
const GRID_GAP = 12;
const ITEM_SIZE = (screenWidth - GRID_PADDING * 2 - GRID_GAP) / 2;

interface GameGridItemProps {
	game: GameGridItemGame;
	onPress?: () => void;
}

const GameGridItem = ({ game, onPress }: GameGridItemProps) => {
	const thumbnailUrl = 'thumbnailUrl' in game ? game.thumbnailUrl : null;
	const rank = 'rank' in game ? game.rank : null;

	return (
		<Pressable onPress = { onPress } style = {{ width: ITEM_SIZE }} className = 'mb-4'>
			<View className = 'relative'>
				{thumbnailUrl ? (
					<Image source = {{ uri: thumbnailUrl }} style = {{ width: ITEM_SIZE, height: ITEM_SIZE, borderRadius: 12 }} contentFit = 'cover' transition = { 150 }/>
				) : (
					<View style = {{ width: ITEM_SIZE, height: ITEM_SIZE }} className = 'items-center justify-center rounded-xl bg-secondary'>
						<Dices size = { 32 } className = 'text-muted-foreground'/>
					</View>
				)}

				{rank != null && (
					<View className = 'absolute left-2 top-2 h-6 min-w-6 items-center justify-center rounded-full bg-accent px-1.5'>
						<Text className = 'text-xs font-bold text-accent-foreground'>{rank}</Text>
					</View>
				)}
			</View>
			<Text className = 'mt-1 text-sm text-foreground' numberOfLines = { 2 }>
				{game.name}
			</Text>
		</Pressable>
	)
}

export default GameGridItem;