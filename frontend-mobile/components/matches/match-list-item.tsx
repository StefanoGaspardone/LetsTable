import { memo, useMemo } from 'react';
import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Dices } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import AvatarStack from '@/components/matches/avatar-stack';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { formatRelativeDays } from '@/lib/date';
import { getAvatarUrl } from '@/lib/file';
import { getMatchSummary } from '@/lib/match-summary';

import { Match } from '@/types/match';

interface MatchCardProps {
	match: Match;
}

const MatchListItem = ({ match }: MatchCardProps) => {
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const { participants, winner, isInProgress } = useMemo(() => getMatchSummary(match), [match]);

	const avatars = participants.map(participant => ({ key: participant.key, uri: getAvatarUrl(participant.avatarId, participant.name) }));

	return (
		<Pressable onPress = { () => router.push(`/match/${match.id}`) } className = 'flex-row items-stretch gap-3 rounded-2xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75'>
			<View className = 'aspect-square overflow-hidden rounded-xl bg-secondary'>
				{match.game.thumbnailUrl ? (
					<Image source = {{ uri: match.game.thumbnailUrl }} recyclingKey = { match.id } style = {{ width: '100%', height: '100%' }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 22 } color = { colors.mutedForeground }/>
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
				{match.isTeamBased ? (
					<AvatarStack avatars = { avatars } ringColor = { colors.background } remainingClassName = 'h-[26px] items-center justify-center px-3' remainingTextClassName = 'text-sm font-medium text-muted-foreground'/>
				) : (
					<AvatarStack avatars = { avatars } ringColor = { colors.background } remainingClassName = 'h-[26px] items-center justify-center rounded-full bg-secondary px-2' remainingTextClassName = 'text-xs font-semibold text-muted-foreground'/>
				)}
			</View>
			{isInProgress ? (
				<View className = 'items-center justify-center'>
					<View className = 'flex-row items-center gap-1.5 rounded-full border border-primary/30 bg-primary/10 px-2.5 py-1'>
						<View className = 'h-1.5 w-1.5 rounded-full bg-primary'/>
						<Text className = 'text-xs font-medium text-primary'>In corso</Text>
					</View>
				</View>
			) : winner && (
				<View className = 'items-center justify-center'>
					<Image source = {{ uri: getAvatarUrl(winner.avatarId, winner.name) }} style = {{ width: 39, height: 39, borderRadius: 100 }} contentFit = 'cover'/>
					<Text className = 'mt-1 text-sm font-semibold text-foreground' numberOfLines = { 1 } style = {{ maxWidth: 60 }}>
						{winner.name}
					</Text>
					{winner.score != null && (
						<Text className = 'text-xs font-medium text-primary'>
							{winner.score} pt
						</Text>
					)}
				</View>
			)}
		</Pressable>
	)
}

export default memo(MatchListItem);