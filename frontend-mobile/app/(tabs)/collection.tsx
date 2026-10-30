import { useCallback, useMemo, useState } from 'react';
import { View, Pressable, FlatList, ActivityIndicator, ListRenderItemInfo } from 'react-native';
import { useFocusEffect } from 'expo-router';
import { Search, List, LayoutGrid, Plus, X } from 'lucide-react-native';
import { Gesture, GestureDetector, RefreshControl } from 'react-native-gesture-handler';
import { scheduleOnRN } from 'react-native-worklets';

import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import GameListItem from '@/components/games/game-list-item';
import GameGridItem from '@/components/games/game-grid-item';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';

import { useDebounce } from '@/hooks/use-debounce';
import { useCollection } from '@/hooks/use-collection';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { CollectionItem } from '@/types/collection';

const FILTER_OPTIONS = [
	{ value: 'all', label: 'Tutti' },
	{ value: 'played', label: 'Giocati' },
	{ value: 'new', label: 'Nuovi' },
]

const PLAYED_FILTER: Record<string, boolean | undefined> = {
	all: undefined,
	played: true,
	new: false,
}

const SWIPE_DISTANCE_THRESHOLD = 40;
const SWIPE_VELOCITY_THRESHOLD = 500;

const GRID_COLUMN_STYLE = { paddingHorizontal: 16, gap: 12 };
const GRID_CONTENT_STYLE = { paddingBottom: 100, flexGrow: 1 };
const LIST_CONTENT_STYLE = { paddingHorizontal: 16, paddingBottom: 100, flexGrow: 1 };

const keyExtractor = (item: CollectionItem) => item.id;
const ListSeparator = () => <View className = 'h-2'/>;

const CollectionScreen = () => {
	const [selectedFilter, setSelectedFilter] = useState('all');
	const [viewMode, setViewMode] = useState<'list' | 'grid'>('grid');
	const [search, setSearch] = useState('');

	const router = useNavigationStack();
	const { colors } = useThemeColors();

	useFocusEffect(
		useCallback(() => {
			setSearch('');
		}, [])
	);

	const debouncedSearch = useDebounce(search);

	const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useCollection(debouncedSearch, PLAYED_FILTER[selectedFilter]);
	useRefetchOnFocus(['collection']);

	const { refreshing, onRefresh } = usePullToRefresh([['collection']]);

	const items = useMemo(() => data?.pages.flatMap(page => page.content) ?? [], [data]);
	const isGrid = viewMode === 'grid';

	const currentIndex = FILTER_OPTIONS.findIndex(option => option.value === selectedFilter);

	const swipeGesture = useMemo(() => {
		const goToIndex = (index: number) => {
			if(index >= 0 && index < FILTER_OPTIONS.length) setSelectedFilter(FILTER_OPTIONS[index].value);
		}

		return Gesture.Pan()
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
	}, [currentIndex]);

	const openGame = useCallback((bggId: number) => {
		router.push(`/game/${bggId}`);
	}, [router]);

	const renderItem = useCallback(({ item }: ListRenderItemInfo<CollectionItem>) => (
		isGrid
			? <GameGridItem game = { item.game } onPress = { () => openGame(item.game.bggId) }/>
			: <GameListItem game = { item.game } onPress = { () => openGame(item.game.bggId) }/>
	), [isGrid, openGame]);

	const handleEndReached = useCallback(() => {
		if(hasNextPage && !isFetchingNextPage) fetchNextPage();
	}, [hasNextPage, isFetchingNextPage, fetchNextPage]);

	const toggleViewMode = useCallback(() => {
		setViewMode(prev => (prev === 'list' ? 'grid' : 'list'));
	}, []);

	const footer = isFetchingNextPage ? (
		<View className = 'py-6'>
			<ActivityIndicator/>
		</View>
	) : !hasNextPage && items.length > 0 ? (
		<View className = 'py-6'>
			<Text className = 'text-center text-sm text-muted-foreground'>
				Hai visto tutta la tua collezione
			</Text>
		</View>
	) : null;

	const empty = isLoading ? null : (
		<View className = 'items-center py-20'>
			<Text className = 'text-center text-muted-foreground'>
				{debouncedSearch ? 'Nessun gioco trovato' : 'La tua collezione è vuota'}
			</Text>
		</View>
	);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'La Mia Collezione'/>
			<View className = 'gap-3 px-4 pt-4'>
				<View className = 'flex-row items-center gap-2'>
					<View className = 'relative flex-1'>
						<Input placeholder = 'Cerca...' value = { search } onChangeText = { setSearch } className = 'rounded-2xl bg-secondary/80 pl-11 pr-10 border-0 text-sm h-11'/>
						<View className = 'pointer-events-none absolute left-3.5 top-0 bottom-0 justify-center'>
							<Search size = { 18 } color = { colors.mutedForeground }/>
						</View>
						{search.length > 0 && (
							<Pressable onPress = { () => setSearch('') } hitSlop = { 10 } className = 'absolute right-3.5 top-0 bottom-0 justify-center'>
								<X size = { 18 } color = { colors.mutedForeground }/>
							</Pressable>
						)}
					</View>
					<Pressable onPress = { toggleViewMode } className = 'h-11 w-11 items-center justify-center rounded-2xl border-0 bg-secondary/80 active:bg-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
						{({ pressed }) =>
							isGrid ? (
								<List size = { 20 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
							) : (
								<LayoutGrid size = { 20 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
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
						<FlatList
							className = 'mt-3'
							key = { viewMode }
							data = { items }
							keyExtractor = { keyExtractor }
							renderItem = { renderItem }
							numColumns = { isGrid ? 2 : 1 }
							columnWrapperStyle = { isGrid ? GRID_COLUMN_STYLE : undefined }
							contentContainerStyle = { isGrid ? GRID_CONTENT_STYLE : LIST_CONTENT_STYLE }
							ItemSeparatorComponent = { isGrid ? undefined : ListSeparator }
							ListFooterComponent = { footer }
							ListEmptyComponent = { empty }
							onEndReached = { handleEndReached }
							onEndReachedThreshold = { 0.4 }
							windowSize = { 7 }
							refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/> }
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