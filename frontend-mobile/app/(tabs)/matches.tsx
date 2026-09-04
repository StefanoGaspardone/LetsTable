import { useMemo, useRef, useState } from 'react';
import { View, ActivityIndicator, FlatList } from 'react-native';
import { Calendar } from 'react-native-calendars';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { Trophy } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import MatchListItem from '@/components/common/match-list-item';
import MatchesCalendarDay from '@/components/common/matches-calendar-day';
import FabMenu from '@/components/common/fab-menu';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';

import { listMatches, getCalendarMatch } from '@/api/match';

const VIEW_OPTIONS = [
	{ value: 'calendar', label: 'Calendario' },
	{ value: 'list', label: 'Lista' },
];

const todayDate = new Date();

const MatchesScreen = () => {
	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);

	const [viewMode, setViewMode] = useState<'list' | 'calendar'>('calendar');
	const [calendarYear, setCalendarYear] = useState(todayDate.getFullYear());
	const [calendarMonth, setCalendarMonth] = useState(todayDate.getMonth() + 1);

	const todayDateString = `${todayDate.getFullYear()}-${String(todayDate.getMonth() + 1).padStart(2, '0')}-${String(todayDate.getDate()).padStart(2, '0')}`;
	const [selectedDate, setSelectedDate] = useState<string | null>(todayDateString);

	const { data: listData, isLoading: isLoadingList, fetchNextPage, hasNextPage, isFetchingNextPage } = useInfiniteQuery({
		queryKey: ['matches', 'list'],
		queryFn: ({ pageParam }) => listMatches({ page: pageParam, size: 20, sort: 'playedAt-desc' }),
		initialPageParam: 0,
		getNextPageParam: lastPage => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: viewMode === 'list',
	});

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
			<View className = 'px-4 pt-3'>
				<SegmentedControl options = { VIEW_OPTIONS } selected = { viewMode } onSelect = { value => setViewMode(value as 'list' | 'calendar') }/>
			</View>
			{viewMode === 'list' ? (
				isLoadingList ? (
					<View className = 'flex-1 items-center justify-center'>
						<ActivityIndicator color = '#C45135'/>
					</View>
				) : (
					<FlatList data = { matches } keyExtractor = { item => item.id } renderItem = { ({ item }) => <MatchListItem match = { item }/> } contentContainerStyle = {{ paddingTop: 12, paddingBottom: 100, flexGrow: 1 }} onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); } } onEndReachedThreshold = { 0.4 }
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
				<View className = 'flex-1'>
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
					<View className = 'flex-1 pt-2'>
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
									<FlatList data = { selectedDayMatches.content } keyExtractor = { item => item.id } renderItem = { ({ item }) => <MatchListItem match = { item }/> } contentContainerStyle = {{ paddingBottom: 100 }}/>
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
				</View>
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
		</View>
	)
}

export default MatchesScreen;