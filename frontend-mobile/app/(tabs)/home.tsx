import { View, ScrollView, Pressable } from 'react-native';
import { router } from 'expo-router';
import { Settings, Library, Trophy, ListPlus, UserPlus } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import QuickStatCard from '@/components/home/quick-stat-card';
import MeepleIllustration from '@/components/common/meeple-illustration';
import EmptyState from '@/components/common/empty-state';
import FabMenu from '@/components/common/fab-menu';
import LatestMatchCard from '@/components/home/latest-match-card';

import { useHomeStats } from '@/hooks/use-stat';
import { useMatches } from '@/hooks/use-match';
import { useMyWishlists } from '@/hooks/use-wishlist';
import WishlistMiniCard from '@/components/home/wishlist-mini-card';

const HomeScreen = () => {
	const { totalMatches, totalGames } = useHomeStats();
	const { data: matchesData } = useMatches({ sort: 'playedAt-desc', size: 1 } as any);
	const { data: wishlists } = useMyWishlists();

	const latestMatch = matchesData?.pages?.[0]?.content?.[0];

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Bentornato'
				rightElement = {
					<Pressable onPress = { () => router.push('/(tabs)/profile') } hitSlop = { 8 }>
						<Settings size = { 22 } className = 'text-muted-foreground'/>
					</Pressable>
				}
			/>
			<ScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 100 }}>
				<Text className = 'font-display text-xl text-foreground'>Le Mie Statistiche</Text>
				<View className = 'flex-row gap-3 mt-1'>
					<QuickStatCard icon = { <MeepleIllustration size = { 48 } color = '#C45135'/> } label = 'Partite totali' value = { totalMatches }/>
					<QuickStatCard icon = { <Library size = { 48 } color = '#C45135'/> } label = 'Giochi posseduti' value = { totalGames }/>
				</View>
				<Text className = 'mt-3 mb-1 font-display text-xl text-foreground'>Ultime Partite</Text>
				{latestMatch ? (
					<LatestMatchCard match = { latestMatch }/>
				) : (
					<EmptyState icon = { <Trophy size = { 32 } color = '#C45135'/> } title = 'Nessuna partita registrata' subtitle = 'Inizia a tracciare le tue serate di gioco.' actionLabel = 'Registra partita' onAction = { () => router.push('/match/new')}/>
				)}
				<Text className = 'mt-3 mb-1 font-display text-xl text-foreground'>Le Mie Wishlist</Text>
				{wishlists && wishlists.length > 0 ? (
					<ScrollView horizontal showsHorizontalScrollIndicator = { false } contentContainerStyle = {{ paddingRight: 16 }}>
						{wishlists.map(wishlist => (
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
					icon: <Trophy size = { 18 } color = '#1c1b1a'/>,
					onPress: () => router.push('/match/new'),
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
		</View>
	)
}

export default HomeScreen;