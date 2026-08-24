import { useState } from 'react';
import { View, Pressable, LayoutChangeEvent } from 'react-native';
import Animated, { useAnimatedStyle, withTiming, Easing } from 'react-native-reanimated';

import { Text } from '@/components/ui/text';

export interface SegmentOption {
	value: string;
	label: string;
}

interface SegmentedControlProps {
	options: SegmentOption[];
	selected: string;
	onSelect: (value: string) => void;
}

const CONTAINER_PADDING = 4;
const PILL_INSET = 1;

const SegmentedControl = ({ options, selected, onSelect }: SegmentedControlProps) => {
	const [innerWidth, setInnerWidth] = useState(0);
	const selectedIndex = options.findIndex((option) => option.value === selected);
	const segmentWidth = innerWidth / options.length;

	const onLayout = (event: LayoutChangeEvent) => {
		setInnerWidth(event.nativeEvent.layout.width - CONTAINER_PADDING * 2);
	}

	const pillStyle = useAnimatedStyle(() => ({
		transform: [
			{ translateX: withTiming(selectedIndex * segmentWidth, { duration: 250, easing: Easing.out(Easing.cubic) }) },
		],
	}));

	return (
		<View className = 'flex-row rounded-full bg-secondary p-1' onLayout={onLayout}>
			{innerWidth > 0 && (
				<Animated.View pointerEvents = 'none' style = { [{ position: 'absolute', top: CONTAINER_PADDING + PILL_INSET, bottom: CONTAINER_PADDING + PILL_INSET, left: CONTAINER_PADDING + PILL_INSET, width: segmentWidth - PILL_INSET * 2, borderRadius: 999 }, pillStyle ] } className = 'bg-background shadow-sm'/>
			)}
			{options.map(option => {
				const isSelected = option.value === selected;

				return (
					<Pressable key = { option.value } onPress = { () => onSelect(option.value) } className = 'flex-1 items-center py-2'>
						<Text className = { `text-sm font-medium ${isSelected ? 'text-foreground' : 'text-muted-foreground'}` }>
							{option.label}
						</Text>
					</Pressable>
				)
			})}
		</View>
	)
}

export default SegmentedControl;