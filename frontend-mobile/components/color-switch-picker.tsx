import { Pressable, View } from 'react-native';
import { Check } from 'lucide-react-native';

const PALETTE = [
	'#B23B3B', '#1F5C4C', '#2E5CB2', '#D8A13B',
	'#7B4B9E', '#E07B39', '#2A2A2A', '#F2EFE6',
]

interface ColorSwatchPickerProps {
	value: string;
	onChange: (color: string) => void;
}

const ColorSwatchPicker = ({ value, onChange }: ColorSwatchPickerProps) => {
	return (
		<View className = 'flex-row flex-wrap gap-2'>
			{PALETTE.map(color => (
				<Pressable key = { color } onPress = { () => onChange(color) } className = 'h-8 w-8 items-center justify-center rounded-full border border-border' style = {{ backgroundColor: color }}>
					{value === color && (
						<Check size = { 16 } color = { color === '#F2EFE6' ? '#201A14' : '#F7F1E4' }/>
					)}
				</Pressable>
			))}
		</View>
	)
}

export default ColorSwatchPicker;