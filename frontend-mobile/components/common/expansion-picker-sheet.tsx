import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetFlatList, BottomSheetFooter, BottomSheetFooterProps } from '@gorhom/bottom-sheet';
import { useInfiniteQuery } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Search, Dices, Check, ChevronLeft } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { getGameExpansions } from '@/api/game';
import { useDebounce } from '@/hooks/use-debounce';
import { useThemeColors } from '@/hooks/use-theme-colors';

export interface PickedExpansion {
	id: string;
	bggId: number;
	name: string;
	thumbnailUrl: string | null;
}

export interface ExpansionPickerSheetRef {
	present: (baseGameBggId: number, initiallySelected: PickedExpansion[]) => void;
	dismiss: () => void;
}

interface ExpansionPickerSheetProps {
	onConfirm: (selected: PickedExpansion[]) => void;
	onBack: () => void;
}

const ExpansionPickerSheet = forwardRef<ExpansionPickerSheetRef, ExpansionPickerSheetProps>(({ onConfirm, onBack }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const [baseGameBggId, setBaseGameBggId] = useState<number | null>(null);
	const [search, setSearch] = useState('');
	const [selected, setSelected] = useState<Map<string, PickedExpansion>>(new Map());

	const debouncedSearch = useDebounce(search);

	const { colors } = useThemeColors();

	useImperativeHandle(ref, () => ({
		present: (bggId, initiallySelected) => {
			setBaseGameBggId(bggId);
			setSearch('');
			setSelected(new Map(initiallySelected.map(e => [e.id, e])));
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const { data, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading } = useInfiniteQuery({
		queryKey: ['games', 'expansions', baseGameBggId],
		queryFn: ({ pageParam }) => getGameExpansions(baseGameBggId!, pageParam),
		initialPageParam: 0,
		getNextPageParam: lastPage => (lastPage.last ? undefined : lastPage.number + 1),
		enabled: baseGameBggId !== null,
	});

	const expansions = data?.pages.flatMap(page => page.content) ?? [];

	const filteredExpansions = debouncedSearch
		? expansions.filter(e => e.name.toLowerCase().includes(debouncedSearch.toLowerCase()))
		: expansions;

	const toggleExpansion = (expansion: PickedExpansion) => {
		setSelected(prev => {
			const next = new Map(prev);
			if(next.has(expansion.id)) next.delete(expansion.id);
			else next.set(expansion.id, expansion);
			return next;
		});
	}

	const handleConfirm = () => {
		onConfirm(Array.from(selected.values()));
		sheetRef.current?.dismiss();
	}

	const renderFooter = (props: BottomSheetFooterProps) => (
		<BottomSheetFooter {...props} bottomInset={16}>
			<View className = 'bg-background px-4 pt-2 pb-4'>
				<Button className = 'h-14 rounded-full' onPress = { handleConfirm }>
					<Text className = 'text-base font-semibold text-primary-foreground'>
						Conferma ({selected.size})
					</Text>
				</Button>
			</View>
		</BottomSheetFooter>
	);

	return (
		<AppBottomSheet ref = { sheetRef } footerComponent = { renderFooter }>
			<View className = 'flex-1 pt-2'>
				<View className = 'mb-3 flex-row items-center gap-2 px-4'>
					<Pressable onPress = { onBack } hitSlop = { 10 } className = 'h-10 w-10 items-center justify-center rounded-full bg-secondary active:bg-card'>
						<ChevronLeft size = { 24 } color = { colors.mutedForeground }/>
					</Pressable>
					<Text className = 'font-display text-lg text-foreground'>Espansioni</Text>
				</View>
				<View className = 'relative mb-3 px-4'>
					<Input placeholder = 'Cerca espansione...' value = { search } onChangeText = { setSearch } className = 'h-11 rounded-xl bg-secondary pl-10'/>
					<View className = 'pointer-events-none absolute left-3 top-0 h-11 justify-center px-4'>
						<Search size = { 18 } color = { colors.mutedForeground }/>
					</View>
				</View>
				<BottomSheetFlatList
					data = { filteredExpansions }
					keyExtractor = { item => item.id }
					contentContainerStyle = {{ paddingHorizontal: 16, paddingBottom: 120 }}
					ItemSeparatorComponent = { () => <View className = 'h-2'/> }
					onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); } }
					onEndReachedThreshold = { 0.4 }
					ListEmptyComponent = {
						!isLoading ? (
							<View className = 'items-center py-10'>
								<Text className = 'text-sm text-muted-foreground'>Nessuna espansione trovata</Text>
							</View>
						) : null
					}
					renderItem = { ({ item }) => {
						const isSelected = selected.has(item.id);

						return (
							<Pressable onPress = { () => toggleExpansion({ id: item.id, bggId: item.bggId, name: item.name, thumbnailUrl: item.thumbnailUrl }) } className = { `flex-row items-center gap-3 rounded-xl border p-2 active:opacity-75 ${isSelected ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
								<View style = {{ width: 44, height: 44 }} className = 'overflow-hidden rounded-xl bg-secondary'>
									{item.thumbnailUrl ? (
										<Image source = {{ uri: item.thumbnailUrl }} style = {{ width: 44, height: 44 }} contentFit = 'cover'/>
									) : (
										<View className = 'h-full w-full items-center justify-center'>
											<Dices size = { 16 } color = { colors.mutedForeground }/>
										</View>
									)}
								</View>
								<Text className = 'flex-1 text-sm text-foreground font-medium' numberOfLines = { 1 }>
									{item.name}
								</Text>
								<View className = { `h-6 w-6 items-center justify-center rounded-md border-2 ${isSelected ? 'border-primary bg-primary' : 'border-border'}` }>
									{isSelected && <Check size = { 14 } color = '#FFFFFF' strokeWidth = { 3 }/>}
								</View>
							</Pressable>
						)
					}}
				/>
			</View>
		</AppBottomSheet>
	)
});

ExpansionPickerSheet.displayName = 'ExpansionPickerSheet';

export default ExpansionPickerSheet;