import { useState, useRef } from 'react';
import { View, Text, TouchableOpacity, LayoutChangeEvent, Dimensions, ScrollView } from 'react-native';
import Animated, { useAnimatedStyle, withTiming, Easing } from 'react-native-reanimated';

export interface SegmentOption {
	value: string;
	label: string;
	badge?: number;
}

interface SegmentedControlProps {
	options: SegmentOption[];
	selected: string;
	onSelect: (value: string) => void;
}

const CONTAINER_PADDING = 4;
const PILL_INSET = 1;
const SCROLLABLE_THRESHOLD = 3;
const SCREEN_WIDTH = Dimensions.get('window').width;
const SCROLLABLE_TAB_WIDTH = (SCREEN_WIDTH - CONTAINER_PADDING * 2 - 32) / 3.6;

const NonScrollableSegmentedControl = ({ options, selected, onSelect }: SegmentedControlProps) => {
	const [innerWidth, setInnerWidth] = useState(0);
	const selectedIndex = options.findIndex((option) => option.value === selected);
	const segmentWidth = innerWidth / options.length;

	const onLayout = (event: LayoutChangeEvent) => {
		setInnerWidth(event.nativeEvent.layout.width - CONTAINER_PADDING * 2);
	}

	const pillStyle = useAnimatedStyle(() => ({
		transform: [
		{
			translateX: withTiming(innerWidth > 0 ? selectedIndex * segmentWidth : 0, {
				duration: 250,
				easing: Easing.out(Easing.cubic),
			}),
		},
		],
	}));

	return (
		<View className = 'flex-row rounded-full bg-secondary p-1' onLayout = { onLayout }>
			{innerWidth > 0 && (
				<Animated.View pointerEvents = 'none'
					style = { [
						{
							position: 'absolute',
							top: CONTAINER_PADDING + PILL_INSET,
							bottom: CONTAINER_PADDING + PILL_INSET,
							left: CONTAINER_PADDING + PILL_INSET,
							width: segmentWidth - PILL_INSET * 2,
							borderRadius: 9999,
						},
						pillStyle,
					] } className = 'bg-background shadow-sm'
				/>
			)}
			{options.map(option => {
				const isSelected = option.value === selected;

				return (
					<TouchableOpacity key = {option.value } activeOpacity = { 0.7 } onPress = { () => onSelect(option.value) } style = {{ flex: 1, flexDirection: 'row', alignItems: 'center', justifyContent: 'center', gap: 6, paddingVertical: 8 }}>
						<Text className = { `text-sm font-medium ${isSelected ? 'text-foreground' : 'text-muted-foreground'}` }>
							{option.label}
						</Text>
						{!!option.badge && option.badge > 0 && (
							<View className = 'h-5 min-w-5 items-center justify-center rounded-full bg-[#C45135] px-1'>
								<Text className = 'text-xs font-bold text-white'>{option.badge > 99 ? '99+' : option.badge}</Text>
							</View>
						)}
					</TouchableOpacity>
				)
			})}
		</View>
	)
}

const ScrollableSegmentedControl = ({ options, selected, onSelect }: SegmentedControlProps) => {
	const scrollRef = useRef<ScrollView>(null);
	const [viewportWidth, setViewportWidth] = useState(0);
	const selectedIndex = options.findIndex((option) => option.value === selected);
	const GAP = 4;

	const pillStyle = useAnimatedStyle(() => {
		const targetX = selectedIndex >= 0 ? selectedIndex * (SCROLLABLE_TAB_WIDTH + GAP) : 0;
		
		return {
			transform: [
				{
					translateX: withTiming(targetX, {
						duration: 250,
						easing: Easing.out(Easing.cubic),
					}),
				},
			],
		}
	}, [selectedIndex]);

	const scrollToSelected = (index: number) => {
		if(viewportWidth === 0) return;

		const tabStart = index * (SCROLLABLE_TAB_WIDTH + GAP);
		const tabEnd = tabStart + SCROLLABLE_TAB_WIDTH;

		scrollRef.current?.scrollTo({
			x: Math.max(0, tabEnd - viewportWidth + CONTAINER_PADDING * 2),
			animated: true,
		});
	}

	const handleSelect = (value: string, index: number) => {
		onSelect(value);
		scrollToSelected(index);
	}

	return (
		<View className = 'rounded-full bg-secondary p-1' onLayout = { e => setViewportWidth(e.nativeEvent.layout.width) }>
			<ScrollView ref = { scrollRef } horizontal showsHorizontalScrollIndicator = { false } contentContainerStyle = {{ flexDirection: 'row', gap: GAP, position: 'relative' }}>
				<Animated.View pointerEvents = 'none'
					style = { [
						{
							position: 'absolute',
							top: 0,
							bottom: 0,
							width: SCROLLABLE_TAB_WIDTH,
							borderRadius: 9999,
						},
						pillStyle,
					] } className = 'bg-background shadow-sm'
				/>
				{options.map((option, index) => {
					const isSelected = option.value === selected;

					return (
						<TouchableOpacity key = { option.value } activeOpacity = { 0.7 } onPress = { () => handleSelect(option.value, index) }
							style = {{
								width: SCROLLABLE_TAB_WIDTH,
								flexDirection: 'row',
								alignItems: 'center',
								justifyContent: 'center',
								gap: 6,
								paddingVertical: 8,
							}}
						>
						<Text numberOfLines = { 1 } className = { `text-sm font-medium ${isSelected ? 'text-foreground' : 'text-muted-foreground'}` }>
							{option.label}
						</Text>
						{!!option.badge && option.badge > 0 && (
							<View className = 'h-5 min-w-5 items-center justify-center rounded-full bg-[#C45135] px-1'>
								<Text className = 'text-xs font-bold text-white'>{option.badge > 99 ? '99+' : option.badge}</Text>
							</View>
						)}
						</TouchableOpacity>
					)
				})}
			</ScrollView>
		</View>
	)
}

const SegmentedControl = (props: SegmentedControlProps) => {
	const isScrollable = props.options.length > SCROLLABLE_THRESHOLD;

	return isScrollable ? <ScrollableSegmentedControl {...props}/> : <NonScrollableSegmentedControl {...props}/>;
}

export default SegmentedControl;