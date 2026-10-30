import { useCallback, useMemo, useState } from 'react';
import { View, ActivityIndicator, Pressable } from 'react-native';
import { FlashList, ListRenderItemInfo } from '@shopify/flash-list';
import { RefreshControl } from 'react-native-gesture-handler';
import { useFocusEffect } from 'expo-router';
import { Search, List, LayoutGrid, X } from 'lucide-react-native';

import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import GameListItem from '@/components/games/game-list-item';
import GameGridItem, { GRID_CELL_PADDING, GRID_HORIZONTAL_PADDING } from '@/components/games/game-grid-item';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import SegmentedControl from '@/components/common/segmented-control';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useDebounce } from '@/hooks/use-debounce';
import { useGameSearch, useHotGames, useOverallGames } from '@/hooks/use-game';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { Game } from '@/types/game';

type RankingMode = 'overall' | 'hotness';

const RANKING_OPTIONS = [
	{ value: 'overall', label: 'Classifica' },
	{ value: 'hotness', label: 'In Tendenza' },
];

const GRID_CONTENT_STYLE = { paddingHorizontal: GRID_HORIZONTAL_PADDING, paddingTop: 12, paddingBottom: 40, flexGrow: 1 };
const LIST_CONTENT_STYLE = { paddingHorizontal: 16, paddingTop: 12, paddingBottom: 40, flexGrow: 1 };
const GRID_CELL_STYLE = { paddingHorizontal: GRID_CELL_PADDING, alignItems: 'center' as const };
const VISIBLE_POSITION = { disabled: true };

const keyExtractor = (item: Game) => `${item.bggId}`;
const ListSeparator = () => <View className = 'h-2'/>;

const BrowseScreen = () => {
	const [search, setSearch] = useState('');
	const [viewMode, setViewMode] = useState<'list' | 'grid'>('grid');
	const [rankingMode, setRankingMode] = useState<RankingMode>('overall');

	const router = useNavigationStack();
	const { colors } = useThemeColors();

	useFocusEffect(
		useCallback(() => {
			setSearch('');
		}, [])
	);

	const debouncedSearch = useDebounce(search);
	const isSearching = debouncedSearch.length > 0;
	const isGrid = viewMode === 'grid';
	const rankField = rankingMode === 'overall' ? 'bggRank' : 'rank';

	const searchQuery = useGameSearch(debouncedSearch);
	const overallQuery = useOverallGames(rankingMode === 'overall');
	const hotQuery = useHotGames(rankingMode === 'hotness');

	const { refreshing, onRefresh } = usePullToRefresh([['games', 'hot'], ['games', 'overall']]);

	const rankingQuery = rankingMode === 'overall' ? overallQuery : hotQuery;
	const activeQuery = isSearching ? searchQuery : rankingQuery;
	const { hasNextPage, isFetchingNextPage, fetchNextPage } = activeQuery;

	const items = useMemo(() => activeQuery.data?.pages.flatMap(page => page.content) ?? [], [activeQuery.data]);

	const openGame = useCallback((bggId: number) => {
		router.push(`/game/${bggId}`);
	}, [router]);

	const renderItem = useCallback(({ item, index }: ListRenderItemInfo<Game>) => {
		if(!isGrid) {
			return <GameListItem game = { item } onPress = { () => openGame(item.bggId) } showRank = { !isSearching } rankField = { rankField }/>;
		}

		const partner = items[index % 2 === 0 ? index + 1 : index - 1];

		return (
			<View style = { GRID_CELL_STYLE }>
				<GameGridItem game = { item } partner = { partner } onPress = { () => openGame(item.bggId) } showRank = { !isSearching } rankField = { rankField }/>
			</View>
		)
	}, [isGrid, isSearching, rankField, openGame, items]);

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
		<View className = 'pt-6'>
			<Text className = 'text-center text-sm text-muted-foreground'>
				{isSearching ? 'Fine dei risultati' : 'Fine della classifica'}
			</Text>
		</View>
	) : null;

	const empty = activeQuery.isLoading ? null : (
		<View className = 'items-center py-20'>
			<Text className = 'text-center text-muted-foreground'>
				{isSearching ? 'Nessun gioco trovato su BGG' : 'Nessun gioco in classifica'}
			</Text>
		</View>
	);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Sfoglia Giochi' leftElement = { <BackButton/> }/>
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
				{!isSearching && (
					<SegmentedControl options = { RANKING_OPTIONS } selected = { rankingMode } onSelect = { value => setRankingMode(value as RankingMode) }/>
				)}
			</View>
			{activeQuery.isLoading ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator/>
				</View>
			) : (
				<FlashList
					key = { viewMode }
					data = { items }
					keyExtractor = { keyExtractor }
					renderItem = { renderItem }
					numColumns = { isGrid ? 2 : 1 }
					contentContainerStyle = { isGrid ? GRID_CONTENT_STYLE : LIST_CONTENT_STYLE }
					ItemSeparatorComponent = { isGrid ? undefined : ListSeparator }
					ListFooterComponent = { footer }
					ListEmptyComponent = { empty }
					onEndReached = { handleEndReached }
					onEndReachedThreshold = { 0.4 }
					maintainVisibleContentPosition = { VISIBLE_POSITION }
					refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/> }
				/>
			)}
		</View>
	)
}

export default BrowseScreen;