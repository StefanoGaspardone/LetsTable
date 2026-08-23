import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { Check, Plus } from 'lucide-react-native';

import HuePickerModal from '@/components/hue-picker-modal';

const PALETTE = [
	'#B23B3B', '#1F5C4C', '#2E5CB2', '#D8A13B',
	'#7B4B9E', '#E07B39', '#2A2A2A', '#F2EFE6',
]

interface ColorSwatchPickerProps {
	value: string;
	onChange: (color: string) => void;
}

const ColorSwatchPicker = ({ value, onChange }: ColorSwatchPickerProps) => {
	const [isPickerOpen, setIsPickerOpen] = useState(false);
	const isCustomColor = !PALETTE.includes(value);

	return (
		<>
			<View className = 'flex-row flex-wrap items-center gap-2'>
				{PALETTE.map(color => (
					<Pressable key = { color } onPress = { () => onChange(color) } className = 'h-8 w-8 items-center justify-center rounded-full border border-border' style = {{ backgroundColor: color }}>
						{value === color && (
							<Check size = { 16 } color = { color === '#F2EFE6' ? '#201A14' : '#F7F1E4' }/>
						)}
					</Pressable>
				))}
				{isCustomColor && (
					<View className = 'h-8 w-8 items-center justify-center rounded-full border-2 border-foreground' style = {{ backgroundColor: value }}>
						<Check size = { 16 } color = '#F7F1E4'/>
					</View>
				)}
				<Pressable onPress = { () => setIsPickerOpen(true) } className = 'h-8 w-8 items-center justify-center rounded-full border border-dashed border-border'>
					<Plus size = { 16 } className = 'text-muted-foreground'/>
				</Pressable>
			</View>
			<HuePickerModal visible = { isPickerOpen } onClose = { () => setIsPickerOpen(false) }
				onConfirm = { color => {
					onChange(color);
					setIsPickerOpen(false);
				}}
			/>
		</>
	)
}

export default ColorSwatchPicker;