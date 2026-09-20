import { useMemo, useRef, useState } from 'react';
import { View, ActivityIndicator, FlatList, Pressable } from 'react-native';
import { Calendar } from 'react-native-calendars';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { RefreshControl, ScrollView } from 'react-native-gesture-handler';
import { SlidersHorizontal, Trophy, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import MatchListItem from '@/components/common/match-list-item';
import MatchesCalendarDay from '@/components/common/matches-calendar-day';
import FabMenu from '@/components/common/fab-menu';
import GameFilterSheet, { GameFilterSheetRef } from '@/components/common/game-filter-sheet';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';

import { listMatches, getCalendarMatch } from '@/api/match';

import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';

const VIEW_OPTIONS = [
	{ value: 'calendar', label: 'Calendario' },
	{ value: 'list', label: 'Lista' },
]

const todayDate = new Date();

const MatchesScreen = () => {
	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);
	const gameFilterSheetRef = useRef<GameFilterSheetRef>(null);

	const [viewMode, setViewMode] = useState<'list' | 'calendar'>('calendar');
	const [gameFilter, setGameFilter] = useState<{ id: string; name: string } | null>(null);
	const [calendarYear, setCalendarYear] = useState(todayDate.getFullYear());
	const [calendarMonth, setCalendarMonth] = useState(todayDate.getMonth() + 1);

	const todayDateString = `${todayDate.getFullYear()}-${String(todayDate.getMonth() + 1).padStart(2, '0')}-${String(todayDate.getDate()).padStart(2, '0')}`;
	const [selectedDate, setSelectedDate] = useState<string | null>(todayDateString);

	const { data: listData, isLoading: isLoadingList, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
		queryKey: ['matches', 'list', gameFilter?.id ?? null],
		queryFn: ({ pageParam }) => listMatches({ page: pageParam, size: 20, sort: 'playedAt-desc', gameId: gameFilter?.id }),
		initialPageParam: 0,
		getNextPageParam: lastPage => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: viewMode === 'list',
	});
	useRefetchOnFocus(['matches']);
	
	const { refreshing, onRefresh } = usePullToRefresh([['matches']]);

	const matches = listData?.pages.flatMap(page => page.content) ?? [];

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

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Partite'/>
						<View className = 'flex-row items-center gap-2 px-4 pt-3'>
				<View className = 'flex-1'>
					<SegmentedControl options = { VIEW_OPTIONS } selected = { viewMode } onSelect = { value => setViewMode(value as 'list' | 'calendar') }/>
				</View>
				{viewMode === 'list' && (
					<Pressable onPress = { () => gameFilterSheetRef.current?.present() } className = 'h-11 w-11 items-center justify-center rounded-2xl border border-border active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
						{({ pressed }) =>
							<SlidersHorizontal size = { 19 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
						}
					</Pressable>
				)}
			</View>
			{viewMode === 'list' && gameFilter && (
				<View className = 'flex-row px-4 pt-2'>
					<Pressable onPress = { () => setGameFilter(null) } className = 'flex-row items-center gap-1.5 self-start rounded-full bg-primary/10 px-3 py-1.5'>
						<Text className = 'text-xs font-medium text-primary'>{gameFilter.name}</Text>
						<X size = { 14 } color = '#C45135'/>
					</Pressable>
				</View>
			)}
			{viewMode === 'list' ? (
				isLoadingList ? (
					<View className = 'flex-1 items-center justify-center'>
						<ActivityIndicator color = '#C45135'/>
					</View>
				) : (
					<FlatList data = { matches } keyExtractor = { item => item.id } renderItem = { ({ item }) => <MatchListItem match = { item }/> } contentContainerStyle = {{ paddingHorizontal: 16, paddingTop: 12, paddingBottom: 100, flexGrow: 1 }} ItemSeparatorComponent = { () => <View className = 'h-2'/> } onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); } } onEndReachedThreshold = { 0.4 } refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = '#C45135' colors = { ['#C45135'] } progressBackgroundColor = '#F2EFE9'/> }
						ListFooterComponent = {
							isFetchingNextPage ? (
								<View className = 'py-6'>
									<ActivityIndicator color = '#C45135'/>
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
				<ScrollView className = 'flex-1' refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = '#C45135' colors = { ['#C45135'] } progressBackgroundColor = '#F2EFE9'/> }>
					<Calendar current = { `${calendarYear}-${String(calendarMonth).padStart(2, '0')}-01` } onMonthChange = { handleMonthChange } onDayPress = { day => setSelectedDate(day.dateString) } markingType = 'custom' markedDates = { markedDates } dayComponent = { ({ date, state, marking }: any) => (<MatchesCalendarDay date = { date } state = { state } marking = { marking } onPress = { d => setSelectedDate(d.dateString) }/>) }
						theme = {{
							backgroundColor: '#F2EFE9',
							calendarBackground: '#F2EFE9',
							textSectionTitleColor: '#736E65',
							todayTextColor: '#C45135',
							arrowColor: '#C45135',
							monthTextColor: '#1E1C1A',
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
										<ActivityIndicator color = '#C45135'/>
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
						icon: <Trophy size = { 18 } className = 'text-foreground'/>,
						onPress: () => registerMatchSheetRef.current?.present(),
					},
				] }
			/>
			<RegisterMatchSheet ref = { registerMatchSheetRef }/>
			<GameFilterSheet ref = { gameFilterSheetRef } selectedGameId = { gameFilter?.id ?? null } onSelect = { game => setGameFilter(game ? { id: game.id, name: game.name } : null) }/>
		</View>
	)
}

export default MatchesScreen;