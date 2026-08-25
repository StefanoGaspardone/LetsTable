import { useState } from 'react';
import { Pressable } from 'react-native';
import { router } from 'expo-router';
import { ChevronLeft } from 'lucide-react-native';
import Animated, { useAnimatedStyle, interpolate, interpolateColor, Extrapolation, SharedValue } from 'react-native-reanimated';

interface BackButtonProps {
	variant?: 'onLight' | 'onDark';
	progress?: SharedValue<number>;
}

const BackButton = ({ variant = 'onLight', progress }: BackButtonProps) => {
	const [isPressed, setIsPressed] = useState(false);

	const backgroundStyle = useAnimatedStyle(() => {
		const restOnDark = 'rgba(255,255,255,0.16)';
		const pressedOnDark = 'rgba(255,255,255,0.32)';
		const restOnLight = 'rgba(0,0,0,0.06)';
		const pressedOnLight = 'rgba(0,0,0,0.14)';

		if(!progress) {
			const isDark = variant === 'onDark';
			
			return {
				backgroundColor: isPressed
					? isDark
						? pressedOnDark
						: pressedOnLight
					: isDark
						? restOnDark
						: restOnLight,
			}
		}

		const inputRange = isPressed ? [0, 1] : [0, 1];
		const colors = isPressed
			? [pressedOnDark, pressedOnLight]
			: [restOnDark, restOnLight];

		return {
			backgroundColor: interpolateColor(progress.value, inputRange, colors),
		}
	});

	const darkBgIconStyle = useAnimatedStyle(() => ({
		opacity: progress
			? interpolate(progress.value, [0, 1], [1, 0], Extrapolation.CLAMP)
			: variant === 'onDark'
				? 1
				: 0,
	}));

	const lightBgIconStyle = useAnimatedStyle(() => ({
		opacity: progress
			? interpolate(progress.value, [0, 1], [0, 1], Extrapolation.CLAMP)
			: variant === 'onLight'
				? 1
				: 0,
	}));

	return (
		<Pressable onPress = { () => router.back() } onPressIn = { () => setIsPressed(true) } onPressOut = { () => setIsPressed(false) } hitSlop = { 8 } style = {{ height: 36, width: 36 }}>
			<Animated.View
				style = { [{ height: 36, width: 36, borderRadius: 999, alignItems: 'center', justifyContent: 'center' }, backgroundStyle ] }>
				<Animated.View style = { [{ position: 'absolute' }, darkBgIconStyle] }>
					<ChevronLeft size = { 20 } color = '#FFFFFF'/>
				</Animated.View>
				<Animated.View style = { [{ position: 'absolute' }, lightBgIconStyle] }>
					<ChevronLeft size = { 20 } color = '#736E65'/>
				</Animated.View>
			</Animated.View>
		</Pressable>
	)
}

export default BackButton;