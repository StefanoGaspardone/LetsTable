import { useState } from 'react';
import { View, FlatList, ActivityIndicator, Pressable } from 'react-native';
import { router } from 'expo-router';
import { Search, List, LayoutGrid } from 'lucide-react-native';

import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import GameListItem from '@/components/common/game-list-item';
import GameGridItem from '@/components/common/game-grid-item';
import { Input } from '@/components/ui/input';
import { Text } from '@/components/ui/text';

import { useDebounce } from '@/hooks/use-debounce';
import { useGameSearch, useHotGames } from '@/hooks/use-game';

const BrowseScreen = () => {
	const [search, setSearch] = useState('');
	const [viewMode, setViewMode] = useState<'list' | 'grid'>('grid');
	const debouncedSearch = useDebounce(search);
	const isSearching = debouncedSearch.length > 0;

	const searchQuery = useGameSearch(debouncedSearch);
	const hotQuery = useHotGames();

	const activeQuery = isSearching ? searchQuery : hotQuery;
	const items = isSearching
		? (searchQuery.data?.pages.flatMap((page) => page.content) ?? [])
		: (hotQuery.data?.pages.flatMap((page) => page.content) ?? []);

	const renderFooter = () => {
		if(activeQuery.isFetchingNextPage) {
			return (
				<View className = 'py-6'>
					<ActivityIndicator/>
				</View>
			)
		}

		if(!activeQuery.hasNextPage && items.length > 0) {
			return (
				<View className = 'py-6'>
					<Text className = 'text-center text-sm text-muted-foreground'>
						{isSearching ? 'Fine dei risultati' : 'Fine della classifica'}
					</Text>
				</View>
			)
		}

		return null;
	}

	const renderEmpty = () => {
		if(activeQuery.isLoading) return null;
		
		return (
			<View className = 'items-center py-20'>
				<Text className = 'text-center text-muted-foreground'>
					{isSearching ? 'Nessun gioco trovato su BGG' : 'Nessun gioco in classifica'}
				</Text>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Sfoglia Giochi' leftElement = { <BackButton/> }/>
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
				{!isSearching && (
					<Text className = 'font-display text-lg text-foreground'>In tendenza su BGG</Text>
				)}
			</View>
			{activeQuery.isLoading ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator/>
				</View>
			) : (
				<FlatList className = 'mt-3' key = { viewMode } data = { items } keyExtractor = { item => `${item.bggId}` } numColumns = { viewMode === 'grid' ? 2 : 1 } columnWrapperStyle = { viewMode === 'grid' ? { paddingHorizontal: 16, gap: 12 } : undefined } contentContainerStyle = {{ paddingBottom: 40, flexGrow: 1 }}
					renderItem = {({ item }) =>
						viewMode === 'list' ? (
							<GameListItem game = { item } onPress = { () => router.push(`/game/${item.bggId}`) } showRank = { !isSearching }/>
						) : (
							<GameGridItem game = { item } onPress = { () => router.push(`/game/${item.bggId}`) } showRank = { !isSearching }/>
						)
					}
					onEndReached = { () => { if(activeQuery.hasNextPage && !activeQuery.isFetchingNextPage) activeQuery.fetchNextPage() } } onEndReachedThreshold = { 0.4 } ListFooterComponent = { renderFooter } ListEmptyComponent = { renderEmpty }
				/>
			)}
		</View>
	)
}

export default BrowseScreen;