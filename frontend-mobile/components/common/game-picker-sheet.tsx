import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, ActivityIndicator, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetFlatList } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Search, Dices, ChevronLeft } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useGameSearch } from '@/hooks/use-game';
import { useDebounce } from '@/hooks/use-debounce';
import { useCollection } from '@/hooks/use-collection';

export interface PickedGame {
	id: string;
	name: string;
	thumbnailUrl: string | null;
}

export interface GamePickerSheetRef {
	present: () => void;
	dismiss: () => void;
}

interface GamePickerSheetProps {
	onSelect: (game: PickedGame) => void;
    onBack: () => void;
}

const GamePickerSheet = forwardRef<GamePickerSheetRef, GamePickerSheetProps>(({ onSelect, onBack }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const [search, setSearch] = useState('');

	const debouncedSearch = useDebounce(search);
	const isSearching = debouncedSearch.length > 0;

	useImperativeHandle(ref, () => ({
		present: () => {
			setSearch('');
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const collectionQuery = useCollection('', undefined, false);
	const searchQuery = useGameSearch(debouncedSearch);

	const collectionItems = (collectionQuery.data?.pages.flatMap(p => p.content) ?? [])
		.map(item => item.game);

	const searchResults = (searchQuery.data?.pages.flatMap((p) => p.content) ?? []).filter(
		game => game.isExpansion !== true
	);

	const items = isSearching ? searchResults : collectionItems;
	const isLoading = isSearching ? searchQuery.isLoading : collectionQuery.isLoading;

	const handleSelect = (game: { id: string | null; name: string; thumbnailUrl: string | null }) => {
		if(!game.id) return;
		
        onSelect({ id: game.id, name: game.name, thumbnailUrl: game.thumbnailUrl });
		sheetRef.current?.dismiss();
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<View className='flex-1 pt-2'>
                <View className = 'mb-3 flex-row items-center gap-2 px-4'>
                    <Pressable onPress = { onBack } hitSlop = { 10 } className = 'h-10 w-10 items-center justify-center rounded-full bg-secondary active:bg-[#DDD8CE]'>
                        <ChevronLeft size = { 24 } color = '#736E65'/>
                    </Pressable>
                    <Text className = 'font-display text-lg text-foreground'>Scegli un gioco</Text>
                </View>
				<View className = 'relative mb-3 px-4'>
					<Input placeholder = 'Cerca...' value = { search } onChangeText = { setSearch } className = 'rounded-2xl bg-secondary pl-10'/>
					<View className = 'pointer-events-none absolute left-3 top-0 h-full justify-center px-4'>
						<Search size = { 18 } className = 'text-muted-foreground'/>
					</View>
				</View>
				{!isSearching && (
					<Text className = 'mb-2 text-xs uppercase tracking-wide font-semibold text-muted-foreground px-4'>La tua collezione</Text>
				)}
				{isLoading ? (
					<View className = 'items-center py-8 px-4'>
						<ActivityIndicator color = '#C45135'/>
					</View>
				) : (
					<BottomSheetFlatList data = { items } keyExtractor = { (item, index) => item.id ?? `${item.bggId}-${index}` }
						renderItem={({ item }) => (
							<Pressable onPress = { () => handleSelect(item) } className = 'flex-row items-center gap-3 border-b border-border py-2.5 active:bg-[#DDD8CE] px-4'>
								<View style = {{ width: 44, height: 44 }} className = 'overflow-hidden rounded-xl bg-secondary'>
									{item.thumbnailUrl ? (
										<Image source = {{ uri: item.thumbnailUrl }} style = {{ width: 44, height: 44 }} contentFit = 'cover'/>
									) : (
										<View className = 'h-full w-full items-center justify-center'>
											<Dices size = { 16 } color = '#736E65'/>
										</View>
									)}
								</View>
								<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>
									{item.name}
								</Text>
							</Pressable>
						)}
						contentContainerStyle = {{ paddingBottom: 24 }}
						ListEmptyComponent = {
							<Text className = 'py-8 text-center text-sm text-muted-foreground'>
								{isSearching ? 'Nessun gioco trovato' : 'La tua collezione è vuota'}
							</Text>
						}
					/>
				)}
			</View>
		</AppBottomSheet>
	)
})

GamePickerSheet.displayName = 'GamePickerSheet';

export default GamePickerSheet;