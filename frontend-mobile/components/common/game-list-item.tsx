import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Dices } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game } from '@/types/game';

interface GameListItemProps {
	game: Game;
	onPress?: () => void;
}

const GameListItem = ({ game, onPress }: GameListItemProps) => {
	return (
		<Pressable
			onPress={onPress}
			className="flex-row items-center gap-3 border-b border-border px-4 py-3"
		>
			<View style={{ width: 56, height: 56 }} className="overflow-hidden rounded-xl bg-secondary">
				{game.thumbnailUrl ? (
					<Image source={{ uri: game.thumbnailUrl }} style={{ width: 56, height: 56 }} contentFit="cover" />
				) : (
					<View className="h-full w-full items-center justify-center">
						<Dices size={22} color="#736E65" />
					</View>
				)}
			</View>

			<View className="flex-1">
				<Text className="font-display text-base text-foreground" numberOfLines={1}>
					{game.name}
				</Text>
				<Text className="text-sm text-muted-foreground">
					{game.yearPublished ?? '—'}
					{game.minPlayers && game.maxPlayers ? ` · ${game.minPlayers}-${game.maxPlayers} giocatori` : ''}
				</Text>
			</View>
		</Pressable>
	)
}

export default GameListItem;