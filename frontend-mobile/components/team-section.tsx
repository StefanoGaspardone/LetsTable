import { View } from 'react-native';
import { Crown } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { MatchTeam } from '@/types/match';

interface TeamSectionProps {
	team: MatchTeam;
}

const TeamSection = ({ team }: TeamSectionProps) => {
	return (
		<View className = 'mb-2'>
			<View className = 'flex-row items-center gap-3 bg-secondary px-4 py-3'>
				{team.startingPosition != null && (
					<Text className = 'w-5 text-center text-xs text-muted-foreground'>{team.startingPosition}</Text>
				)}
				<View className = 'h-4 w-4 rounded-full border border-border' style = {{ backgroundColor: team.color }}/>
				<Text className = 'flex-1 font-display text-base text-foreground'>
					{team.name ?? 'Squadra'}
				</Text>
				<Text className = 'text-sm text-muted-foreground'>{team.score} pt</Text>
				{team.isWinner && <Crown size = { 18 } className = 'text-accent'/>}
			</View>
			{team.players.map((player) => (
				<View key = { player.id } className = 'border-b border-border px-4 py-2 pl-12'>
					<Text className = 'text-sm text-foreground'>
						{player.user?.username ?? player.guestName}
					</Text>
				</View>
			))}
		</View>
	)
}

export default TeamSection;