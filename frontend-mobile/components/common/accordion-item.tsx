import { useState } from 'react';
import { View, Pressable, LayoutChangeEvent } from 'react-native';
import { ChevronRight } from 'lucide-react-native';
import Animated, { useAnimatedStyle, useSharedValue, withTiming, Easing } from 'react-native-reanimated';

import { Text } from '@/components/ui/text';

interface AccordionItemProps {
	title: string;
	children: React.ReactNode;
	onFirstOpen?: () => void;
}

const AccordionItem = ({ title, children, onFirstOpen }: AccordionItemProps) => {
	const [isOpen, setIsOpen] = useState(false);
	const [hasOpenedOnce, setHasOpenedOnce] = useState(false);
	const [contentHeight, setContentHeight] = useState(0);

	const animatedHeight = useSharedValue(0);
	const rotation = useSharedValue(0);

	const onContentLayout = (event: LayoutChangeEvent) => {
        const measuredHeight = event.nativeEvent.layout.height;
        if(measuredHeight > 0 && measuredHeight !== contentHeight) {
            setContentHeight(measuredHeight);

            if(isOpen) {
                animatedHeight.value = withTiming(measuredHeight, {
                    duration: 200,
                    easing: Easing.out(Easing.cubic),
                });
            }
        }
    }

	const toggleOpen = () => {
		const next = !isOpen;
		setIsOpen(next);

		if(next && !hasOpenedOnce) {
			setHasOpenedOnce(true);
			onFirstOpen?.();
		}

		animatedHeight.value = withTiming(next ? contentHeight : 0, {
			duration: 250,
			easing: Easing.out(Easing.cubic),
		});

		rotation.value = withTiming(next ? 90 : 0, { duration: 250, easing: Easing.out(Easing.cubic) });
	}

	const containerStyle = useAnimatedStyle(() => ({
		height: animatedHeight.value,
	}));

	const chevronStyle = useAnimatedStyle(() => ({
		transform: [{ rotate: `${rotation.value}deg` }],
	}));

	return (
		<View className = 'mb-3 overflow-hidden rounded-2xl border border-border bg-card'>
			<Pressable onPress = { toggleOpen } className = 'flex-row items-center justify-between px-4 py-4'>
				<Text className = 'text-base font-semibold text-foreground'>{title}</Text>
				<Animated.View style = { chevronStyle }>
					<ChevronRight size = { 18 } color = '#736E65'/>
				</Animated.View>
			</Pressable>
			<Animated.View style = { [{ overflow: 'hidden' }, containerStyle] }>
				<View onLayout = { onContentLayout } className = 'absolute w-full px-4 pb-4'>
					{children}
				</View>
			</Animated.View>
		</View>
	)
}

export default AccordionItem;