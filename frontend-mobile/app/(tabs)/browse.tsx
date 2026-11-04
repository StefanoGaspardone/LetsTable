import { useCallback, useMemo, useRef, useState } from 'react';
import { View, ActivityIndicator, Pressable, Keyboard, ScrollView } from 'react-native';
import { FlashList, ListRenderItemInfo } from '@shopify/flash-list';
import { RefreshControl } from 'react-native-gesture-handler';
import { router as expoRouter, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { Search, List, LayoutGrid, X, ArrowUpDown, ChevronDown, Cog, Tag, Puzzle, LucideIcon } from 'lucide-react-native';

import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import SortSheet, { SortSheetRef } from '@/components/common/sort-sheet';
import OptionPickerSheet, { OptionPickerSheetRef, PickerOption } from '@/components/common/option-picker-sheet';
import GameListItem from '@/components/games/game-list-item';
import GameGridItem, { GRID_CELL_PADDING, GRID_HORIZONTAL_PADDING } from '@/components/games/game-grid-item';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';
import SegmentedControl from '@/components/common/segmented-control';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useDebounce } from '@/hooks/use-debounce';
import { useBrowseGames, useGameCategories, useGameMechanics, useHotGames, useOverallGames } from '@/hooks/use-game';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { DEFAULT_GAME_SORT, GAME_SORT_OPTIONS } from '@/lib/sort';

import { Game } from '@/types/game';

type RankingMode = 'overall' | 'hotness';
type GameTypeFilter = 'base' | 'expansion';

interface BrowseParams {
	mechanicId?: string;
	mechanicName?: string;
	categoryId?: string;
	categoryName?: string;
	type?: GameTypeFilter;
	[key: string]: string | undefined;
}

const RANKING_OPTIONS = [
	{ value: 'overall', label: 'Classifica' },
	{ value: 'hotness', label: 'In Tendenza' },
];

const TYPE_OPTIONS: PickerOption[] = [
	{ id: 'base', name: 'Giochi base' },
	{ id: 'expansion', name: 'Espansioni' },
];

const RANKED_SORT = 'bggRank-asc';

const GRID_CONTENT_STYLE = { paddingHorizontal: GRID_HORIZONTAL_PADDING, paddingTop: 12, paddingBottom: 40, flexGrow: 1 };
const LIST_CONTENT_STYLE = { paddingHorizontal: 16, paddingTop: 12, paddingBottom: 40, flexGrow: 1 };
const GRID_CELL_STYLE = { paddingHorizontal: GRID_CELL_PADDING, alignItems: 'center' as const };
const FILTER_BAR_STYLE = { gap: 8, paddingRight: 16 };
const VISIBLE_POSITION = { disabled: true };

const keyExtractor = (item: Game) => `${item.bggId}`;
const ListSeparator = () => <View className = 'h-2'/>;

interface FilterChipProps {
	label: string;
	icon: LucideIcon;
	isActive: boolean;
	onPress: () => void;
}

const FilterChip = ({ label, icon: Icon, isActive, onPress }: FilterChipProps) => {
	const { colors } = useThemeColors();
	const color = isActive ? colors.primary : colors.mutedForeground;

	return (
		<Pressable onPress = { onPress } className = { `flex-row items-center gap-1.5 rounded-full px-3 py-1.5 border active:opacity-80 ${isActive ? 'bg-primary/15 border-primary/40' : 'bg-secondary/80 border-border/40'}` }>
			<Icon size = { 13 } color = { color }/>
			<Text className = { `text-xs font-medium ${isActive ? 'text-primary' : 'text-muted-foreground'}` } numberOfLines = { 1 } style = {{ maxWidth: 160 }}>
				{label}
			</Text>
			<ChevronDown size = { 13 } color = { color }/>
		</Pressable>
	)
}

