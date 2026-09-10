import { useRef, useState } from 'react';
import { View, ScrollView, Pressable } from 'react-native';
import { router } from 'expo-router';
import { Settings, Library, Trophy, ListPlus, Users, UserPlus } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import QuickStatCard from '@/components/home/quick-stat-card';
import EmptyState from '@/components/common/empty-state';
import FabMenu from '@/components/common/fab-menu';
import LatestMatchCard from '@/components/home/latest-match-card';
import WishlistMiniCard from '@/components/home/wishlist-mini-card';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';
import WinRateCard from '@/components/home/win-rate-card';
import RecentMatchCard from '@/components/home/recent-match-card';

import { useHomeStats } from '@/hooks/use-stat';
import { useMatches } from '@/hooks/use-match';
import { useMyWishlists } from '@/hooks/use-wishlist';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { useFriends } from '@/hooks/use-friend';

const HomeScreen = () => {
	const { totalWins, totalMatches, totalGames } = useHomeStats();
	const { data: friends } = useFriends();
	const { data: matchesData } = useMatches({ sort: 'playedAt-desc', size: 5 } as any);
	const { data: wishlists } = useMyWishlists();

	useRefetchOnFocus(['matches']);
	useRefetchOnFocus(['collection']);
	useRefetchOnFocus(['wishlists']);
	useRefetchOnFocus(['friends']);

	const [isSettingsPressed, setIsSettingsPressed] = useState(false);

	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);

	const recentMatches = matchesData?.pages?.[0]?.content ?? [];
	const latestMatch = recentMatches[0];
	const otherRecentMatches = recentMatches.slice(1, 5);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Bentornato'
				rightElement = {
					<Pressable onPress = { () => router.push('/(tabs)/profile') } onPressIn = { () => setIsSettingsPressed(true) } onPressOut = { () => setIsSettingsPressed(false) } hitSlop = { 8 } style = {{ height: 36, width: 36, alignItems: 'center', justifyContent: 'center', borderRadius: 999, backgroundColor: isSettingsPressed ? '#DDD8CE' : '#E9E4DB' }}>
						<Settings size = { 22 } className = 'text-muted-foreground'/>
					</Pressable>
				}
			/>
			<ScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 100 }}>
				<Text className = 'font-display text-xl text-foreground'>Le Mie Statistiche</Text>
				<View className = 'mt-1 gap-3'>
					{totalMatches > 0 && (
						<WinRateCard totalWins = { totalWins } totalMatches = { totalMatches }/>
					)}
					<View className = 'flex-row gap-3'>
						<QuickStatCard icon = { <Library size = { 48 } color = '#C45135'/> } label = 'Collezione' value = { totalGames }/>
						<QuickStatCard icon = { <Users size = { 48 } color = '#C45135'/> } label = 'Amici' value = { friends?.length ?? 0 }/>
					</View>
				</View>
				<Text className = 'mt-3 mb-1 font-display text-xl text-foreground'>Partite Recenti</Text>
				{latestMatch ? (
					<View className = 'gap-2'>
						<LatestMatchCard match = { latestMatch }/>
						{otherRecentMatches.map(match => (
							<RecentMatchCard key = { match.id } match = { match }/>
						))}
					</View>
				) : (
					<EmptyState icon = { <Trophy size = { 32 } color = '#C45135'/> } title = 'Nessuna partita registrata' subtitle = 'Inizia a tracciare le tue serate di gioco.' actionLabel = 'Registra partita' onAction = { () => router.push('/match/new')}/>
				)}
				<Text className = 'mt-3 mb-1 font-display text-xl text-foreground'>Le Mie Wishlist</Text>
				{wishlists && wishlists.length > 0 ? (
					<ScrollView horizontal showsHorizontalScrollIndicator = { false } contentContainerStyle = {{ paddingRight: 16 }}>
						{[...wishlists]
							.sort((a, b) => (b.isDefault ? 1 : 0) - (a.isDefault ? 1 : 0))
							.map(wishlist => (
								<WishlistMiniCard key = { wishlist.id } wishlist = { wishlist }/>
							))}
					</ScrollView>
				) : (
					<Text className = 'text-sm text-muted-foreground'>Nessuna wishlist ancora</Text>
				)}
			</ScrollView>
			<FabMenu actions = { [
				{
					label: 'Registra partita',
					icon: <Trophy size = { 18 } className = 'text-foreground'/>,
					onPress: () => registerMatchSheetRef.current?.present(),
				},
				{
					label: 'Aggiungi gioco',
					icon: <ListPlus size = { 18 } color = '#1c1b1a'/>,
					onPress: () => router.push('/browse'),
				},
				{
					label: 'Aggiungi amico',
					icon: <UserPlus size = { 18 } color = '#1c1b1a'/>,
					onPress: () => router.push('/(tabs)/friends'),
				},
			] }/>
			<RegisterMatchSheet ref = { registerMatchSheetRef }/>
		</View>
	)
}

export default HomeScreen;