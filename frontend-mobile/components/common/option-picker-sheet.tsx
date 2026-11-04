import { forwardRef, useImperativeHandle, useMemo, useRef, useState } from 'react';
import { ActivityIndicator, Pressable, View } from 'react-native';
import { BottomSheetModal, BottomSheetScrollView } from '@gorhom/bottom-sheet';
import { Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useThemeColors } from '@/hooks/use-theme-colors';

export interface PickerOption {
	id: string;
	name: string;
}

export interface OptionPickerSheetRef {
	present: () => void;
	dismiss: () => void;
}

interface OptionPickerSheetProps {
	title: string;
	allLabel: string;
	options: PickerOption[];
	selectedId: string | null;
	isLoading?: boolean;
	searchable?: boolean;
	onSelect: (option: PickerOption | null) => void;
}

const OptionPickerSheet = forwardRef<OptionPickerSheetRef, OptionPickerSheetProps>(({ title, allLabel, options, selectedId, isLoading = false, searchable = true, onSelect }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const [search, setSearch] = useState('');

	const { colors } = useThemeColors();

	useImperativeHandle(ref, () => ({
		present: () => {
			setSearch('');
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const filteredOptions = useMemo(() => {
		const normalized = search.trim().toLowerCase();
		if(!normalized) return options;

		return options.filter(option => option.name.toLowerCase().includes(normalized));
	}, [options, search]);

	const handleSelect = (option: PickerOption | null) => {
		onSelect(option);
		sheetRef.current?.dismiss();
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<BottomSheetScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 32 }} keyboardShouldPersistTaps = 'handled'>
				<Text className = 'mb-4 font-display text-xl text-foreground'>{title}</Text>
				{searchable && (
					<Input placeholder = 'Cerca...' value = { search } onChangeText = { setSearch } className = 'mb-4 h-11 rounded-xl border-0'/>
				)}
				<View className = 'gap-2'>
					<Pressable onPress = { () => handleSelect(null) } className = { `flex-row items-center gap-2.5 rounded-xl border px-3 py-3 ${selectedId === null ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
						<Text className = 'flex-1 text-sm font-medium text-foreground'>{allLabel}</Text>
						{selectedId === null && <Check size = { 16 } color = { colors.primary }/>}
					</Pressable>
					{isLoading ? (
						<View className = 'items-center py-4'>
							<ActivityIndicator color = { colors.primary }/>
						</View>
					) : filteredOptions.length > 0 ? filteredOptions.map(option => (
						<Pressable key = { option.id } onPress = { () => handleSelect(option) } className = { `flex-row items-center gap-2.5 rounded-xl border px-3 py-3 ${selectedId === option.id ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
							<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{option.name}</Text>
							{selectedId === option.id && <Check size = { 16 } color = { colors.primary }/>}
						</Pressable>
					)) : (
						<Text className = 'py-4 text-center text-sm text-muted-foreground'>Nessun risultato</Text>
					)}
				</View>
			</BottomSheetScrollView>
		</AppBottomSheet>
	)
});

OptionPickerSheet.displayName = 'OptionPickerSheet';

export default OptionPickerSheet;