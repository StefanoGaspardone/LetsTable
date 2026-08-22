import { useState } from 'react';
import { View, Modal, FlatList, ActivityIndicator, Pressable } from 'react-native';
import { X, Search, Dices } from 'lucide-react-native';
import { Image } from 'expo-image';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';

import { useGameSearch } from '@/hooks/use-game';
import { useDebounce } from '@/hooks/use-debounce';

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

	const { data, isLoading } = useGameSearch(debouncedSearch);
	const results = data?.pages.flatMap((page) => page.content) ?? [];

	const handleSelect = async (bggId: number) => {
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
				<Text className = {value ? 'text-foreground' : 'text-muted-foreground'}>
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
					{isLoading || isSyncing ? (
						<View className = 'flex-1 items-center justify-center'>
							<ActivityIndicator/>
						</View>
					) : (
						<FlatList data = { results } keyExtractor = { item => `${item.bggId}` }
							renderItem = { ({ item }) => (
								<Pressable onPress = { () => handleSelect(item.bggId) } className = 'border-b border-border px-4 py-3'>
									<Text className = 'text-foreground'>{item.name}</Text>
									<Text className = 'text-sm text-muted-foreground'>{item.yearPublished ?? '-'}</Text>
								</Pressable>
							)}
						/>
					)}
				</View>
			</Modal>
		</>
	)
}

export default GamePickerField;