import { forwardRef, useCallback, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { View, ActivityIndicator, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetFlatList } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Search, Dices, ChevronLeft, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useGameSearch } from '@/hooks/use-game';
import { useDebounce } from '@/hooks/use-debounce';
import { useCollection } from '@/hooks/use-collection';
import { useRecentGames } from '@/hooks/use-match';
import { useThemeColors } from '@/hooks/use-theme-colors';

export interface PickedGame {
	id: string;
	bggId: number;
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

interface SelectableGame {
	id: string | null;
	bggId: number;
	name: string;
	thumbnailUrl: string | null;
}

const keyExtractor = (item: SelectableGame, index: number) => item.id ?? `${item.bggId}-${index}`;

const GamePickerSheet = forwardRef<GamePickerSheetRef, GamePickerSheetProps>(({ onSelect, onBack }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const [search, setSearch] = useState('');
	const [isOpen, setIsOpen] = useState(false);

	const { colors } = useThemeColors();

	const debouncedSearch = useDebounce(search);
	const isSearching = debouncedSearch.length > 0;

	useImperativeHandle(ref, () => ({
		present: () => {
			setSearch('');
			setIsOpen(true);
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const collectionQuery = useCollection('', undefined, false, isOpen);
	const searchQuery = useGameSearch(isOpen ? debouncedSearch : '');
	const recentGamesQuery = useRecentGames(isOpen);

	const collectionItems = useMemo(() => (collectionQuery.data?.pages.flatMap(page => page.content) ?? []).map(item => item.game), [collectionQuery.data]);
	const searchResults = useMemo(() => (searchQuery.data?.pages.flatMap(page => page.content) ?? []).filter(game => game.isExpansion !== true), [searchQuery.data]);

	const activeQuery = isSearching ? searchQuery : collectionQuery;
	const items = isSearching ? searchResults : collectionItems;

	const handleSelect = useCallback((game: SelectableGame) => {
		if(!game.id) return;

		onSelect({ id: game.id, bggId: game.bggId, name: game.name, thumbnailUrl: game.thumbnailUrl });
		sheetRef.current?.dismiss();
	}, [onSelect]);

	const handleEndReached = () => {
		if(activeQuery.hasNextPage && !activeQuery.isFetchingNextPage) activeQuery.fetchNextPage();
	}

	const renderGameRow = (game: SelectableGame) => (
		<Pressable onPress = { () => handleSelect(game) } className = 'flex-row items-center gap-3 border-b border-border py-2.5 active:bg-card px-4'>
			<View style = {{ width: 44, height: 44 }} className = 'overflow-hidden rounded-xl bg-secondary'>
				{game.thumbnailUrl ? (
					<Image source = {{ uri: game.thumbnailUrl }} recyclingKey = { game.id ?? `${game.bggId}` } style = {{ width: 44, height: 44 }} contentFit = 'cover'/>
				) : (
					<View className = 'h-full w-full items-center justify-center'>
						<Dices size = { 16 } color = { colors.mutedForeground }/>
					</View>
				)}
			</View>
			<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>
				{game.name}
			</Text>
		</Pressable>
	)

	const recentGames = recentGamesQuery.data ?? [];

	const header = !isSearching ? (
		<>
			{recentGames.length > 0 && (
				<>
					<Text className = 'mb-2 text-xs uppercase tracking-wide font-semibold text-muted-foreground px-4'>Giochi recenti</Text>
					{recentGames.map(game => (
						<View key = { game.id ?? game.bggId }>{renderGameRow(game)}</View>
					))}
				</>
			)}
			<Text className = 'mb-2 mt-3 text-xs uppercase tracking-wide font-semibold text-muted-foreground px-4'>La tua collezione</Text>
		</>
	) : null;

	const footer = activeQuery.isFetchingNextPage ? (
		<View className = 'items-center py-3'>
			<ActivityIndicator size = 'small' color = { colors.primary }/>
		</View>
	) : null;

	return (
		<AppBottomSheet ref = { sheetRef } onClose = { () => setIsOpen(false) }>
			<View className = 'flex-1 pt-2'>
				<View className = 'mb-3 flex-row items-center gap-2 px-4'>
					<Pressable onPress = { onBack } hitSlop = { 10 } className = 'h-10 w-10 items-center justify-center rounded-full bg-secondary active:bg-card'>
						<ChevronLeft size = { 24 } color = { colors.mutedForeground }/>
					</Pressable>
					<Text className = 'font-display text-lg text-foreground'>Scegli un gioco</Text>
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
				{activeQuery.isLoading ? (
					<View className = 'items-center py-8 px-4'>
						<ActivityIndicator color = { colors.primary }/>
					</View>
				) : (
					<BottomSheetFlatList
						data = { items }
						keyExtractor = { keyExtractor }
						renderItem = { ({ item }) => renderGameRow(item) }
						ListHeaderComponent = { header }
						ListFooterComponent = { footer }
						onEndReached = { handleEndReached }
						onEndReachedThreshold = { 0.4 }
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
});

GamePickerSheet.displayName = 'GamePickerSheet';

export default GamePickerSheet;