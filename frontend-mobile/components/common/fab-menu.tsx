import { useEffect, useState, cloneElement, isValidElement } from 'react';
import { View, Pressable } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming, withDelay, Easing } from 'react-native-reanimated';
import { Plus } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

export interface FabMenuAction {
	label: string;
	icon: React.ReactNode;
	onPress: () => void;
}

interface FabMenuProps {
	actions: FabMenuAction[];
}

const STAGGER_DELAY_MS = 40;

const FabMenuItem = ({ action, index, isOpen, onPress }: { action: FabMenuAction; index: number; isOpen: boolean; onPress: () => void }) => {
	const opacity = useSharedValue(0);
	const translateY = useSharedValue(16);
	const scale = useSharedValue(0.85);

	useEffect(() => {
		const openDelay = index * STAGGER_DELAY_MS;

		if(isOpen) {
			opacity.value = withDelay(openDelay, withTiming(1, { duration: 200, easing: Easing.out(Easing.cubic) }));
			translateY.value = withDelay(openDelay, withTiming(0, { duration: 220, easing: Easing.out(Easing.back(1.2)) }));
			scale.value = withDelay(openDelay, withTiming(1, { duration: 220, easing: Easing.out(Easing.back(1.2)) }));
		} else {
			opacity.value = withTiming(0, { duration: 120 });
			translateY.value = withTiming(16, { duration: 120, easing: Easing.in(Easing.cubic) });
			scale.value = withTiming(0.85, { duration: 120, easing: Easing.in(Easing.cubic) });
		}
	}, [isOpen]);

	const itemStyle = useAnimatedStyle(() => ({
		opacity: opacity.value,
		transform: [{ translateY: translateY.value }, { scale: scale.value }],
	}));

	return (
		<Animated.View style = { itemStyle }>
			<Pressable onPress = { onPress } className = 'flex-row items-center gap-3 rounded-full border border-border bg-card px-4 py-3 shadow-md active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
				{({ pressed }) => (
					<>
						<Text className = { `text-sm font-semibold ${pressed ? 'text-white' : 'text-foreground'}` }>
							{action.label}
						</Text>
						{isValidElement(action.icon)
							? cloneElement(action.icon as React.ReactElement<any>, {
									color: pressed ? '#FFFFFF' : (action.icon as React.ReactElement<any>).props.color,
									strokeWidth: 2,
								})
							: action.icon}
					</>
				)}
			</Pressable>
		</Animated.View>
	)
}

const FabMenu = ({ actions }: FabMenuProps) => {
	const [isOpen, setIsOpen] = useState(false);
	const rotation = useSharedValue(0);

	useEffect(() => {
		rotation.value = withTiming(isOpen ? 45 : 0, { duration: 200, easing: Easing.out(Easing.cubic) });
	}, [isOpen]);

	const iconStyle = useAnimatedStyle(() => ({
		transform: [{ rotate: `${rotation.value}deg` }],
	}));

	const handleToggle = () => setIsOpen(prev => !prev);

	const handleActionPress = (action: FabMenuAction) => {
		setIsOpen(false);
		action.onPress();
	}

	return (
		<View pointerEvents = 'box-none' style = {{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}>
			{isOpen && (
				<Pressable onPress = { () => setIsOpen(false) } style = {{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }} className = 'bg-black/20'/>
			)}
			<View className = 'absolute bottom-6 right-6 items-end' pointerEvents = 'box-none'>
				<View pointerEvents = { isOpen ? 'auto' : 'none' } className = 'mb-3 items-end gap-3'>
					{[...actions].reverse().map((action, reversedIndex) => {
						const index = actions.length - 1 - reversedIndex;
						
						return (
							<FabMenuItem key = { index } action = { action } index = { index } isOpen = { isOpen } onPress = { () => handleActionPress(action) }/>
						)
					})}
				</View>
				<Pressable onPress = { handleToggle } className = 'h-14 w-14 items-center justify-center rounded-full bg-primary shadow-lg active:bg-primary/90'>
					<Animated.View style = { iconStyle }>
						<Plus size = { 26 } color = '#FFFFFF'/>
					</Animated.View>
				</Pressable>
			</View>
		</View>
    )
}

export default FabMenu;