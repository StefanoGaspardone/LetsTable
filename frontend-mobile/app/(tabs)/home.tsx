import { useRef } from 'react';
import { View, ScrollView, Pressable } from 'react-native';
import { Library, Trophy, ListPlus, Users, UserPlus } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import QuickStatCard from '@/components/home/quick-stat-card';
import EmptyState from '@/components/common/empty-state';
import FabMenu from '@/components/common/fab-menu';
import LatestMatchCard from '@/components/home/latest-match-card';
import WishlistMiniCard from '@/components/common/wishlist-mini-card';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';
import WinRateCard from '@/components/home/win-rate-card';
import MatchListItem from '@/components/common/match-list-item';

import { useHomeStats } from '@/hooks/use-stat';
import { useMatches } from '@/hooks/use-match';
import { useMyWishlists } from '@/hooks/use-wishlist';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { useFriends } from '@/hooks/use-friend';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

const HomeScreen = () => {
	const { totalWins, totalMatches, totalGames } = useHomeStats();
	const { data: friends } = useFriends();
	const { data: matchesData } = useMatches({ sort: 'playedAt-desc', size: 5 } as any);
	const { colors } = useThemeColors();
  	
	const { data: wishlistsData } = useMyWishlists();
	const wishlists = wishlistsData?.pages?.[0]?.content ?? [];

	const router = useNavigationStack();

	useRefetchOnFocus(['matches']);
	useRefetchOnFocus(['collection']);
	useRefetchOnFocus(['wishlists']);
	useRefetchOnFocus(['friends']);

	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);

	const recentMatches = (matchesData?.pages?.[0]?.content ?? []).filter(match => match.durationMinutes != null);
	const latestMatch = recentMatches[0];
	const otherRecentMatches = recentMatches.slice(1, 5);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Bentornato'/>
			<ScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 24 }}>
				<Text className = 'font-display text-xl text-foreground'>Le Mie Statistiche</Text>
				<View className = 'mt-1 gap-3'>
					{totalMatches > 0 && (
						<WinRateCard totalWins = { totalWins } totalMatches = { totalMatches }/>
					)}
					<View className = 'flex-row gap-3'>
						<QuickStatCard icon = { <Library size = { 48 } color = { colors.primary }/> } label = 'Collezione' value = { totalGames }/>
						<QuickStatCard icon = { <Users size = { 48 } color = { colors.primary }/> } label = 'Amici' value = { friends?.length ?? 0 }/>
					</View>
				</View>
				<View className = 'mt-4 mb-1 flex-row items-center justify-between'>
					<Text className = 'font-display text-xl text-foreground'>Partite Recenti</Text>
						{latestMatch && (
						<Pressable onPress = { () => router.push('/(tabs)/matches') } hitSlop = { 8 }>
							<Text className = 'text-sm font-semibold text-primary'>Vedi tutte</Text>
						</Pressable>
					)}
				</View>
				{latestMatch ? (
					<View className = 'gap-2'>
						<LatestMatchCard match = { latestMatch }/>
						{otherRecentMatches.map(match => (
							<MatchListItem key = { match.id } match = { match }/>
						))}
					</View>
				) : (
					<EmptyState icon = { <Trophy size = { 32 } color = { colors.primary }/> } title = 'Nessuna partita registrata' subtitle = 'Inizia a tracciare le tue serate di gioco.' actionLabel = 'Registra partita' onAction = { () => registerMatchSheetRef.current?.present()}/>
				)}
				<View className = 'mt-4 mb-1 flex-row items-center justify-between'>
					<Text className = 'font-display text-xl text-foreground'>Le Mie Wishlist</Text>
						{latestMatch && (
						<Pressable onPress = { () => router.push('/(tabs)/my-wishlists') } hitSlop = { 8 }>
							<Text className = 'text-sm font-semibold text-primary'>Vedi tutte</Text>
						</Pressable>
					)}
				</View>
				{wishlists.length > 0 ? (
					<View className = 'gap-2'>
						{[...wishlists]
							.sort((a, b) => (b.isDefault ? 1 : 0) - (a.isDefault ? 1 : 0))
							.slice(0, 5)
							.map(wishlist => (
								<WishlistMiniCard key = { wishlist.id } wishlist = { wishlist }/>
							))}
					</View>
				) : (
					<Text className = 'text-sm text-muted-foreground'>Nessuna wishlist ancora</Text>
				)}
			</ScrollView>
			<FabMenu actions = { [
				{
					label: 'Registra partita',
					icon: <Trophy size = { 18 }/>,
					onPress: () => registerMatchSheetRef.current?.present(),
				},
				{
					label: 'Aggiungi gioco',
					icon: <ListPlus size = { 18 }/>,
					onPress: () => router.push('/browse'),
				},
				{
					label: 'Aggiungi amico',
					icon: <UserPlus size = { 18 }/>,
					onPress: () => router.push('/(tabs)/friends'),
				},
			] }/>
			<RegisterMatchSheet ref = { registerMatchSheetRef }/>
		</View>
	)
}

export default HomeScreen;