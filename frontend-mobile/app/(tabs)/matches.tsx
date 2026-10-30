import { useCallback, useMemo, useRef, useState } from 'react';
import { View, ActivityIndicator, FlatList, Pressable, ListRenderItemInfo } from 'react-native';
import { Calendar, DateData } from 'react-native-calendars';
import { RefreshControl, ScrollView } from 'react-native-gesture-handler';
import { ArrowUpDown, SlidersHorizontal, Trophy, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import MatchListItem from '@/components/matches/match-list-item';
import MatchesCalendarDay from '@/components/matches/matches-calendar-day';
import FabMenu from '@/components/common/fab-menu';
import GameFilterSheet, { GameFilterSheetRef } from '@/components/games/game-filter-sheet';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/matches/register-match-sheet';
import SortSheet, { SortSheetRef, SortOption } from '@/components/common/sort-sheet';
import GameStatsListItem from '@/components/games/game-stats-list-item';

import { useGameStats, useMatchCalendar, useMatches, useMatchesByDay } from '@/hooks/use-match';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { formatFullDate, toLocalDateString } from '@/lib/date';

import { GameMatchStats, Match } from '@/types/match';

type ViewMode = 'list' | 'calendar';
type ListSubView = 'matches' | 'stats';

const VIEW_OPTIONS = [
	{ value: 'calendar', label: 'Calendario' },
	{ value: 'list', label: 'Lista' },
]

const LIST_SUB_VIEW_OPTIONS = [
	{ value: 'matches', label: 'Partite' },
	{ value: 'stats', label: 'Statistiche' },
]

const MATCH_SORT_OPTIONS: SortOption[] = [
	{ value: 'playedAt-desc', label: 'Più recenti' },
	{ value: 'playedAt-asc', label: 'Meno recenti' },
	{ value: 'durationMinutes-desc', label: 'Più lunghe' },
	{ value: 'durationMinutes-asc', label: 'Più corte' },
]

const STATS_SORT_OPTIONS: SortOption[] = [
	{ value: 'matchCount-desc', label: 'Più giocati' },
	{ value: 'matchCount-asc', label: 'Meno giocati' },
	{ value: 'totalMinutes-desc', label: 'Più tempo giocato' },
	{ value: 'totalMinutes-asc', label: 'Meno tempo giocato' },
	{ value: 'avgMinutes-desc', label: 'Durata media più lunga' },
	{ value: 'avgMinutes-asc', label: 'Durata media più corta' },
	{ value: 'winRate-desc', label: 'Win rate più alto' },
	{ value: 'winRate-asc', label: 'Win rate più basso' },
	{ value: 'lastPlayedAt-desc', label: 'Giocati di recente' },
	{ value: 'gameName-asc', label: 'Nome (A-Z)' },
]

const DEFAULT_MATCH_SORT = 'playedAt-desc';
const DEFAULT_STATS_SORT = 'matchCount-desc';

const LIST_CONTENT_STYLE = { paddingHorizontal: 16, paddingTop: 12, paddingBottom: 100, flexGrow: 1 };

const matchKeyExtractor = (item: Match) => item.id;
const statsKeyExtractor = (item: GameMatchStats) => item.gameId;
const renderMatch = ({ item }: ListRenderItemInfo<Match>) => <MatchListItem match = { item }/>;
const renderStats = ({ item }: ListRenderItemInfo<GameMatchStats>) => <GameStatsListItem stats = { item }/>;
const ListSeparator = () => <View className = 'h-2'/>;

const MatchesScreen = () => {
	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);
	const gameFilterSheetRef = useRef<GameFilterSheetRef>(null);
	const sortSheetRef = useRef<SortSheetRef>(null);

	const [today] = useState(() => new Date());

	const [viewMode, setViewMode] = useState<ViewMode>('calendar');
	const [listSubView, setListSubView] = useState<ListSubView>('matches');
	const [matchSort, setMatchSort] = useState(DEFAULT_MATCH_SORT);
	const [statsSort, setStatsSort] = useState(DEFAULT_STATS_SORT);
	const [gameFilter, setGameFilter] = useState<{ id: string; name: string } | null>(null);
	const [calendarYear, setCalendarYear] = useState(today.getFullYear());
	const [calendarMonth, setCalendarMonth] = useState(today.getMonth() + 1);
	const [selectedDate, setSelectedDate] = useState<string | null>(() => toLocalDateString(today));

	const isCalendar = viewMode === 'calendar';
	const isMatchesList = viewMode === 'list' && listSubView === 'matches';
	const isStatsList = viewMode === 'list' && listSubView === 'stats';

	const matchesQuery = useMatches({ size: 20, sort: matchSort, gameId: gameFilter?.id }, isMatchesList);
	const statsQuery = useGameStats(statsSort, isStatsList);
	const { data: dayCounts } = useMatchCalendar(calendarYear, calendarMonth, isCalendar);
	const { data: selectedDayMatches, isLoading: isLoadingSelectedDay } = useMatchesByDay(selectedDate, isCalendar);

	useRefetchOnFocus(['matches']);

	const { refreshing, onRefresh } = usePullToRefresh([['matches']]);
	const { colors, colorScheme } = useThemeColors();

	const matches = useMemo(() => matchesQuery.data?.pages.flatMap(page => page.content) ?? [], [matchesQuery.data]);
	const gameStats = useMemo(() => statsQuery.data?.pages.flatMap(page => page.content) ?? [], [statsQuery.data]);

	const markedDates = useMemo(() => {
		const map: Record<string, { count?: number; selected?: boolean }> = {};

		dayCounts?.forEach(day => {
			map[day.date] = { count: day.count };
		});

		if(selectedDate) map[selectedDate] = { ...map[selectedDate], selected: true };

		return map;
	}, [dayCounts, selectedDate]);

	const calendarTheme = useMemo(() => ({
		backgroundColor: 'transparent',
		calendarBackground: 'transparent',
		textSectionTitleColor: colors.mutedForeground,
		todayTextColor: colors.primary,
		arrowColor: colors.primary,
		monthTextColor: colors.foreground,
		...({
			'stylesheet.calendar.main': {
				week: {
					marginTop: 0,
					marginBottom: 0,
					flexDirection: 'row',
					justifyContent: 'space-around',
				},
			},
		} as any),
	}), [colors]);

	const handleMonthChange = useCallback((month: DateData) => {
		setCalendarYear(month.year);
		setCalendarMonth(month.month);
	}, []);

	const handleDayPress = useCallback((day: DateData) => {
		setSelectedDate(day.dateString);
	}, []);

	const handleMatchesEndReached = useCallback(() => {
		if(matchesQuery.hasNextPage && !matchesQuery.isFetchingNextPage) matchesQuery.fetchNextPage();
	}, [matchesQuery.hasNextPage, matchesQuery.isFetchingNextPage, matchesQuery.fetchNextPage]);

	const handleStatsEndReached = useCallback(() => {
		if(statsQuery.hasNextPage && !statsQuery.isFetchingNextPage) statsQuery.fetchNextPage();
	}, [statsQuery.hasNextPage, statsQuery.isFetchingNextPage, statsQuery.fetchNextPage]);

	const currentSort = listSubView === 'matches' ? matchSort : statsSort;
	const defaultSort = listSubView === 'matches' ? DEFAULT_MATCH_SORT : DEFAULT_STATS_SORT;
	const isSortActive = currentSort !== defaultSort;

	const currentSortOptions = listSubView === 'matches' ? MATCH_SORT_OPTIONS : STATS_SORT_OPTIONS;
	const currentSortLabel = currentSortOptions.find(option => option.value === currentSort)?.label || 'Ordina';

	const handleSortSelect = (value: string) => {
		if(listSubView === 'matches') setMatchSort(value);
		else setStatsSort(value);
	}

	const refreshControl = (
		<RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/>
	)

	const loadingFooter = (isFetching: boolean) => isFetching ? (
		<View className = 'py-6'>
			<ActivityIndicator color = { colors.primary }/>
		</View>
	) : null;

	const fullScreenLoader = (
		<View className = 'flex-1 items-center justify-center'>
			<ActivityIndicator color = { colors.primary }/>
		</View>
	)

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Partite'/>
			<View className = 'px-4 pt-4'>
				<SegmentedControl options = { VIEW_OPTIONS } selected = { viewMode } onSelect = { value => setViewMode(value as ViewMode) }/>
			</View>
			{viewMode === 'list' && (
				<View className = 'px-4 pt-2'>
					<SegmentedControl options = { LIST_SUB_VIEW_OPTIONS } selected = { listSubView } onSelect = { value => setListSubView(value as ListSubView) }/>
				</View>
			)}
			{viewMode === 'list' && (
				<View className = 'pt-2.5'>
					<ScrollView horizontal showsHorizontalScrollIndicator = { false } contentContainerStyle = {{ paddingHorizontal: 16, gap: 8, alignItems: 'center' }}>
						<Pressable onPress = { () => sortSheetRef.current?.present() } className = { `flex-row items-center gap-1.5 rounded-full px-3 py-1.5 border active:opacity-80 ${isSortActive ? 'bg-primary/15 border-primary/40' : 'bg-secondary/80 border-border/40'}` }>
							<ArrowUpDown size = { 13 } color = { isSortActive ? colors.primary : colors.mutedForeground }/>
							<Text className = { `text-xs font-medium ${isSortActive ? 'text-primary' : 'text-muted-foreground'}` }>
								{currentSortLabel}
							</Text>
						</Pressable>
						{isMatchesList && (
							<Pressable onPress = { () => gameFilterSheetRef.current?.present() } className = { `flex-row items-center gap-1.5 rounded-full px-3 py-1.5 border active:opacity-80 ${gameFilter ? 'bg-primary/15 border-primary/40' : 'bg-secondary/80 border-border/40'}` }>
								<SlidersHorizontal size = { 13 } color = { gameFilter ? colors.primary : colors.mutedForeground }/>
								<Text className = { `text-xs font-medium ${gameFilter ? 'text-primary' : 'text-muted-foreground'}` }>
									{gameFilter ? gameFilter.name : 'Tutti i giochi'}
								</Text>
								{gameFilter && (
									<Pressable hitSlop = { 8 } onPress = { e => { e.stopPropagation(); setGameFilter(null); } }>
										<X size = { 12 } color = { colors.primary }/>
									</Pressable>
								)}
							</Pressable>
						)}
					</ScrollView>
				</View>
			)}
			{isMatchesList && (
				matchesQuery.isLoading ? fullScreenLoader : (
					<FlatList
						data = { matches }
						keyExtractor = { matchKeyExtractor }
						renderItem = { renderMatch }
						contentContainerStyle = { LIST_CONTENT_STYLE }
						ItemSeparatorComponent = { ListSeparator }
						onEndReached = { handleMatchesEndReached }
						onEndReachedThreshold = { 0.4 }
						refreshControl = { refreshControl }
						ListFooterComponent = { loadingFooter(matchesQuery.isFetchingNextPage) }
						ListEmptyComponent = {
							<View className = 'items-center py-20'>
								<Text className = 'text-center text-muted-foreground'>Nessuna partita registrata</Text>
							</View>
						}
					/>
				)
			)}
			{isStatsList && (
				statsQuery.isLoading ? fullScreenLoader : (
					<FlatList
						data = { gameStats }
						keyExtractor = { statsKeyExtractor }
						renderItem = { renderStats }
						contentContainerStyle = { LIST_CONTENT_STYLE }
						ItemSeparatorComponent = { ListSeparator }
						onEndReached = { handleStatsEndReached }
						onEndReachedThreshold = { 0.4 }
						refreshControl = { refreshControl }
						ListFooterComponent = { loadingFooter(statsQuery.isFetchingNextPage) }
						ListEmptyComponent = {
							<View className = 'items-center px-6 py-20'>
								<Text className = 'text-center text-muted-foreground'>Nessuna statistica disponibile. Compaiono dopo aver concluso almeno una partita.</Text>
							</View>
						}
					/>
				)
			)}
			{isCalendar && (
				<ScrollView className = 'flex-1' refreshControl = { refreshControl }>
					<Calendar key = { colorScheme } current = { `${calendarYear}-${String(calendarMonth).padStart(2, '0')}-01` } onMonthChange = { handleMonthChange } onDayPress = { handleDayPress } markingType = 'custom' markedDates = { markedDates } dayComponent = { MatchesCalendarDay as any } theme = { calendarTheme }/>
					<View className = 'pt-2'>
						{selectedDate ? (
							<>
								<Text className = 'mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground px-4'>
									{formatFullDate(`${selectedDate}T00:00:00`)}
								</Text>
								{isLoadingSelectedDay ? (
									<View className = 'items-center py-6'>
										<ActivityIndicator color = { colors.primary }/>
									</View>
								) : selectedDayMatches && selectedDayMatches.content.length > 0 ? (
									<View className = 'px-4 gap-2'>
										{selectedDayMatches.content.map(item => (
											<MatchListItem key = { item.id } match = { item }/>
										))}
									</View>
								) : (
									<Text className = 'py-4 text-center text-sm text-muted-foreground'>
										Nessuna partita in questo giorno
									</Text>
								)}
							</>
						) : (
							<Text className = 'py-4 text-center text-sm text-muted-foreground'>
								Tocca un giorno per vedere le partite
							</Text>
						)}
					</View>
				</ScrollView>
			)}
			<FabMenu
				actions = {[
					{
						label: 'Registra partita',
						icon: <Trophy size = { 18 }/>,
						onPress: () => registerMatchSheetRef.current?.present(),
					},
				]}
			/>
			<RegisterMatchSheet ref = { registerMatchSheetRef }/>
			<GameFilterSheet ref = { gameFilterSheetRef } selectedGameId = { gameFilter?.id ?? null } onSelect = { game => setGameFilter(game ? { id: game.id, name: game.name } : null) }/>
			<SortSheet ref = { sortSheetRef } options = { currentSortOptions } selected = { currentSort } onSelect = { handleSortSelect }/>
		</View>
	)
}

export default MatchesScreen;