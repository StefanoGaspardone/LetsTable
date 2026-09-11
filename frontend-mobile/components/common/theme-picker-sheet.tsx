import { forwardRef, useImperativeHandle, useRef } from 'react';
import { View, Pressable } from 'react-native';
import { BottomSheetModal } from '@gorhom/bottom-sheet';
import { Sun, Moon, Smartphone, Check } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { useTheme, ThemePreference } from '@/contexts/theme-context';

export interface ThemePickerSheetRef {
	present: () => void;
	dismiss: () => void;
}

const OPTIONS: { value: ThemePreference; label: string; icon: any }[] = [
	{ value: 'light', label: 'Chiaro', icon: Sun },
	{ value: 'dark', label: 'Scuro', icon: Moon },
	{ value: 'system', label: 'Sistema', icon: Smartphone },
]

const ThemePickerSheet = forwardRef<ThemePickerSheetRef>((_, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);
	const { themePreference, setThemePreference } = useTheme();

	useImperativeHandle(ref, () => ({
		present: () => sheetRef.current?.present(),
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const handleSelect = (value: ThemePreference) => {
		setThemePreference(value);
		sheetRef.current?.dismiss();
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<View className = 'px-4 pb-6 pt-2'>
				<Text className = 'mb-3 font-display text-lg text-foreground'>Tema</Text>
				<View className = 'gap-2'>
					{OPTIONS.map(option => {
						const isSelected = themePreference === option.value;
						const Icon = option.icon;

						return (
							<Pressable key = { option.value } onPress = { () => handleSelect(option.value) } className = { `flex-row items-center gap-3 rounded-xl border p-3.5 active:opacity-75 ${isSelected ? 'border-[#C45135] bg-[#C45135]/5' : 'border-border bg-card'}` }>
								<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-[#C45135]/15'>
									<Icon size = { 17 } color = '#C45135'/>
								</View>
								<Text className = 'flex-1 text-sm font-medium text-foreground'>{option.label}</Text>
								{isSelected && <Check size = { 18 } color = '#C45135' strokeWidth = { 3 }/>}
							</Pressable>
						)
					})}
				</View>
			</View>
		</AppBottomSheet>
	)
});

ThemePickerSheet.displayName = 'ThemePickerSheet';

export default ThemePickerSheet;