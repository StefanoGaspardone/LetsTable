import { View, Pressable, Dimensions } from 'react-native';
import { Image } from 'expo-image';
import { Dices, Users, Clock } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game, GameSearchResult, HotGame } from '@/types/game';

type GameGridItemGame = Game | GameSearchResult | HotGame;

const screenWidth = Dimensions.get('window').width;
const GRID_PADDING = 16;
const GRID_GAP = 12;
const CARD_WIDTH = Math.floor((screenWidth - GRID_PADDING * 2 - GRID_GAP) / 2);
const CARD_PADDING = 7;
const BORDER_WIDTH = 1;
const IMAGE_WIDTH = CARD_WIDTH - CARD_PADDING * 2 - BORDER_WIDTH * 2;
const IMAGE_HEIGHT = Math.round(IMAGE_WIDTH * 0.9);

interface GameGridItemProps {
	game: GameGridItemGame;
	onPress?: () => void;
}

const GameGridItem = ({ game, onPress }: GameGridItemProps) => {
    const thumbnailUrl = 'thumbnailUrl' in game ? game.thumbnailUrl : null;
	const rank = 'rank' in game ? game.rank : null;
	const minPlayers = 'minPlayers' in game ? game.minPlayers : null;
	const maxPlayers = 'maxPlayers' in game ? game.maxPlayers : null;
	const playingTimeMinutes = 'playingTimeMinutes' in game ? game.playingTimeMinutes : null;

	return (
		<Pressable onPress = { onPress } style = {{ width: CARD_WIDTH, padding: CARD_PADDING }} className = 'mb-4 rounded-2xl border border-border bg-card active:bg-[#DDD8CE]'>
			<View style = {{ width: IMAGE_WIDTH, height: IMAGE_HEIGHT }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{thumbnailUrl ? (
					<Image source = {{ uri: thumbnailUrl }} style = {{ width: IMAGE_WIDTH, height: IMAGE_HEIGHT }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 28 } color = '#736E65'/>
					</View>
				)}
                {rank != null && (
					<View style = {{ position: 'absolute', top: 6, left: 6 }} className = 'h-6 min-w-6 items-center justify-center rounded-full bg-primary px-1.5'>
						<Text className = 'text-xs font-bold text-white'>{rank}</Text>
					</View>
				)}
			</View>
			<View className = 'mt-1'>
				<Text className = 'text-base text-foreground font-semibold' numberOfLines = { 1 }>
					{game.name}
				</Text>
				<View className = 'mt-1.5 flex-row items-center justify-between gap-3'>
					{minPlayers && maxPlayers && (
						<View className = 'flex-row items-center gap-1'>
							<Users size = { 14 } color = '#736E65'/>
							<Text className = 'text-xs text-muted-foreground'>
								{minPlayers}-{maxPlayers}
							</Text>
						</View>
					)}
					{playingTimeMinutes && (
						<View className = 'flex-row items-center gap-1'>
							<Clock size = { 14 } color = '#736E65'/>
							<Text className = 'text-xs text-muted-foreground'>{playingTimeMinutes} min</Text>
						</View>
					)}
				</View>
			</View>
		</Pressable>
	)
}

export default GameGridItem;