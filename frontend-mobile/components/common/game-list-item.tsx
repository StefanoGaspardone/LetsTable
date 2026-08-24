import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Calendar, ChevronRight, Clock, Dices, Users } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Game, GameSearchResult, HotGame } from '@/types/game';

type GameListItemGame = Game | GameSearchResult | HotGame;

interface GameListItemProps {
	game: GameListItemGame;
	onPress?: () => void;
}

const GameListItem = ({ game, onPress }: GameListItemProps) => {
    const thumbnailUrl = 'thumbnailUrl' in game ? game.thumbnailUrl : null;
	const rank = 'rank' in game ? game.rank : null;
	const minPlayers = 'minPlayers' in game ? game.minPlayers : null;
	const maxPlayers = 'maxPlayers' in game ? game.maxPlayers : null;
    const playingTimeMinutes = 'playingTimeMinutes' in game ? game.playingTimeMinutes : null;

	return (
		<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3 active:bg-[#DDD8CE]'>
			{rank != null && (
				<Text className = 'font-display text-lg text-primary'>{rank}</Text>
			)}
			<View style = {{ width: 56, height: 56 }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{thumbnailUrl  ? (
					<Image source = {{ uri: thumbnailUrl  }} style = {{ width: 56, height: 56 }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 22 } color = '#736E65'/>
					</View>
				)}
			</View>
			<View className = 'flex-1 gap-1'>
				<Text className = 'text-base text-foreground font-semibold' numberOfLines = { 1 }>
					{game.name}
				</Text>
                <View className = 'flex-row items-center gap-3'>
                    <View className = 'flex-row items-center gap-1'>
                        <Users size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
                        <Text className = 'text-sm text-muted-foreground font-medium'>
                            {minPlayers === maxPlayers ? minPlayers : `${minPlayers}-${maxPlayers}`}
                        </Text>
                    </View>
                    <View className = 'flex-row items-center gap-1'>
                        <Clock size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
                        <Text className = 'text-sm text-muted-foreground font-medium'>{playingTimeMinutes} min</Text>
                    </View>
                    <View className = 'flex-row items-center gap-1'>
                        <Calendar size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
                        <Text className = 'text-sm text-muted-foreground font-medium'>{game.yearPublished ?? '-'}</Text>
                    </View>
                </View>
			</View>
			<ChevronRight size = { 18 } color = '#736E65'/>
		</Pressable>
	)
}

export default GameListItem;