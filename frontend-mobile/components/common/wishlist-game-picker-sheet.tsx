import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, ActivityIndicator, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetFlatList } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Search, Dices, ChevronLeft, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useGameSearch } from '@/hooks/use-game';
import { useDebounce } from '@/hooks/use-debounce';
import { useThemeColors } from '@/hooks/use-theme-colors';

export interface PickedGame {
	id: string;
	bggId: number;
	name: string;
	thumbnailUrl: string | null;
}

export interface WishlistGamePickerSheetRef {
	present: () => void;
	dismiss: () => void;
}

interface WishlistGamePickerSheetProps {
	excludeGameIds: string[];
	onSelect: (game: PickedGame) => void;
	onBack: () => void;
}

const WishlistGamePickerSheet = forwardRef<WishlistGamePickerSheetRef, WishlistGamePickerSheetProps>(({ excludeGameIds, onSelect, onBack }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const [search, setSearch] = useState('');

	const { colors } = useThemeColors();

	const debouncedSearch = useDebounce(search);
	const isSearching = debouncedSearch.length > 0;

	useImperativeHandle(ref, () => ({
		present: () => {
			setSearch('');
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useGameSearch(debouncedSearch);

	const results = (data?.pages.flatMap(p => p.content) ?? []).filter(
		game => !game.id || !excludeGameIds.includes(game.id)
	);

	const handleSelect = (game: { id: string | null; bggId: number; name: string; thumbnailUrl: string | null }) => {
		if(!game.id) return;

		onSelect({ id: game.id, bggId: game.bggId, name: game.name, thumbnailUrl: game.thumbnailUrl });
		sheetRef.current?.dismiss();
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<View className = 'flex-1 pt-2'>
				<View className = 'mb-3 flex-row items-center gap-2 px-4'>
					<Pressable onPress = { onBack } hitSlop = { 10 } className = 'h-10 w-10 items-center justify-center rounded-full bg-secondary active:bg-card'>
						<ChevronLeft size = { 24 } color = { colors.mutedForeground }/>
					</Pressable>
					<Text className = 'font-display text-lg text-foreground'>Cerca un gioco</Text>
				</View>
				<View className = 'relative mb-3 px-4'>
					<Input placeholder = 'Cerca...' value = { search } onChangeText = { setSearch } className = 'rounded-2xl bg-secondary pl-10 pr-10 border-0'/>
					<View className = 'pointer-events-none absolute left-3 top-0 h-full justify-center px-4'>
						<Search size = { 18 } color = { colors.mutedForeground }/>
					</View>
					{search.length > 0 && (
						<Pressable onPress = { () => setSearch('') } hitSlop = { 8 } className = 'absolute right-3 top-0 h-full justify-center px-4'>
							<X size = { 18 } color = { colors.mutedForeground }/>
						</Pressable>
					)}
				</View>
				{isLoading ? (
					<View className = 'items-center py-8 px-4'>
						<ActivityIndicator color = { colors.primary }/>
					</View>
				) : (
					<BottomSheetFlatList data = { results } keyExtractor = { (item, index) => item.id ?? `${item.bggId}-${index}` }
						renderItem = { ({ item }) => (
							<Pressable onPress = { () => handleSelect(item) } className = 'flex-row items-center gap-3 border-b border-border py-2.5 active:bg-card px-4'>
								<View style = {{ width: 44, height: 44 }} className = 'overflow-hidden rounded-xl bg-secondary'>
									{item.thumbnailUrl ? (
										<Image source = {{ uri: item.thumbnailUrl }} style = {{ width: 44, height: 44 }} contentFit = 'cover'/>
									) : (
										<View className = 'h-full w-full items-center justify-center'>
											<Dices size = { 16 } color = { colors.mutedForeground }/>
										</View>
									)}
								</View>
								<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>
									{item.name}
								</Text>
							</Pressable>
						)}
						onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); } } onEndReachedThreshold = { 0.4 }
						contentContainerStyle = {{ paddingBottom: 24 }}
						ListFooterComponent = { isFetchingNextPage ? (<View className = 'items-center py-3'><ActivityIndicator size = 'small' color = { colors.primary }/></View>) : null }
						ListEmptyComponent = {
							<Text className = 'py-8 text-center text-sm text-muted-foreground'>
								{isSearching ? 'Nessun gioco trovato' : 'Digita per cercare un gioco'}
							</Text>
						}
					/>
				)}
			</View>
		</AppBottomSheet>
	)
});

WishlistGamePickerSheet.displayName = 'WishlistGamePickerSheet';

export default WishlistGamePickerSheet;