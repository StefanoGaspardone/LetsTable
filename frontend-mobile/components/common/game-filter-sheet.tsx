import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetScrollView } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Dices, Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { getRecentGames } from '@/api/match';

import { useQuery } from '@tanstack/react-query';

interface FilterGame {
	id: string;
	name: string;
	thumbnailUrl: string | null;
}

export interface GameFilterSheetRef {
	present: () => void;
	dismiss: () => void;
}

interface GameFilterSheetProps {
	selectedGameId: string | null;
	onSelect: (game: FilterGame | null) => void;
}

const GameFilterSheet = forwardRef<GameFilterSheetRef, GameFilterSheetProps>(({ selectedGameId, onSelect }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);
	const [search, setSearch] = useState('');

	useImperativeHandle(ref, () => ({
		present: () => {
			setSearch('');
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const { data: games, isLoading } = useQuery({
		queryKey: ['matches', 'recent-games'],
		queryFn: getRecentGames,
	});

	const filteredGames = (games ?? []).filter(g => g.name.toLowerCase().includes(search.toLowerCase()));

	const handleSelect = (game: FilterGame | null) => {
		onSelect(game);
		sheetRef.current?.dismiss();
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<BottomSheetScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 32 }}>
				<Text className = 'mb-4 font-display text-xl text-foreground'>Filtra per gioco</Text>
				<Input placeholder = 'Cerca...' value = { search } onChangeText = { setSearch } className = 'mb-4 h-11 rounded-xl'/>
				<View className = 'gap-2'>
					<Pressable onPress = { () => handleSelect(null) } className = { `flex-row items-center gap-2.5 rounded-xl border px-3 py-3 ${selectedGameId === null ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
						<Text className = 'flex-1 text-sm font-medium text-foreground'>Tutti i giochi</Text>
						{selectedGameId === null && <Check size = { 16 } color = '#C45135'/>}
					</Pressable>
					{isLoading ? null : filteredGames.map(game => (
						<Pressable key = { game.id } onPress = { () => handleSelect(game) } className = { `flex-row items-center gap-2.5 rounded-xl border px-2.5 py-2 ${selectedGameId === game.id ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
							<View style = {{ width: 36, height: 36 }} className = 'overflow-hidden rounded-lg bg-secondary'>
								{game.thumbnailUrl ? (
									<Image source = {{ uri: game.thumbnailUrl }} style = {{ width: 36, height: 36 }} contentFit = 'cover'/>
								) : (
									<View className = 'h-full w-full items-center justify-center'>
										<Dices size = { 14 } color = '#736E65'/>
									</View>
								)}
							</View>
							<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{game.name}</Text>
							{selectedGameId === game.id && <Check size = { 16 } color = '#C45135'/>}
						</Pressable>
					))}
				</View>
			</BottomSheetScrollView>
		</AppBottomSheet>
	)
});

GameFilterSheet.displayName = 'GameFilterSheet';

export default GameFilterSheet;