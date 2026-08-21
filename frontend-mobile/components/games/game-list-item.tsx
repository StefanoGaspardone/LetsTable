import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';

import { Text } from '@/components/ui/text';

import { Game } from '@/types/game';

interface GameListItemProps {
	game: Game;
	onPress?: () => void;
}

const GameListItem = ({ game, onPress }: GameListItemProps) => {
	return (
		<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3'>
			<Image source = {{ uri: game.thumbnailUrl ?? undefined }} style = {{ width: 56, height: 56, borderRadius: 8 }} contentFit = 'cover' transition = { 150 }/>
			<View className = 'flex-1'>
				<Text className = 'font-display text-base text-foreground' numberOfLines = { 1 }>
					{game.name}
				</Text>
				<Text className = 'text-sm text-muted-foreground'>
					{game.yearPublished ?? '—'}
					{game.minPlayers && game.maxPlayers
						? ` · ${game.minPlayers}-${game.maxPlayers} giocatori`
						: ''}
				</Text>
			</View>
		</Pressable>
	)
}

export default GameListItem;