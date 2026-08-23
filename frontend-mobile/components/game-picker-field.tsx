import { useState } from 'react';
import { View, Modal, FlatList, ActivityIndicator, Pressable } from 'react-native';
import { X, Search, Dices } from 'lucide-react-native';
import { Image } from 'expo-image';
import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

import { useCollection } from '@/hooks/use-collection';
import { useDebounce } from '@/hooks/use-debounce';
import { useGameSearch } from '@/hooks/use-game';

import { Game } from '@/types/game';

import { getGameByBggId } from '@/api/game';

interface GamePickerFieldProps {
	value: Game | null;
	onChange: (game: Game) => void;
}

const GamePickerField = ({ value, onChange }: GamePickerFieldProps) => {
	const [isOpen, setIsOpen] = useState(false);
	const [search, setSearch] = useState('');
	const [isSyncing, setIsSyncing] = useState(false);

	const debouncedSearch = useDebounce(search);
	const isSearching = debouncedSearch.length > 0;

	const searchQuery = useGameSearch(debouncedSearch);
	const collectionQuery = useCollection('');

	const searchResults = searchQuery.data?.pages.flatMap((page) => page.content) ?? [];
	const collectionResults = collectionQuery.data?.pages.flatMap((page) => page.content) ?? [];

	const isLoading = isSearching ? searchQuery.isLoading : collectionQuery.isLoading;

	const handleSelectBgg = async (bggId: number) => {
		setIsSyncing(true);
		
		try {
			const game = await getGameByBggId(bggId);
			onChange(game);
			
			setIsOpen(false);
			setSearch('');
		} finally {
			setIsSyncing(false);
		}
	}

	const handleSelectFromCollection = async (game: Game) => {
		onChange(game);
		
		setIsOpen(false);
		setSearch('');
	}

	return (
		<>
			<Pressable onPress = { () => setIsOpen(true) } className = 'flex-row items-center gap-3 rounded-lg border border-border bg-card px-4 py-3'>
				{value?.thumbnailUrl ? (
					<Image source = {{ uri: value.thumbnailUrl }} style = {{ width: 36, height: 36, borderRadius: 6 }}/>
				) : (
					<View className = 'h-9 w-9 items-center justify-center rounded-md bg-secondary'>
						<Dices size = { 18 } className = 'text-muted-foreground'/>
					</View>
				)}
				<Text className = { value ? 'text-foreground' : 'text-muted-foreground' }>
					{value?.name ?? 'Seleziona un gioco'}
				</Text>
			</Pressable>
			<Modal visible = { isOpen } animationType = 'slide' onRequestClose = { () => setIsOpen(false) }>
				<View className = 'flex-1 bg-background'>
					<View className = 'flex-row items-center gap-2 px-4 pb-3 pt-14'>
						<View className = 'relative flex-1'>
							<Input placeholder = 'Cerca su BoardGameGeek' value = { search } onChangeText = { setSearch } autoFocus className = 'pl-10'/>
							<View className = 'pointer-events-none absolute left-3 top-0 h-full justify-center'>
								<Search size = { 18 } className = 'text-muted-foreground'/>
							</View>
						</View>
						<Button variant = 'ghost' size = 'icon' onPress = { () => setIsOpen(false) }>
							<X size = { 22 } className = 'text-foreground'/>
						</Button>
					</View>
					{!isSearching && (
						<Text className = 'px-4 pb-2 font-display text-base text-foreground'>
							La tua collezione
						</Text>
					)}
					{isLoading || isSyncing ? (
						<View className = 'flex-1 items-center justify-center'>
							<ActivityIndicator/>
						</View>
					) : isSearching ? (
						<FlatList data = { searchResults } keyExtractor = { item => `${item.bggId}` }
							renderItem = {({ item }) => (
								<Pressable onPress = { () => handleSelectBgg(item.bggId) } className = 'border-b border-border px-4 py-3'>
									<Text className = 'text-foreground'>{item.name}</Text>
									<Text className = 'text-sm text-muted-foreground'>{item.yearPublished ?? '—'}</Text>
								</Pressable>
							)}
							ListEmptyComponent = {
								<Text className = 'px-4 py-6 text-center text-sm text-muted-foreground'>
									Nessun gioco trovato
								</Text>
							}
						/>
					) : (
						<FlatList data = { collectionResults } keyExtractor = { item => item.id }
							renderItem = { ({ item }) => (
								<Pressable onPress = { () => handleSelectFromCollection(item.game) } className = 'flex-row items-center gap-3 border-b border-border px-4 py-3'>
									{item.game.thumbnailUrl ? (
										<Image source = {{ uri: item.game.thumbnailUrl }}
											style = {{ width: 40, height: 40, borderRadius: 6 }}/>
									) : (
										<View className = 'h-10 w-10 items-center justify-center rounded-md bg-secondary'>
											<Dices size = { 16 } className = 'text-muted-foreground'/>
										</View>
									)}
									<Text className = 'text-foreground'>{item.game.name}</Text>
								</Pressable>
							)}
							ListEmptyComponent = {
								<Text className = 'px-4 py-6 text-center text-sm text-muted-foreground'>
									La tua collezione è vuota, cerca un gioco su BGG
								</Text>
							}
						/>
					)}
				</View>
			</Modal>
		</>
	)
} 

export default GamePickerField;