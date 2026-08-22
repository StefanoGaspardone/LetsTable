import { useState } from 'react';
import { View, FlatList, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { List, CalendarDays, Plus } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import MatchListItem from '@/components/match-list-item';
import MatchesCalendarView from '@/components/matches-calendar-view';

import { useMatches } from '@/hooks/use-match';

type ViewMode = 'calendar' | 'list';

const MatchesScreen = () => {
	const [viewMode, setViewMode] = useState<ViewMode>('calendar');
	const [selectedDay, setSelectedDay] = useState<string | null>(null);

	const filters = selectedDay
		? { fromDate: selectedDay, toDate: selectedDay, sort: 'playedAt-desc' }
		: { sort: 'playedAt-desc' };

	const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useMatches(filters);

	const matches = data?.pages.flatMap((page) => page.content) ?? [];

	const handleSelectDay = (date: string) => {
		setSelectedDay(date);
		setViewMode('list');
	}

	const handleToggleView = () => {
		if(viewMode === 'calendar') {
			setSelectedDay(null);
			setViewMode('list');
		} else {
			setViewMode('calendar');
		}
	}

	const renderFooter = () => {
		if(isFetchingNextPage) {
			return (
				<View className = 'py-6'>
					<ActivityIndicator/>
				</View>
			)
		}

		if(!hasNextPage && matches.length > 0) {
			return (
				<View className = 'py-6'>
					<Text className = 'text-center text-sm text-muted-foreground'>
						Hai visto tutte le partite
					</Text>
				</View>
			)
		}

		return null;
	}

	const renderEmpty = () => {
		if(isLoading) return null;
		
		return (
			<View className = 'flex-1 items-center justify-center py-20'>
				<Text className = 'text-center text-muted-foreground'>
					{selectedDay ? 'Nessuna partita in questo giorno' : 'Nessuna partita registrata'}
				</Text>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<View className = 'flex-row items-center justify-between px-4 pb-3 pt-14'>
				<Text className = 'font-display text-2xl text-foreground'>Partite</Text>
				<Button variant = 'outline' size = 'icon' onPress = { handleToggleView }>
					{viewMode === 'calendar' ? (
						<List size = { 20 } className = 'text-foreground'/>
					) : (
						<CalendarDays size = { 20 } className = 'text-foreground'/>
					)}
				</Button>
			</View>
			{viewMode === 'calendar' ? (
				<MatchesCalendarView onSelectDay = { handleSelectDay }/>
			) : (
				<>
					{selectedDay && (
						<View className = 'flex-row items-center justify-between px-4 pb-2'>
							<Text className = 'text-sm text-muted-foreground'>
								Partite del {new Date(selectedDay).toLocaleDateString('it-IT', {
									day: 'numeric',
									month: 'long',
									year: 'numeric',
								})}
							</Text>
							<Text className = 'text-sm text-primary' onPress = { () => setSelectedDay(null) }>
								Mostra tutte
							</Text>
						</View>
					)}
					{isLoading ? (
						<View className = 'flex-1 items-center justify-center'>
							<ActivityIndicator/>
						</View>
					) : (
						<FlatList data = { matches } keyExtractor = { item => item.id }
							renderItem = { ({ item }) => (
								<MatchListItem match = { item } onPress = { () => router.push(`/match/${item.id}`) }/>
							)}
							onEndReached = { () => {
								if(hasNextPage && !isFetchingNextPage) fetchNextPage();
							}}
							onEndReachedThreshold = { 0.4 } ListFooterComponent = { renderFooter } ListEmptyComponent = { renderEmpty } contentContainerStyle = {{ flexGrow: 1 }}
						/>
					)}
				</>
			)}
			<Button size = 'icon' onPress = { () => router.push('/match/new') } className = 'absolute bottom-6 right-6 h-14 w-14 rounded-full bg-accent shadow-lg'>
				<Plus size = { 26 } className = 'text-accent-foreground'/>
			</Button>
		</View>
	)
}

export default MatchesScreen;