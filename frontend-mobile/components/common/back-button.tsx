import { useState } from 'react';
import { Pressable } from 'react-native';
import { ChevronLeft } from 'lucide-react-native';
import Animated, { useAnimatedStyle, interpolate, interpolateColor, Extrapolation, SharedValue } from 'react-native-reanimated';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface BackButtonProps {
	variant?: 'onLight' | 'onDark';
	progress?: SharedValue<number>;
}

const BackButton = ({ variant = 'onLight', progress }: BackButtonProps) => {
  	const [isPressed, setIsPressed] = useState(false);
	
	const router = useNavigationStack();
	const { colors, isDark: isSystemDark } = useThemeColors();

	const backgroundStyle = useAnimatedStyle(() => {
		const restOnDark = 'rgba(255,255,255,0.16)';
		const pressedOnDark = 'rgba(255,255,255,0.32)';

		const restOnLight = isSystemDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.06)';
		const pressedOnLight = isSystemDark ? 'rgba(255,255,255,0.18)' : 'rgba(0,0,0,0.14)';

		if(!progress) {
			const isDarkVariant = variant === 'onDark';

			if(isPressed) return { backgroundColor: isDarkVariant ? pressedOnDark : pressedOnLight };
			return { backgroundColor: isDarkVariant ? restOnDark : restOnLight };
		}

		const colorArray = isPressed
			? [pressedOnDark, pressedOnLight]
			: [restOnDark, restOnLight];

		return {
			backgroundColor: interpolateColor(progress.value, [0, 1], colorArray),
		}
	});

	const darkBgIconStyle = useAnimatedStyle(() => {
		if(progress) {
			return {
				opacity: interpolate(
					progress.value,
					[0, 1],
					[1, 0],
					Extrapolation.CLAMP
				),
			}
		}

		return {
			opacity: variant === 'onDark' ? 1 : 0,
		}
	});

	const lightBgIconStyle = useAnimatedStyle(() => {
		if(progress) {
			return {
				opacity: interpolate(
					progress.value,
					[0, 1],
					[0, 1],
					Extrapolation.CLAMP
				),
			}
		}

		return {
			opacity: variant === 'onLight' ? 1 : 0,
		}
	});

	return (
		<Pressable onPress = { () => router.back() } onPressIn = { () => setIsPressed(true) } onPressOut = { () => setIsPressed(false) } hitSlop = { 8 } style = {{ height: 36, width: 36 }}>
			<Animated.View
				style = {[
					{
						height: 36,
						width: 36,
						borderRadius: 999,
						alignItems: 'center',
						justifyContent: 'center',
					},
					backgroundStyle,
				]}
			>
				<Animated.View style = { [{ position: 'absolute' }, darkBgIconStyle] }>
					<ChevronLeft size = { 20 } color = '#FFFFFF'/>
				</Animated.View>
				<Animated.View style = { [{ position: 'absolute' }, lightBgIconStyle] }>
					<ChevronLeft size = { 20 } color = { colors.mutedForeground }/>
				</Animated.View>
			</Animated.View>
		</Pressable>
	)
}

export default BackButton;