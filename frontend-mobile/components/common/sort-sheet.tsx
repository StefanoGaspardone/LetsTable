import { forwardRef, useImperativeHandle, useRef } from 'react';
import { View, Pressable } from 'react-native';
import { BottomSheetModal } from '@gorhom/bottom-sheet';
import { Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useThemeColors } from '@/hooks/use-theme-colors';

export interface SortOption {
	value: string;
	label: string;
}

export interface SortSheetRef {
	present: () => void;
	dismiss: () => void;
}

interface SortSheetProps {
	title?: string;
	options: SortOption[];
	selected: string;
	onSelect: (value: string) => void;
}

const SortSheet = forwardRef<SortSheetRef, SortSheetProps>(({ title = 'Ordina per', options, selected, onSelect }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);
	const { colors } = useThemeColors();

	useImperativeHandle(ref, () => ({
		present: () => sheetRef.current?.present(),
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const handleSelect = (value: string) => {
		onSelect(value);
		sheetRef.current?.dismiss();
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<View className = 'px-4 pb-8 pt-2'>
				<Text className = 'mb-3 font-display text-xl text-foreground'>{title}</Text>
				<View className = 'gap-2'>
					{options.map(option => {
						const isSelected = option.value === selected;

						return (
							<Pressable key = { option.value } onPress = { () => handleSelect(option.value) } className = { `flex-row items-center justify-between rounded-xl border px-4 py-3.5 active:opacity-75 ${isSelected ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
								<Text className = { `text-sm ${isSelected ? 'font-semibold text-primary' : 'font-medium text-foreground'}` }>{option.label}</Text>
								{isSelected && <Check size = { 18 } color = { colors.primary }/>}
							</Pressable>
						)
					})}
				</View>
			</View>
		</AppBottomSheet>
	)
});

SortSheet.displayName = 'SortSheet';

export default SortSheet;