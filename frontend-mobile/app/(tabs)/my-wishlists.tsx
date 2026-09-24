import { useRef, useState } from 'react';
import { View, FlatList, ActivityIndicator } from 'react-native';
import { RefreshControl } from 'react-native-gesture-handler';
import { Heart, Lock, Users } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import ComingSoon from '@/components/common/coming-soon';
import SegmentedControl from '@/components/common/segmented-control';
import WishlistMiniCard from '@/components/common/wishlist-mini-card';
import FabMenu from '@/components/common/fab-menu';
import CreateWishlistSheet, { CreateWishlistSheetRef } from '@/components/common/create-wishlist-sheet';

import { useDefaultWishlist, useMyWishlists } from '@/hooks/use-wishlist';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';

import { WishlistFilterType } from '@/api/wishlist';

const FILTER_OPTIONS = [
	{ value: 'all', label: 'Tutte' },
	{ value: 'SHARED', label: 'Condivise' },
	{ value: 'PRIVATE', label: 'Private' },
];

const MyWishlistsScreen = () => {
	const createWishlistSheetRef = useRef<CreateWishlistSheetRef>(null);
	const [filter, setFilter] = useState<WishlistFilterType | 'all'>('all');

	const { data: defaultWishlist } = useDefaultWishlist();
	const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useMyWishlists(filter === 'all' ? undefined : { type: filter });

	useRefetchOnFocus(['wishlists']);
	const { refreshing, onRefresh } = usePullToRefresh([['wishlists']]);

	const otherWishlists = (data?.pages.flatMap(page => page.content) ?? []).filter(w => !w.isDefault);
	const showDefaultSection = filter === 'all' && !!defaultWishlist;

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Le Mie Wishlist' leftElement = { <BackButton/> }/>
			<View className = 'px-4 pt-3'>
				<SegmentedControl options = { FILTER_OPTIONS } selected = { filter } onSelect = { value => setFilter(value as WishlistFilterType | 'all') }/>
			</View>
			{isLoading ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator color = '#C45135'/>
				</View>
			) : (otherWishlists.length > 0 || showDefaultSection) ? (
				<FlatList className = 'mt-3' data = { otherWishlists } keyExtractor = { item => item.id } contentContainerStyle = {{ paddingHorizontal: 16, paddingBottom: 100 }} ItemSeparatorComponent = { () => <View className = 'h-2'/> } refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = '#C45135' colors = { ['#C45135'] } progressBackgroundColor = '#F2EFE9'/> }
					ListHeaderComponent = {
						showDefaultSection ? (
							<View className = 'mb-4'>
								<Text className = 'mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground'>La tua wishlist principale</Text>
								<WishlistMiniCard wishlist = { defaultWishlist }/>
								{otherWishlists.length > 0 && (
									<Text className = 'mt-5 mb-[-8px] text-xs font-semibold uppercase tracking-wide text-muted-foreground'>Altre wishlist</Text>
								)}
							</View>
						) : null
					}
					renderItem = { ({ item }) => <WishlistMiniCard wishlist = { item }/> } onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); } } onEndReachedThreshold = { 0.4 }
					ListFooterComponent = {
						isFetchingNextPage ? (
							<View className = 'py-6'>
								<ActivityIndicator color = '#C45135'/>
							</View>
						) : null
					}
				/>
			) : (
				<ComingSoon icon = { filter === 'all' ? <Heart size = { 40 } color = '#C45135'/> : filter === 'SHARED' ? <Users size = { 40 } color = '#C45135'/> : <Lock size = { 40 } color = '#C45135'/> } title = { filter === 'all' ? 'Nessuna wishlist' : filter === 'SHARED' ? 'Nessuna wishlist condivisa' : 'Nessuna wishlist privata' } subtitle = { filter === 'all' ? 'Crea la tua prima lista dei desideri con il pulsante qui sotto.' : 'Prova a cambiare filtro, oppure creane una nuova.' }/>
			)}
			<FabMenu
				actions = { [
					{
						label: 'Nuova wishlist',
						icon: <Heart size = { 18 } className = 'text-foreground'/>,
						onPress: () => createWishlistSheetRef.current?.present(),
					},
				] }
			/>
			<CreateWishlistSheet ref = { createWishlistSheetRef }/>
		</View>
	)
}

export default MyWishlistsScreen;