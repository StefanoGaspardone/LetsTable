import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Dices } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game, HotGame, GameSearchResult } from '@/types/game';

type GameListItemGame = Game | HotGame | GameSearchResult;

interface GameListItemProps {
	game: GameListItemGame;
	onPress?: () => void;
}

const GameListItem = ({ game, onPress }: GameListItemProps) => {
	const thumbnailUrl = 'thumbnailUrl' in game ? game.thumbnailUrl : null;
	const rank = 'rank' in game ? game.rank : null;
	const minPlayers = 'minPlayers' in game ? game.minPlayers : null;
	const maxPlayers = 'maxPlayers' in game ? game.maxPlayers : null;

	return (
		<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3'>
			{rank != null && (
				<Text className = 'w-6 text-center font-display text-lg text-accent'>{rank}</Text>
			)}
			{thumbnailUrl ? (
				<Image source = {{ uri: thumbnailUrl }} style = {{ width: 56, height: 56, borderRadius: 8 }} contentFit = 'cover' transition = { 150 }/>
			) : (
				<View className = 'h-14 w-14 items-center justify-center rounded-lg bg-secondary'>
					<Dices size = { 24 } className = 'text-muted-foreground'/>
				</View>
			)}
			<View className = 'flex-1'>
				<Text className = 'font-display text-base text-foreground' numberOfLines = { 1 }>
					{game.name}
				</Text>
				<Text className='text-sm text-muted-foreground'>
					{game.yearPublished ?? '-'}
					{minPlayers && maxPlayers ? ` · ${minPlayers}-${maxPlayers} giocatori` : ''}
				</Text>
			</View>
		</Pressable>
	)
}

export default GameListItem;