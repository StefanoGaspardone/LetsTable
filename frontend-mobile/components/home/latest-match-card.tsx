import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { LinearGradient } from 'expo-linear-gradient';
import { router } from 'expo-router';
import { Dices, Crown, User } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Match } from '@/types/match';

import { formatRelativeDays } from '@/lib/date';

import { useAuth } from '@/contexts/auth-context';

interface LatestMatchCardProps {
	match: Match;
}

interface FlatPlayer {
	userId: string | null;
	name: string;
	avatarUrl: string | null;
	score: number | null;
	isWinner: boolean | null;
}

const flattenPlayers = (match: Match): FlatPlayer[] => {
	if(match.isTeamBased && match.teams) {
		return match.teams.flatMap(team =>
			team.players.map(p => ({
				userId: p.user?.id ?? null,
				name: p.user?.username ?? p.guestName ?? 'Sconosciuto',
				avatarUrl: p.user?.avatarUrl ?? null,
				score: team.score,
				isWinner: team.isWinner,
			}))
		)
	}

	return (match.players ?? []).map(p => ({
		userId: p.user?.id ?? null,
		name: p.user?.username ?? p.guestName ?? 'Sconosciuto',
		avatarUrl: p.user?.avatarUrl ?? null,
		score: p.score,
		isWinner: p.isWinner,
	}))
}

const PlayerAvatar = ({ avatarUrl }: { avatarUrl: string | null }) => {
	if(avatarUrl) {
		return (
			<Image source = {{ uri: avatarUrl }} style = {{ width: 22, height: 22, borderRadius: 11 }}/>
		)
	}

	return (
		<View className = 'h-[22px] w-[22px] items-center justify-center rounded-full bg-secondary'>
			<User size = { 12 } className = 'text-muted-foreground'/>
		</View>
	)
}

const LatestMatchCard = ({ match }: LatestMatchCardProps) => {
    const { user: currentUser } = useAuth();

	const players = flattenPlayers(match).sort((a, b) => (b.score ?? 0) - (a.score ?? 0));
	const visiblePlayers = players.slice(0, 4);
	const remainingCount = players.length - visiblePlayers.length;
	const winner = players.find((p) => p.isWinner);
	const iAmWinner = winner?.userId != null && winner.userId === currentUser?.id;

	return (
		<Pressable onPress = { () => router.push(`/match/${match.id}`) } className = 'rounded-2xl border border-border bg-card p-4'>
			<View className = 'mb-3 flex-row items-center justify-between'>
				<Text className = 'flex-1 font-display text-lg text-foreground' numberOfLines = { 1 }>
					{match.game.name}
				</Text>
				{iAmWinner && (
					<View className = 'ml-2 rounded-full bg-primary px-2.5 py-1'>
						<Text className = 'text-[10px] font-bold uppercase tracking-wider text-primary-foreground'>
							Vincitore
						</Text>
					</View>
				)}
			</View>
			<View className = 'flex-row gap-4'>
				<View style = {{ width: 88, height: 88 }} className = 'overflow-hidden rounded-xl bg-secondary'>
					{match.game.thumbnailUrl ? (
						<Image source = {{ uri: match.game.thumbnailUrl }} style = {{ width: 88, height: 88 }} contentFit = 'cover'/>
					) : (
						<View className = 'h-full w-full items-center justify-center'>
							<Dices size = { 26 } className = 'text-muted-foreground'/>
						</View>
					)}
				</View>
				<View className = 'flex-1 justify-center gap-1.5'>
					{visiblePlayers.map((player, index) => {
                        const isMe = player.userId != null && player.userId === currentUser?.id;

						const row = (
							<View className = 'flex-row items-center justify-between px-1.5 py-1'>
								<View className = 'flex-1 flex-row items-center gap-1.5'>
									<PlayerAvatar avatarUrl = { player.avatarUrl }/>
									{player.isWinner && <Crown size = { 12 } color = '#C45135'/>}
									<Text className = { `text-sm ${player.isWinner ? 'font-semibold text-foreground' : 'text-muted-foreground'}`} numberOfLines = { 1 }>
										{player.name}
                                        {isMe && <Text className = 'text-xs text-primary'> (tu)</Text>}
									</Text>
								</View>
								{player.score != null && (
									<Text className = { `text-sm font-bold ${player.isWinner ? 'text-primary' : 'text-muted-foreground'}` }>
										{player.score}
									</Text>
								)}
							</View>
						)

						if(!player.isWinner) return <View key = { index }>{row}</View>;

						return (
							<LinearGradient key = { index } colors = { ['rgba(196,81,53,0.28)', 'rgba(196,81,53,0)'] } start = {{ x: 1, y: 0 }} end = {{ x: 0, y: 0 }} style = {{ borderRadius: 8 }}>
								{row}
							</LinearGradient>
						)
					})}
					{remainingCount > 0 && (
						<Text className = 'px-1.5 text-xs text-muted-foreground'>+{remainingCount} {remainingCount === 1 ? 'altro' : 'altri'}</Text>
					)}
				</View>
			</View>
			<Text className = 'mt-3 text-right text-xs text-muted-foreground'>
				{formatRelativeDays(match.playedAt)}
			</Text>
		</Pressable>
	)
}

export default LatestMatchCard;