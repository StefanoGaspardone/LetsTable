import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { Crown, User } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Match } from '@/types/match';

import { formatRelativeDays } from '@/lib/date';

interface LatestMatchCardProps {
	match: Match;
}

interface FlatPlayer {
	userId: string | null;
	name: string;
	avatarUrl: string | null;
	isWinner: boolean | null;
}

const flattenPlayers = (match: Match): FlatPlayer[] => {
	if(match.isTeamBased && match.teams) {
		return match.teams.flatMap(team =>
			team.players.map(p => ({
				userId: p.user?.id ?? null,
				name: p.user?.username ?? p.guestName ?? 'Sconosciuto',
				avatarUrl: p.user?.avatarUrl ?? null,
				isWinner: team.isWinner,
			}))
		)
	}

	return (match.players ?? []).map(p => ({
		userId: p.user?.id ?? null,
		name: p.user?.username ?? p.guestName ?? 'Sconosciuto',
		avatarUrl: p.user?.avatarUrl ?? null,
		isWinner: p.isWinner,
	}))
}

const LatestMatchCard = ({ match }: LatestMatchCardProps) => {
    const players = flattenPlayers(match);
    const winner = players.find(p => p.isWinner);

    return (
        <Pressable onPress = { () => router.push(`/match/${match.id}`)} className = 'rounded-3xl bg-[#C45135] p-2 active:scale-[0.98] active:opacity-75 shadow-sm mb-1'>
            <View className = 'rounded-[20px] border-2 border-background p-4 items-center text-center'>
                <Text className = 'text-xs font-semibold uppercase tracking-wider text-white/80'>
                    Ultima partita
                </Text>
                <Text className = 'font-display text-2xl text-white mt-1 text-center' numberOfLines = { 2 }>
                    {match.game.name}
                </Text>
                {winner && (
                    <View className = 'mt-3 flex-row items-center gap-1.5 px-3 py-1 rounded-full'>
                        {winner.avatarUrl ? (
                            <Image source = {{ uri: winner.avatarUrl }} style = {{ width: 36, height: 36, borderRadius: 100 }}/>
                        ) : (
                            <View className = 'h-6 w-6 items-center justify-center rounded-full bg-white/20'>
                                <User size = { 14 } color = '#FFFFFF'/>
                            </View>
                        )}
                        <Text className = 'text-lg font-semibold tracking-wide text-white' numberOfLines  = { 1}>
                            {winner.name}
                        </Text>
                        <Crown size = { 24 } color = '#FFD700' fill = '#FFD700'/>
                    </View>
                )}
                <Text className = 'mt-2 text-xs text-white/80'>
                    {formatRelativeDays(match.playedAt)}
                </Text>
            </View>
        </Pressable>
    )
}

export default LatestMatchCard;