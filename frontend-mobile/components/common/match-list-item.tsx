import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { router } from 'expo-router';
import { Dices } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { Match } from '@/types/match';

import { formatRelativeDays } from '@/lib/date';
import { getAvatarUrl } from '@/lib/file';

interface MatchCardProps {
	match: Match;
}

interface FlatPlayer {
	name: string;
	avatarId: string | null | undefined;
	isWinner: boolean | null;
}

interface FlatTeam {
	name: string;
	color: string;
	isWinner: boolean;
}

const flattenPlayers = (match: Match): FlatPlayer[] => {
	return (match.players ?? []).map(p => ({
		name: p.user?.username ?? p.guestName ?? 'Sconosciuto',
		avatarId: p.user?.avatarId,
		isWinner: p.isWinner,
	}))
}

const flattenTeams = (match: Match): FlatTeam[] => {
	return (match.teams ?? []).map(team => ({
		name: team.name ?? 'Squadra',
		color: team.color,
		isWinner: team.isWinner,
	}))
}

const MAX_VISIBLE_AVATARS = 4;
const AVATAR_SIZE = 26;
const AVATAR_OVERLAP = 8;

const PlayerAvatarStack = ({ players }: { players: FlatPlayer[] }) => {
	const visiblePlayers = players.slice(0, MAX_VISIBLE_AVATARS);
	const remainingCount = players.length - visiblePlayers.length;

	return (
		<View className = 'flex-row items-center'>
			{visiblePlayers.map((player, index) => (
				<View key = { `${player.name}-${player.avatarId}` } style = {{ marginLeft: index === 0 ? 0 : -AVATAR_OVERLAP, zIndex: visiblePlayers.length - index }}>
					<Image source = {{ uri: getAvatarUrl(player?.avatarId ?? null, player?.name ?? '') }} style = {{ width: AVATAR_SIZE, height: AVATAR_SIZE, borderRadius: AVATAR_SIZE / 2, borderWidth: 2, borderColor: '#F2EFE9' }} contentFit = 'cover'/>
				</View>
			))}
			{remainingCount > 0 && (
				<View style = {{ marginLeft: -AVATAR_OVERLAP }} className = 'h-[26px] items-center justify-center rounded-full bg-secondary px-2'>
					<Text className = 'text-xs font-semibold text-muted-foreground'>+{remainingCount}</Text>
				</View>
			)}
		</View>
	)
}

const TeamDotStack = ({ teams }: { teams: FlatTeam[] }) => {
	const visibleTeams = teams.slice(0, MAX_VISIBLE_AVATARS);
	const remainingCount = teams.length - visibleTeams.length;

	return (
		<View className = 'flex-row items-center'>
			{visibleTeams.map((team, index) => (
				<View key = { `${team.name}-${team.color}` } style = {{ marginLeft: index === 0 ? 0 : -AVATAR_OVERLAP, zIndex: visibleTeams.length - index }}>
					<Image source = {{ uri: getAvatarUrl(null, team.name ?? '') }} style = {{ width: AVATAR_SIZE, height: AVATAR_SIZE, borderRadius: AVATAR_SIZE / 2, borderWidth: 2, borderColor: '#F2EFE9' }} contentFit = 'cover'/>
				</View>
			))}
			{remainingCount > 0 && (
				<View style = {{ marginLeft: -AVATAR_OVERLAP }} className = 'h-[26px] items-center justify-center rounded-full bg-secondary px-2'>
					<Text className = 'text-xs font-semibold text-muted-foreground'>+{remainingCount}</Text>
				</View>
			)}
		</View>
	)
}

const MatchListItem = ({ match }: MatchCardProps) => {
	const isInProgress = match.durationMinutes == null;

	const teams = match.isTeamBased ? flattenTeams(match) : [];
	const players = !match.isTeamBased ? flattenPlayers(match) : [];
	const winningTeam = teams.find(t => t.isWinner);
	const winner = players.find(p => p.isWinner);

	return (
		<Pressable onPress = { () => router.push(`/match/${match.id}`) } className = 'flex-row items-stretch gap-3 rounded-2xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75'>
			<View className = 'aspect-square overflow-hidden rounded-xl bg-secondary'>
				{match.game.thumbnailUrl ? (
					<Image source = {{ uri: match.game.thumbnailUrl }} style = {{ width: '100%', height: '100%' }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 22 } className = 'text-muted-foreground'/>
					</View>
				)}
			</View>
			<View className = 'flex-1 gap-1'>
				<Text className = 'font-semibold text-sm text-foreground' numberOfLines = { 1 }>
					{match.game.name}
				</Text>
				<Text className = 'text-xs text-muted-foreground'>
					{formatRelativeDays(match.playedAt)}
				</Text>
				{match.isTeamBased ? <TeamDotStack teams = { teams }/> : <PlayerAvatarStack players = { players }/>}
			</View>
			{isInProgress ? (
				<View className = 'items-center justify-center'>
					<View className = 'flex-row items-center gap-1.5 rounded-full border border-[#C45135]/30 bg-[#C45135]/10 px-2.5 py-1'>
						<View className = 'h-1.5 w-1.5 rounded-full bg-[#C45135]'/>
						<Text className = 'text-xs font-medium text-[#C45135]'>In corso</Text>
					</View>
				</View>
			) : match.isTeamBased ? (
				winningTeam && (
					<View className = 'items-center justify-center gap-1'>
						<Image source = {{ uri: getAvatarUrl(null, winningTeam?.name ?? '') }} style = {{ width: 39, height: 39, borderRadius: 100 }} contentFit = 'cover'/>
						<Text className = 'text-sm font-semibold text-foreground' numberOfLines = { 1 } style = {{ maxWidth: 60 }}>
							{winningTeam.name}
						</Text>
					</View>
				)
			) : (
				winner && (
					<View className = 'items-center justify-center gap-1'>
						<Image source = {{ uri: getAvatarUrl(winner?.avatarId ?? null, winner?.name ?? '') }} style = {{ width: 39, height: 39, borderRadius: 100 }} contentFit = 'cover'/>
						<Text className = 'text-sm font-semibold text-foreground' numberOfLines = { 1 } style = {{ maxWidth: 60 }}>
							{winner.name}
						</Text>
					</View>
				)
			)}
		</Pressable>
	)
}

export default MatchListItem;