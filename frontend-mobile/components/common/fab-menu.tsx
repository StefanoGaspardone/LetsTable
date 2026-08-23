import { useEffect, useState, cloneElement, isValidElement } from 'react';
import { View, Pressable } from 'react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming, Easing } from 'react-native-reanimated';
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

const FabMenu = ({ actions }: FabMenuProps) => {
	const [isOpen, setIsOpen] = useState(false);

	const rotation = useSharedValue(0);
	const panelOpacity = useSharedValue(0);
	const panelTranslateY = useSharedValue(12);

	useEffect(() => {
		rotation.value = withTiming(isOpen ? 45 : 0, { duration: 200, easing: Easing.out(Easing.cubic) });
		panelOpacity.value = withTiming(isOpen ? 1 : 0, { duration: isOpen ? 180 : 150 });
		panelTranslateY.value = withTiming(isOpen ? 0 : 12, {
			duration: isOpen ? 200 : 150,
			easing: Easing.out(Easing.cubic),
		});
	}, [isOpen]);

	const iconStyle = useAnimatedStyle(() => ({
		transform: [{ rotate: `${rotation.value}deg` }],
	}));

	const panelStyle = useAnimatedStyle(() => ({
		opacity: panelOpacity.value,
		transform: [{ translateY: panelTranslateY.value }],
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
				<Animated.View style = { panelStyle } pointerEvents = { isOpen ? 'auto' : 'none' } className = 'mb-3 items-end gap-3'>
					{actions.map((action, index) => (
                        <Pressable key = { index } onPress = { () => handleActionPress(action) } className = 'flex-row items-center gap-3 rounded-full border border-border bg-card px-4 py-3 shadow-md active:bg-primary/90 active:border-primary/90' style = { ({ pressed }) => [pressed && { backgroundColor: '#C45135', borderColor: '#C45135' }] }>
                            {({ pressed }) => (
                                <>
                                    <Text className = { `text-sm ${pressed ? 'text-white' : 'text-foreground'}` }>
                                        {action.label}
                                    </Text>
                                    {isValidElement(action.icon) && pressed
                                        ? cloneElement(action.icon as React.ReactElement<any>, { color: '#FFFFFF' })
                                        : action.icon}
                                </>
                            )}
                        </Pressable>
                    ))}
				</Animated.View>
				<Pressable onPress = { handleToggle } className = 'h-14 w-14 items-center justify-center rounded-full bg-primary shadow-lg'>
					<Animated.View style = { iconStyle }>
						<Plus size = { 26 } color = '#FFFFFF'/>
					</Animated.View>
				</Pressable>
			</View>
		</View>
    )
}

export default FabMenu;