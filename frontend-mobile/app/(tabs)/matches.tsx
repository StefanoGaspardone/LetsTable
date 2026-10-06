import { useMemo, useRef, useState } from 'react';
import { View, ActivityIndicator, FlatList, Pressable } from 'react-native';
import { Calendar } from 'react-native-calendars';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { RefreshControl, ScrollView } from 'react-native-gesture-handler';
import { ArrowUpDown, SlidersHorizontal, Trophy, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import MatchListItem from '@/components/common/match-list-item';
import MatchesCalendarDay from '@/components/common/matches-calendar-day';
import FabMenu from '@/components/common/fab-menu';
import GameFilterSheet, { GameFilterSheetRef } from '@/components/common/game-filter-sheet';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';
import SortSheet, { SortSheetRef, SortOption } from '@/components/common/sort-sheet';
import GameStatsListItem from '@/components/common/game-stats-list-item';

import { listMatches, getCalendarMatch, listGameStats } from '@/api/match';

import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

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

const todayDate = new Date();

const MatchesScreen = () => {
	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);
	const gameFilterSheetRef = useRef<GameFilterSheetRef>(null);
	const sortSheetRef = useRef<SortSheetRef>(null);

	const [viewMode, setViewMode] = useState<'list' | 'calendar'>('calendar');
	const [listSubView, setListSubView] = useState<'matches' | 'stats'>('matches');
	const [matchSort, setMatchSort] = useState(DEFAULT_MATCH_SORT);
	const [statsSort, setStatsSort] = useState(DEFAULT_STATS_SORT);
	const [gameFilter, setGameFilter] = useState<{ id: string; name: string } | null>(null);
	const [calendarYear, setCalendarYear] = useState(todayDate.getFullYear());
	const [calendarMonth, setCalendarMonth] = useState(todayDate.getMonth() + 1);

	const todayDateString = `${todayDate.getFullYear()}-${String(todayDate.getMonth() + 1).padStart(2, '0')}-${String(todayDate.getDate()).padStart(2, '0')}`;
	const [selectedDate, setSelectedDate] = useState<string | null>(todayDateString);

	const isMatchesList = viewMode === 'list' && listSubView === 'matches';
	const isStatsList = viewMode === 'list' && listSubView === 'stats';

	const { data: listData, isLoading: isLoadingList, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
		queryKey: ['matches', 'list', gameFilter?.id ?? null, matchSort],
		queryFn: ({ pageParam }) => listMatches({ page: pageParam, size: 20, sort: matchSort, gameId: gameFilter?.id }),
		initialPageParam: 0,
		getNextPageParam: lastPage => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: isMatchesList,
	});

	const { data: statsData, isLoading: isLoadingStats, fetchNextPage: fetchNextStatsPage, hasNextPage: hasNextStatsPage, isFetchingNextPage: isFetchingNextStatsPage } = useInfiniteQuery({
		queryKey: ['matches', 'game-stats', statsSort],
		queryFn: ({ pageParam }) => listGameStats({ page: pageParam, size: 20, sort: statsSort }),
		initialPageParam: 0,
		getNextPageParam: lastPage => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: isStatsList,
	});
	useRefetchOnFocus(['matches']);

	const { refreshing, onRefresh } = usePullToRefresh([['matches']]);
	const { colors, colorScheme } = useThemeColors();

	const matches = listData?.pages.flatMap(page => page.content) ?? [];
	const gameStats = statsData?.pages.flatMap(page => page.content) ?? [];

	const { data: dayCounts } = useQuery({
		queryKey: ['matches', 'calendar', calendarYear, calendarMonth],
		queryFn: () => getCalendarMatch(calendarYear, calendarMonth),
		enabled: viewMode === 'calendar',
	});

	const { data: selectedDayMatches, isLoading: isLoadingSelectedDay } = useQuery({
		queryKey: ['matches', 'day', selectedDate],
		queryFn: () => listMatches({ page: 0, size: 50, fromDate: selectedDate!, toDate: selectedDate!, sort: 'playedAt-desc' }),
		enabled: !!selectedDate,
	});

	const markedDates = useMemo(() => {
		const map: Record<string, { count?: number; selected?: boolean }> = {};
		dayCounts?.forEach(d => {
			map[d.date] = { count: d.count };
		});

		if(selectedDate) map[selectedDate] = { ...map[selectedDate], selected: true };
		return map;
	}, [dayCounts, selectedDate]);

	const handleMonthChange = (month: { year: number; month: number }) => {
		setCalendarYear(month.year);
		setCalendarMonth(month.month);
	}

	const currentSort = listSubView === 'matches' ? matchSort : statsSort;
	const defaultSort = listSubView === 'matches' ? DEFAULT_MATCH_SORT : DEFAULT_STATS_SORT;
	const isSortActive = currentSort !== defaultSort;

	const handleSortSelect = (value: string) => {
		if(listSubView === 'matches') setMatchSort(value);
		else setStatsSort(value);
	}

	const refreshControl = (
		<RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/>
	);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Partite'/>
			<View className = 'flex-row items-center gap-2 px-4 pt-4'>
				<View className = 'flex-1'>
					<SegmentedControl options = { VIEW_OPTIONS } selected = { viewMode } onSelect = { value => setViewMode(value as 'list' | 'calendar') }/>
				</View>
				{viewMode === 'list' && (
					<Pressable onPress = { () => sortSheetRef.current?.present() } className = { `h-11 w-11 items-center justify-center rounded-2xl border-0 active:bg-primary/90 ${isSortActive ? 'bg-primary/10' : 'bg-secondary/80'}` } style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
						{({ pressed }) =>
							<ArrowUpDown size = { 19 } color = { pressed ? '#FFFFFF' : isSortActive ? colors.primary : colors.mutedForeground }/>
						}
					</Pressable>
				)}
				{isMatchesList && (
					<Pressable onPress = { () => gameFilterSheetRef.current?.present() } className = 'h-11 w-11 items-center justify-center rounded-2xl border-0 active:bg-primary/90 bg-secondary/80' style = { ({ pressed }) => [pressed && { backgroundColor: colors.primary, borderColor: colors.primary }] }>
						{({ pressed }) =>
							<SlidersHorizontal size = { 19 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
						}
					</Pressable>
				)}
			</View>
			{viewMode === 'list' && (
				<View className = 'px-4 pt-2'>
					<SegmentedControl options = { LIST_SUB_VIEW_OPTIONS } selected = { listSubView } onSelect = { value => setListSubView(value as 'matches' | 'stats') }/>
				</View>
			)}
			{isMatchesList && gameFilter && (
				<View className = 'flex-row px-4 pt-2'>
					<Pressable onPress = { () => setGameFilter(null) } className = 'flex-row items-center gap-1.5 self-start rounded-full bg-primary/10 px-3 py-1.5'>
						<Text className = 'text-xs font-medium text-primary'>{gameFilter.name}</Text>
						<X size = { 14 } color = { colors.primary }/>
					</Pressable>
				</View>
			)}
			{viewMode === 'list' ? (
				listSubView === 'matches' ? (
					isLoadingList ? (
						<View className = 'flex-1 items-center justify-center'>
							<ActivityIndicator color = { colors.primary }/>
						</View>
					) : (
						<FlatList data = { matches } keyExtractor = { item => item.id } renderItem = { ({ item }) => <MatchListItem match = { item }/> } contentContainerStyle = {{ paddingHorizontal: 16, paddingTop: 12, paddingBottom: 100, flexGrow: 1 }} ItemSeparatorComponent = { () => <View className = 'h-2'/> } onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); } } onEndReachedThreshold = { 0.4 } refreshControl = { refreshControl }
							ListFooterComponent = {
								isFetchingNextPage ? (
									<View className = 'py-6'>
										<ActivityIndicator color = { colors.primary }/>
									</View>
								) : null
							}
							ListEmptyComponent = {
								<View className = 'items-center py-20'>
									<Text className = 'text-center text-muted-foreground'>Nessuna partita registrata</Text>
								</View>
							}
						/>
					)
				) : (
					isLoadingStats ? (
						<View className = 'flex-1 items-center justify-center'>
							<ActivityIndicator color = { colors.primary }/>
						</View>
					) : (
						<FlatList data = { gameStats } keyExtractor = { item => item.gameId } renderItem = { ({ item }) => <GameStatsListItem stats = { item }/> } contentContainerStyle = {{ paddingHorizontal: 16, paddingTop: 12, paddingBottom: 100, flexGrow: 1 }} ItemSeparatorComponent = { () => <View className = 'h-2'/> } onEndReached = { () => { if(hasNextStatsPage && !isFetchingNextStatsPage) fetchNextStatsPage(); } } onEndReachedThreshold = { 0.4 } refreshControl = { refreshControl }
							ListFooterComponent = {
								isFetchingNextStatsPage ? (
									<View className = 'py-6'>
										<ActivityIndicator color = { colors.primary }/>
									</View>
								) : null
							}
							ListEmptyComponent = {
								<View className = 'items-center px-6 py-20'>
									<Text className = 'text-center text-muted-foreground'>Nessuna statistica disponibile. Compaiono dopo aver concluso almeno una partita.</Text>
								</View>
							}
						/>
					)
				)
			) : (
				<ScrollView className = 'flex-1' refreshControl = { refreshControl }>
					<Calendar key = { colorScheme } current = { `${calendarYear}-${String(calendarMonth).padStart(2, '0')}-01` } onMonthChange = { handleMonthChange } onDayPress = { day => setSelectedDate(day.dateString) } markingType = 'custom' markedDates = { markedDates } dayComponent = { ({ date, state, marking }: any) => (<MatchesCalendarDay date = { date } state = { state } marking = { marking } onPress = { d => setSelectedDate(d.dateString) }/>) }
						theme = {{
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
						}}
					/>
					<View className = 'pt-2'>
						{selectedDate ? (
							<>
								<Text className = 'mb-2 text-xs font-semibold uppercase tracking-wide text-muted-foreground px-4'>
									{new Date(selectedDate + 'T00:00:00').toLocaleDateString('it-IT', {
										day: 'numeric',
										month: 'long',
										year: 'numeric',
									})}
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
				actions = { [
					{
						label: 'Registra partita',
						icon: <Trophy size = { 18 }/>,
						onPress: () => registerMatchSheetRef.current?.present(),
					},
				] }
			/>
			<RegisterMatchSheet ref = { registerMatchSheetRef }/>
			<GameFilterSheet ref = { gameFilterSheetRef } selectedGameId = { gameFilter?.id ?? null } onSelect = { game => setGameFilter(game ? { id: game.id, name: game.name } : null) }/>
			<SortSheet ref = { sortSheetRef } options = { listSubView === 'matches' ? MATCH_SORT_OPTIONS : STATS_SORT_OPTIONS } selected = { currentSort } onSelect = { handleSortSelect }/>
		</View>
	)
}

export default MatchesScreen;