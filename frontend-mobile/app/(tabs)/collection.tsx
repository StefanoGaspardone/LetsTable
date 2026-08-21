import { useState } from 'react';
import { View, FlatList, ActivityIndicator } from 'react-native';
import { router } from 'expo-router';
import { List, LayoutGrid, Search, Plus } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import GameListItem from '@/components/games/game-list-item';
import GameGridItem from '@/components/games/game-grid-item';

import { useCollection } from '@/hooks/use-collection';
import { useDebounce } from '@/hooks/use-debounce';

type ViewMode = 'list' | 'grid';

const CollectionScreen = () => {
	const [search, setSearch] = useState('');
	const [viewMode, setViewMode] = useState<ViewMode>('list');

	const debouncedSearch = useDebounce(search);

	const { data, fetchNextPage, hasNextPage,isFetchingNextPage, isLoading } = useCollection(debouncedSearch);

	const items = data?.pages.flatMap((page) => page.content) ?? [];

	const renderFooter = () => {
		if(isFetchingNextPage) {
			return (
				<View className = 'py-6'>
					<ActivityIndicator/>
				</View>
			)
		}

		if(!hasNextPage && items.length > 0) {
			return (
				<View className = 'py-6'>
					<Text className = 'text-center text-sm text-muted-foreground'>
						Hai visto tutta la tua collezione
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
					{debouncedSearch ? 'Nessun gioco trovato' : 'La tua collezione è vuota'}
				</Text>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<View className = 'flex-row items-center gap-2 px-4 pb-3 pt-14'>
				<View className = 'relative flex-1'>
					<Input placeholder = 'Cerca nella tua collezione' value = { search } onChangeText = { setSearch } className='pl-10'/>
					<View className='pointer-events-none absolute left-3 top-0 h-full justify-center'>
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
			</View>
			{isLoading ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator/>
				</View>
			) : (
				<FlatList key = { viewMode } data = { items } keyExtractor = { item => item.id } numColumns = { viewMode === 'grid' ? 2 : 1 } columnWrapperStyle = { viewMode === 'grid' ? { paddingHorizontal: 16, justifyContent: 'space-between' } : undefined } renderItem = { ({ item }) =>
					viewMode === 'list' ? (
						<GameListItem game = { item.game } onPress = { () => router.push(`/game/${item.game.bggId}`) }/>
					) : (
						<GameGridItem game = { item.game } onPress = { () => router.push(`/game/${item.game.bggId}`) }/>
					)
				} onEndReached = { () => {
					if(hasNextPage && !isFetchingNextPage) {
						fetchNextPage();
					}
				} } onEndReachedThreshold = { 0.4 } ListFooterComponent = { renderFooter } ListEmptyComponent = { renderEmpty } contentContainerStyle = {{ flexGrow: 1 }}/>
			)}
			<Button size = 'icon' onPress = { () => router.push('/browse') } className = 'absolute bottom-6 right-6 h-14 w-14 rounded-full bg-accent shadow-lg'>
				<Plus size = { 26 } className = 'text-accent-foreground'/>
			</Button>
		</View>
	)
}

export default CollectionScreen;