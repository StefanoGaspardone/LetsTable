import { memo, useMemo } from 'react';
import { View, Pressable } from 'react-native';
import { Image } from 'expo-image';
import { Crown } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import AvatarStack from '@/components/matches/avatar-stack';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { formatRelativeDays } from '@/lib/date';
import { getAvatarUrl } from '@/lib/file';
import { getMatchSummary } from '@/lib/match-summary';

import { Match } from '@/types/match';

interface LatestMatchCardProps {
	match: Match;
}

const LatestMatchCard = ({ match }: LatestMatchCardProps) => {
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const { winner, others, tiedWinnersCount, margin, isInProgress } = useMemo(() => getMatchSummary(match), [match]);

	const otherAvatars = others.map(other => ({ key: other.key, uri: getAvatarUrl(other.avatarId, other.name) }));

	return (
		<Pressable onPress = { () => router.push(`/match/${match.id}`) } className = 'mb-1 rounded-3xl bg-primary p-2 shadow-sm active:scale-[0.98] active:opacity-75'>
			<View className = 'items-center rounded-[20px] border-2 border-background p-4'>
				<Text className = 'text-xs font-semibold uppercase tracking-wider text-white/80'>
					Ultima partita
				</Text>
				<Text className = 'mt-1 text-center font-display text-2xl text-white' numberOfLines = { 2 }>
					{match.game.name}
				</Text>
				{winner ? (
					<View className = 'mt-3 items-center'>
						<View className = 'flex-row items-center gap-2'>
							<Image source = {{ uri: getAvatarUrl(winner.avatarId, winner.name) }} style = {{ width: 36, height: 36, borderRadius: 18 }} contentFit = 'cover'/>
							<Text className = 'text-lg font-semibold tracking-wide text-white' numberOfLines = { 1 }>
								{winner.name}
							</Text>
							<Crown size = { 22 } color = '#FFD700' fill = '#FFD700'/>
						</View>
						{tiedWinnersCount > 0 && (
							<Text className = 'mt-1 text-xs text-white/80'>
								a pari merito con {tiedWinnersCount === 1 ? 'un altro' : `altri ${tiedWinnersCount}`}
							</Text>
						)}
						{winner.score != null && (
							<View className = 'mt-2 flex-row items-baseline gap-1'>
								<Text className = 'font-display text-3xl text-white'>{winner.score}</Text>
								<Text className = 'text-sm text-white/80'>{winner.score === 1 ? 'punto' : 'punti'}</Text>
							</View>
						)}
						{margin != null && margin > 0 && (
							<Text className = 'text-xs text-white/80'>
								+{margin} di distacco
							</Text>
						)}
					</View>
				) : isInProgress ? (
					<View className = 'mt-3 flex-row items-center gap-1.5 rounded-full bg-white/15 px-3 py-1'>
						<View className = 'h-1.5 w-1.5 rounded-full bg-white'/>
						<Text className = 'text-xs font-medium text-white'>In corso</Text>
					</View>
				) : null}
				<View className = 'mt-3 flex-row items-center gap-2'>
					{otherAvatars.length > 0 && (
						<AvatarStack avatars = { otherAvatars } ringColor = { colors.primary } remainingClassName = 'h-[26px] items-center justify-center rounded-full bg-white/20 px-2' remainingTextClassName = 'text-xs font-semibold text-white'/>
					)}
					<Text className = 'text-xs text-white/80'>
						{formatRelativeDays(match.playedAt)}
					</Text>
				</View>
			</View>
		</Pressable>
	)
}

export default memo(LatestMatchCard);