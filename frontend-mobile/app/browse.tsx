import { useState } from 'react';
import { View, FlatList, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { X, Search, List, LayoutGrid } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import GameListItem from '@/components/games/game-list-item';
import GameGridItem from '@/components/games/game-grid-item';

import { useGameSearch, useHotGames } from '@/hooks/use-game';
import { useDebounce } from '@/hooks/use-debounce';

type ViewMode = 'list' | 'grid';

const BrowseScreen = () => {
	const [search, setSearch] = useState('');
	const [viewMode, setViewMode] = useState<ViewMode>('list');

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
			<View className = 'flex-1 items-center justify-center py-20'>
				<Text className = 'text-center text-muted-foreground'>
					{isSearching ? 'Nessun gioco trovato su BGG' : 'Nessun gioco in classifica'}
				</Text>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<View className = 'flex-row items-center gap-2 px-4 pb-3 pt-14'>
				<View className = 'relative flex-1'>
					<Input placeholder = 'Cerca su BoardGameGeek' value = { search } onChangeText = { setSearch } autoFocus className = 'pl-10'/>
					<View className = 'pointer-events-none absolute left-3 top-0 h-full justify-center'>
						<Search size = { 18 } className = 'text-muted-foreground'/>
					</View>
				</View>
				<Button variant = 'outline' size = 'icon' onPress = { () => setViewMode(prev => (prev === 'list' ? 'grid' : 'list')) }>
					{viewMode === 'list' ? (
						<LayoutGrid size = { 20 } className = 'text-foreground'/>
					) : (
						<List size = { 20 } className = 'text-foreground'/>
					)}
				</Button>
				<Button variant = 'ghost' size = 'icon' onPress = { () => router.back()}>
					<X size={22} className = 'text-foreground'/>
				</Button>
			</View>
			{!isSearching && (
				<Text className = 'px-4 pb-2 font-display text-lg text-foreground'>In tendenza su BGG</Text>
			)}
			{activeQuery.isLoading ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator/>
				</View>
			) : (
				<FlatList key = { viewMode } data = { items } keyExtractor = { item => `${item.bggId}` } numColumns = { viewMode === 'grid' ? 2 : 1 } columnWrapperStyle = { viewMode === 'grid' ? { paddingHorizontal: 16, justifyContent: 'space-between' } : undefined }
                    renderItem = { ({ item }) => viewMode === 'list' ? (
                            <GameListItem game = { item } onPress = { () => router.push(`/game/${item.bggId}`) }/>
                        ) : (
                            <GameGridItem game = { item } onPress = { () => router.push(`/game/${item.bggId}`) }/>
                        )
                    }
                    onEndReached = { () => {
                        if(activeQuery.hasNextPage && !activeQuery.isFetchingNextPage) {
                            activeQuery.fetchNextPage();
                        }
                    } }
                    onEndReachedThreshold = { 0.4 } ListFooterComponent = { renderFooter } ListEmptyComponent = { renderEmpty } contentContainerStyle = {{ flexGrow: 1 }}
                />
			)}
		</View>
	)
}

export default BrowseScreen;