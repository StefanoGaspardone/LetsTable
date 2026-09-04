import { useState } from 'react';
import { View, Pressable, FlatList, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { Search, List, LayoutGrid, Plus } from 'lucide-react-native';
import { Gesture, GestureDetector } from 'react-native-gesture-handler';
import { scheduleOnRN } from 'react-native-worklets';

import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import GameListItem from '@/components/common/game-list-item';
import GameGridItem from '@/components/common/game-grid-item';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';

import { useDebounce } from '@/hooks/use-debounce';
import { useCollection } from '@/hooks/use-collection';

const FILTER_OPTIONS = [
	{ value: 'all', label: 'Tutti' },
	{ value: 'played', label: 'Giocati' },
	{ value: 'new', label: 'Nuovi' },
]

const SWIPE_DISTANCE_THRESHOLD = 40;
const SWIPE_VELOCITY_THRESHOLD = 500;

const CollectionScreen = () => {
	const [selectedFilter, setSelectedFilter] = useState('all');
	const [viewMode, setViewMode] = useState<'list' | 'grid'>('grid');
	const [search, setSearch] = useState('');
	
	const debouncedSearch = useDebounce(search);

	const playedFilter = selectedFilter === 'played' ? true : selectedFilter === 'new' ? false : undefined;
	const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useCollection(debouncedSearch, playedFilter);
	const items = data?.pages.flatMap((page) => page.content) ?? [];

	const currentIndex = FILTER_OPTIONS.findIndex((option) => option.value === selectedFilter);

	const goToIndex = (index: number) => {
		if(index >= 0 && index < FILTER_OPTIONS.length) setSelectedFilter(FILTER_OPTIONS[index].value);
	}

	const swipeGesture = Gesture.Pan()
		.activeOffsetX([-10, 10])
		.failOffsetY([-10, 10])
		.onEnd(event => {
			const isFastSwipeLeft = event.velocityX < -SWIPE_VELOCITY_THRESHOLD;
			const isFastSwipeRight = event.velocityX > SWIPE_VELOCITY_THRESHOLD;
			const isFarSwipeLeft = event.translationX < -SWIPE_DISTANCE_THRESHOLD;
			const isFarSwipeRight = event.translationX > SWIPE_DISTANCE_THRESHOLD;

			if(isFastSwipeLeft || isFarSwipeLeft) scheduleOnRN(goToIndex, currentIndex + 1);
			else if(isFastSwipeRight || isFarSwipeRight) scheduleOnRN(goToIndex, currentIndex - 1);
		});

	const renderFooter = () => {
		if(isFetchingNextPage) {
			return (
				<View className = 'py-6'>
					<ActivityIndicator/>
				</View>
			)
		}

		if(!hasNextPage && items.length > 0) {
			return (
				<View className = 'py-6'>
					<Text className = 'text-center text-sm text-muted-foreground'>
						Hai visto tutta la tua collezione
					</Text>
				</View>
			)
		}

		return null;
	}

	const renderEmpty = () => {
		if(isLoading) return null;
		
		return (
			<View className = 'items-center py-20'>
				<Text className = 'text-center text-muted-foreground'>
					{debouncedSearch ? 'Nessun gioco trovato' : 'La tua collezione è vuota'}
				</Text>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'La Mia Collezione'/>
			<View className = 'gap-3 px-4 pt-4'>
				<View className = 'flex-row items-center gap-2'>
					<View className = 'relative flex-1'>
						<Input placeholder = 'Filtra...' value = { search }  onChangeText = { setSearch } className = 'rounded-2xl bg-secondary pl-10'/>
						<View className = 'pointer-events-none absolute left-3 -top-0.5 h-full justify-center'>
							<Search size = { 18 } className = 'text-muted-foreground'/>
						</View>
					</View>
					<Pressable onPress = { () => setViewMode(prev => (prev === 'list' ? 'grid' : 'list')) } className = 'h-10 w-10 items-center justify-center rounded-2xl border border-border active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
						{({ pressed }) =>
							viewMode === 'list' ? (
								<LayoutGrid size = { 20 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
							) : (
								<List size = { 20 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
							)
						}
					</Pressable>
				</View>
				<SegmentedControl options = { FILTER_OPTIONS } selected = { selectedFilter } onSelect = { setSelectedFilter }/>
			</View>
			<GestureDetector gesture = { swipeGesture }>
				<View className = 'flex-1'>
					{isLoading ? (
						<View className = 'flex-1 items-center justify-center'>
							<ActivityIndicator/>
						</View>
					) : (
						<FlatList className = 'mt-3' key = { viewMode } data = { items } keyExtractor = { item => item.id } numColumns = { viewMode === 'grid' ? 2 : 1 } columnWrapperStyle = { viewMode === 'grid' ? { paddingHorizontal: 16, gap: 12 } : undefined } contentContainerStyle = {{ paddingBottom: 100, flexGrow: 1 }}
							renderItem = { ({ item }) =>
								viewMode === 'list' ? (
									<GameListItem game = { item.game } onPress = { () => router.push(`/game/${item.game.bggId}`) }/>
								) : (
									<GameGridItem game = { item.game } onPress = { () => router.push(`/game/${item.game.bggId}`) }/>
								)
							}
							onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); }} onEndReachedThreshold = { 0.4 } ListFooterComponent = { renderFooter } ListEmptyComponent = { renderEmpty }
						/>
					)}
				</View>
			</GestureDetector>
			<Pressable onPress = { () => router.push('/browse') } className = 'absolute bottom-6 right-6 h-14 w-14 items-center justify-center rounded-full bg-primary shadow-lg active:bg-primary/90'>
				<Plus size = { 26 } color = '#FFFFFF'/>
			</Pressable>
		</View>
	)
}

export default CollectionScreen;