const BrowseScreen = () => {
	const params = useLocalSearchParams() as BrowseParams;

	const [search, setSearch] = useState('');
	const [viewMode, setViewMode] = useState<'list' | 'grid'>('grid');
	const [rankingMode, setRankingMode] = useState<RankingMode>('overall');
	const [isCatalogOpen, setIsCatalogOpen] = useState(false);
	const [sort, setSort] = useState(DEFAULT_GAME_SORT);

	const sortSheetRef = useRef<SortSheetRef>(null);
	const typeSheetRef = useRef<OptionPickerSheetRef>(null);
	const categorySheetRef = useRef<OptionPickerSheetRef>(null);
	const mechanicSheetRef = useRef<OptionPickerSheetRef>(null);

	const router = useNavigationStack();
	const { colors } = useThemeColors();

	useFocusEffect(
		useCallback(() => {
			setSearch('');
			setSort(DEFAULT_GAME_SORT);
			setIsCatalogOpen(false);
		}, [])
	);

	const mechanic = params.mechanicId ? { id: params.mechanicId, name: params.mechanicName ?? '' } : null;
	const category = params.categoryId ? { id: params.categoryId, name: params.categoryName ?? '' } : null;
	const gameType = params.type === 'base' || params.type === 'expansion' ? params.type : null;

	const debouncedSearch = useDebounce(search);
	const hasSearch = debouncedSearch.length > 0;
	const hasFilters = !!mechanic || !!category || !!gameType;
	const isSortActive = sort !== DEFAULT_GAME_SORT;
	const isCatalogMode = isCatalogOpen || hasSearch || hasFilters || isSortActive;
	const isBggSearch = hasSearch && !hasFilters && !isSortActive;
	const isGrid = viewMode === 'grid';

	const showRank = !isCatalogMode || sort === RANKED_SORT;
	const rankField = isCatalogMode || rankingMode === 'overall' ? 'bggRank' : 'rank';

	const browseQuery = useBrowseGames({
		query: hasSearch ? debouncedSearch : undefined,
		mechanicId: mechanic?.id,
		categoryId: category?.id,
		isExpansion: gameType ? gameType === 'expansion' : undefined,
		sort: isSortActive ? sort : undefined,
	}, isCatalogMode);

	const overallQuery = useOverallGames(rankingMode === 'overall');
	const hotQuery = useHotGames(rankingMode === 'hotness');

	const { data: mechanics, isLoading: isLoadingMechanics } = useGameMechanics(isCatalogMode);
	const { data: categories, isLoading: isLoadingCategories } = useGameCategories(isCatalogMode);

	const { refreshing, onRefresh } = usePullToRefresh([['games', 'hot'], ['games', 'overall'], ['games', 'browse']]);

	const rankingQuery = rankingMode === 'overall' ? overallQuery : hotQuery;
	const activeQuery = isCatalogMode ? browseQuery : rankingQuery;
	const { hasNextPage, isFetchingNextPage, fetchNextPage } = activeQuery;

	const items = useMemo(() => activeQuery.data?.pages.flatMap(page => page.content) ?? [], [activeQuery.data]);

	const sortLabel = GAME_SORT_OPTIONS.find(option => option.value === sort)?.label ?? 'Ordina';
	const typeLabel = TYPE_OPTIONS.find(option => option.id === gameType)?.name ?? 'Tipo';

	const updateFilters = (changes: Partial<BrowseParams>) => {
		expoRouter.setParams(changes);
	}

	const handleSelectMechanic = (option: PickerOption | null) => {
		updateFilters({ mechanicId: option?.id, mechanicName: option?.name });
	}

	const handleSelectCategory = (option: PickerOption | null) => {
		updateFilters({ categoryId: option?.id, categoryName: option?.name });
	}

	const handleSelectType = (option: PickerOption | null) => {
		updateFilters({ type: option?.id as GameTypeFilter | undefined });
	}

	const handleClear = () => {
		if(search.length > 0) {
			setSearch('');
			return;
		}

		setIsCatalogOpen(false);
		setSort(DEFAULT_GAME_SORT);
		updateFilters({ mechanicId: undefined, mechanicName: undefined, categoryId: undefined, categoryName: undefined, type: undefined });
		Keyboard.dismiss();
	}

	const openGame = useCallback((bggId: number) => {
		router.push(`/game/${bggId}`);
	}, [router]);

	const renderItem = useCallback(({ item, index }: ListRenderItemInfo<Game>) => {
		if(!isGrid) {
			return <GameListItem game = { item } onPress = { () => openGame(item.bggId) } showRank = { showRank } rankField = { rankField }/>;
		}

		const partner = items[index % 2 === 0 ? index + 1 : index - 1];

		return (
			<View style = { GRID_CELL_STYLE }>
				<GameGridItem game = { item } partner = { partner } onPress = { () => openGame(item.bggId) } showRank = { showRank } rankField = { rankField }/>
			</View>
		)
	}, [isGrid, showRank, rankField, openGame, items]);

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
				{isCatalogMode ? 'Fine dei risultati' : 'Fine della classifica'}
			</Text>
		</View>
	) : null;

	const empty = activeQuery.isLoading ? null : (
		<View className = 'items-center px-6 py-20'>
			<Text className = 'text-center text-muted-foreground'>
				{isBggSearch ? 'Nessun gioco trovato su BGG' : isCatalogMode ? 'Nessun gioco trovato' : 'Nessun gioco in classifica'}
			</Text>
			{isCatalogMode && !isBggSearch && (
				<Text className = 'mt-2 text-center text-xs text-muted-foreground'>
					I filtri cercano tra i giochi già presenti in Let&apos;s Table. Prova a togliere qualche filtro o a cercare per nome.
				</Text>
			)}
		</View>
	);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Sfoglia Giochi' leftElement = { <BackButton/> }/>
			<View className = 'gap-3 px-4 pt-4'>
				<View className = 'flex-row items-center gap-2'>
					<View className = 'relative flex-1'>
						<Input placeholder = 'Cerca...' value = { search } onChangeText = { setSearch } onFocus = { () => setIsCatalogOpen(true) } className = 'rounded-2xl bg-secondary/80 pl-11 pr-10 border-0 text-sm h-11'/>
						<View className = 'pointer-events-none absolute left-3.5 top-0 bottom-0 justify-center'>
							<Search size = { 18 } color = { colors.mutedForeground }/>
						</View>
						{(search.length > 0 || isCatalogMode) && (
							<Pressable onPress = { handleClear } hitSlop = { 10 } className = 'absolute right-3.5 top-0 bottom-0 justify-center'>
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
				{isCatalogMode ? (
					<ScrollView horizontal showsHorizontalScrollIndicator = { false } keyboardShouldPersistTaps = 'handled' contentContainerStyle = { FILTER_BAR_STYLE } style = {{ marginRight: -16 }} className = 'mb-2'>
						<FilterChip label = { sortLabel } icon = { ArrowUpDown } isActive = { isSortActive } onPress = { () => sortSheetRef.current?.present() }/>
						<FilterChip label = { typeLabel } icon = { Puzzle } isActive = { !!gameType } onPress = { () => typeSheetRef.current?.present() }/>
						<FilterChip label = { category?.name || 'Categoria' } icon = { Tag } isActive = { !!category } onPress = { () => categorySheetRef.current?.present() }/>
						<FilterChip label = { mechanic?.name || 'Meccanica' } icon = { Cog } isActive = { !!mechanic } onPress = { () => mechanicSheetRef.current?.present() }/>
					</ScrollView>
				) : (
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
					keyboardShouldPersistTaps = 'handled'
					maintainVisibleContentPosition = { VISIBLE_POSITION }
					refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/> }
				/>
			)}
			<SortSheet ref = { sortSheetRef } options = { GAME_SORT_OPTIONS } selected = { sort } onSelect = { setSort }/>
			<OptionPickerSheet ref = { typeSheetRef } title = 'Tipo di gioco' allLabel = 'Tutti' options = { TYPE_OPTIONS } selectedId = { gameType } searchable = { false } onSelect = { handleSelectType }/>
			<OptionPickerSheet ref = { categorySheetRef } title = 'Categoria' allLabel = 'Tutte le categorie' options = { categories ?? [] } selectedId = { category?.id ?? null } isLoading = { isLoadingCategories } onSelect = { handleSelectCategory }/>
			<OptionPickerSheet ref = { mechanicSheetRef } title = 'Meccanica' allLabel = 'Tutte le meccaniche' options = { mechanics ?? [] } selectedId = { mechanic?.id ?? null } isLoading = { isLoadingMechanics } onSelect = { handleSelectMechanic }/>
		</View>
	)
}

export default BrowseScreen;