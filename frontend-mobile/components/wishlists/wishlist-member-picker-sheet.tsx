import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable, TextInput } from 'react-native';
import { BottomSheetModal, BottomSheetFlatList } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Search, ChevronLeft, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useDebounce } from '@/hooks/use-debounce';
import { useUserSearch } from '@/hooks/use-user';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { getAvatarUrl } from '@/lib/file';

export interface WishlistMemberPickerSheetRef {
	present: () => void;
	dismiss: () => void;
}

interface WishlistMemberPickerSheetProps {
	excludeUserIds: string[];
	onConfirm: (userIds: string[]) => void;
	onBack: () => void;
}

const WishlistMemberPickerSheet = forwardRef<WishlistMemberPickerSheetRef, WishlistMemberPickerSheetProps>(({ excludeUserIds, onConfirm, onBack }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const [search, setSearch] = useState('');
	const [selectedIds, setSelectedIds] = useState<string[]>([]);

	const { colors } = useThemeColors();

	const debouncedSearch = useDebounce(search);

	useImperativeHandle(ref, () => ({
		present: () => {
			setSearch('');
			setSelectedIds([]);
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const { data: users, isLoading } = useUserSearch(debouncedSearch);

	const visibleUsers = (users ?? []).filter(u => !excludeUserIds.includes(u.id));

	const toggleUser = (userId: string) => {
		setSelectedIds(prev => (prev.includes(userId) ? prev.filter(id => id !== userId) : [...prev, userId]));
	}

	const handleConfirm = () => {
		onConfirm(selectedIds);
		sheetRef.current?.dismiss();
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<View className = 'px-4 pt-2'>
				<View className = 'mb-3 flex-row items-center gap-2'>
					<Pressable onPress = { onBack } hitSlop = { 10 } className = 'h-10 w-10 items-center justify-center rounded-full bg-secondary active:bg-card'>
						<ChevronLeft size = { 24 } color = { colors.mutedForeground }/>
					</Pressable>
					<Text className = 'font-display text-lg text-foreground'>Invita membri</Text>
				</View>
				<View className = 'relative mb-3'>
					<TextInput value = { search } onChangeText = { setSearch } placeholder = 'Cerca per nome utente...' autoCapitalize = 'none' className = 'h-11 rounded-2xl bg-secondary pl-10 pr-3 text-sm text-foreground'/>
					<View className = 'pointer-events-none absolute left-3 top-0 h-full justify-center'>
						<Search size = { 16 } color = { colors.mutedForeground }/>
					</View>
					{search.length > 0 && (
						<Pressable onPress = { () => setSearch('') } hitSlop = { 8 } className = 'absolute right-3 top-0 h-full justify-center'>
							<X size = { 18 } className = 'text-muted-foreground'/>
						</Pressable>
					)}
				</View>
				<BottomSheetFlatList data = { visibleUsers } keyExtractor = { item => item.id } numColumns = { 4 }
					renderItem = { ({ item }) => {
						const isSelected = selectedIds.includes(item.id);

						return (
							<View style = {{ width: '25%', padding: 4 }}>
								<Pressable onPress = { () => toggleUser(item.id) } className = { `items-center gap-1.5 rounded-2xl border py-3 active:scale-[0.98] active:opacity-75 ${isSelected ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
									<Image source = {{ uri: getAvatarUrl(item.avatarId ?? null, item.username ?? '') }} style = {{ width: 56, height: 56, borderRadius: 100 }} contentFit = 'cover'/>
									<Text className = 'text-center text-sm font-medium text-foreground' numberOfLines = { 1 }>
										{item.username}
									</Text>
								</Pressable>
							</View>
						)
					}}
					contentContainerStyle = {{ paddingBottom: 12 }}
					ListEmptyComponent = { !isLoading && debouncedSearch.length > 0 ? (<Text className = 'py-4 text-center text-sm text-muted-foreground'>Nessun utente trovato</Text>) : null }
				/>
				<Button className = 'mb-4 mt-2 h-12 rounded-full' onPress = { handleConfirm } disabled = { selectedIds.length === 0 }>
					<Text className = 'text-sm font-semibold text-primary-foreground'>
						Invita {selectedIds.length > 0 ? `(${selectedIds.length})` : ''}
					</Text>
				</Button>
			</View>
		</AppBottomSheet>
	)
});

WishlistMemberPickerSheet.displayName = 'WishlistMemberPickerSheet';

export default WishlistMemberPickerSheet;