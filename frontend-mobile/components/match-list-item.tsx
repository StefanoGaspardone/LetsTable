import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Dices, Users, Trophy } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Match } from '@/types/match';

interface MatchListItemProps {
	match: Match;
	onPress?: () => void;
}

const getPlayerCount = (match: Match): number => {
	if(match.isTeamBased && match.teams) {
	    return match.teams.reduce((sum, team) => sum + team.players.length, 0);
	
    }
	return match.players?.length ?? 0;
}

const getWinnerLabel = (match: Match): string | null => {
	if(match.isTeamBased && match.teams) {
		const winningTeam = match.teams.find(t => t.isWinner);
		if(!winningTeam) return null;
		
        return winningTeam.name ?? `Squadra ${winningTeam.color}`;
	}

	const winner = match.players?.find(p => p.isWinner);
	if(!winner) return null;
	
    return winner.user?.username ?? winner.guestName ?? null;
}

const MatchListItem = ({ match, onPress }: MatchListItemProps) => {
	const playerCount = getPlayerCount(match);
	const winnerLabel = getWinnerLabel(match);
	const formattedDate = new Date(match.playedAt).toLocaleDateString('it-IT', {
		day: 'numeric',
		month: 'short',
		year: 'numeric',
	});

	return (
		<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3'>
			{match.game.thumbnailUrl ? (
				<Image source = {{ uri: match.game.thumbnailUrl }} style = {{ width: 56, height: 56, borderRadius: 8 }} contentFit = 'cover'/>
			) : (
				<View className = 'h-14 w-14 items-center justify-center rounded-lg bg-secondary'>
					<Dices size = { 24 } className = 'text-muted-foreground'/>
				</View>
			)}
			<View className = 'flex-1'>
				<Text className = 'font-display text-base text-foreground' numberOfLines = { 1 }>
					{match.game.name}
				</Text>
				<Text className = 'text-sm text-muted-foreground'>{formattedDate}</Text>
				<View className = 'mt-1 flex-row items-center gap-3'>
					<View className = 'flex-row items-center gap-1'>
						<Users size = { 14 } className = 'text-muted-foreground'/>
						<Text className='text-xs text-muted-foreground'>{playerCount}</Text>
					</View>
					{!!(winnerLabel) && (
						<View className = 'flex-row items-center gap-1'>
							<Trophy size = { 14 } className = 'text-accent'/>
							<Text className = 'text-xs text-accent'>{winnerLabel}</Text>
						</View>
					)}
				</View>
			</View>
		</Pressable>
	)
}

export default MatchListItem;