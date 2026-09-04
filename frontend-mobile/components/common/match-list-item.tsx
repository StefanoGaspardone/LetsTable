import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { Calendar, Dices, Users } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Match } from '@/types/match';

interface MatchListItemProps {
	match: Match;
}

const MatchListItem = ({ match }: MatchListItemProps) => {
	const isInProgress = match.durationMinutes == null;

	const formattedDate = new Date(match.playedAt).toLocaleDateString('it-IT', {
		day: 'numeric',
		month: 'short',
		year: 'numeric',
	});

	const participantCount = match.isTeamBased
		? match.teams?.length ?? 0
		: match.players?.length ?? 0;

	return (
		<Pressable onPress = { () => router.push(`/match/${match.id}`) } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3 active:bg-[#DDD8CE]'>
            <View style = {{ width: 56, height: 56 }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{match.game.thumbnailUrl ? (
					<Image source = {{ uri: match.game.thumbnailUrl }} style = {{ width: 56, height: 56 }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 22 } color = '#736E65'/>
					</View>
				)}
			</View>
			<View className = 'flex-1 gap-1'>
                <Text className = 'shrink text-base font-semibold text-foreground' numberOfLines = { 1 }>
                    {match.game.name}
                </Text>
                <View className = 'flex-row items-center gap-3'>
                    <View className = 'flex flex-row items-center gap-1'>
                        <Calendar size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
                        <Text className = 'text-sm font-medium text-muted-foreground'>{formattedDate}</Text>
                    </View>
                    <View className = 'flex flex-row items-center gap-1'>
                        <Users size = { 14 } color = '#736E65' strokeWidth = { 2.5 }/>
                        <Text className = 'text-sm font-medium text-muted-foreground'>{participantCount} {match.isTeamBased ? 'squadre' : 'giocatori'}</Text>
                    </View>
                </View>
			</View>
			{isInProgress && (
				<View className = 'flex-row items-center gap-1.5 rounded-full border border-[#C45135]/30 bg-[#C45135]/10 px-2.5 py-1'>
					<View className = 'h-1.5 w-1.5 rounded-full bg-[#C45135]'/>
					<Text className = 'text-xs font-medium text-[#C45135]'>In corso</Text>
				</View>
			)}
		</Pressable>
	)
}

export default MatchListItem;