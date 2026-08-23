import { View, Pressable, LayoutChangeEvent } from 'react-native';
import { useState } from 'react';
import Animated, { useAnimatedStyle, withTiming, Easing } from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';
import { Home, Dices, Trophy, Users, User } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

const ICONS: Record<string, typeof Home> = {
	home: Home,
	collection: Dices,
	matches: Trophy,
	friends: Users,
	profile: User,
}

const LABELS: Record<string, string> = {
	home: 'Home',
	collection: 'Giochi',
	matches: 'Partite',
	friends: 'Amici',
	profile: 'Profilo',
}

const PILL_WIDTH = 52;
const PILL_HEIGHT = 30;

interface TabBarProps {
	state: {
		index: number;
		routes: Array<{ key: string; name: string }>;
	};
	navigation: {
		emit: (event: { type: string; target: string; canPreventDefault: boolean }) => { defaultPrevented: boolean };
		navigate: (name: string) => void;
	};
}

const TabBar = ({ state, navigation }: TabBarProps) => {
	const insets = useSafeAreaInsets();
	const [tabWidth, setTabWidth] = useState(0);

	const onLayout = (event: LayoutChangeEvent) => {
		setTabWidth(event.nativeEvent.layout.width / state.routes.length);
	}

	const pillStyle = useAnimatedStyle(() => ({
        transform: [
            {
                translateX: withTiming(
                    state.index * tabWidth + (tabWidth - PILL_WIDTH) / 2,
                    { duration: 280, easing: Easing.out(Easing.cubic) }
                ),
            },
        ],
    }));

	return (
		<View className = 'flex-row border-t border-border bg-card' style = {{ paddingBottom: insets.bottom || 12, paddingTop: 8 }} onLayout = { onLayout }>
			{tabWidth > 0 && (
				<Animated.View pointerEvents = 'none' style = { [{ position: 'absolute', top: 8, width: PILL_WIDTH, height: PILL_HEIGHT }, pillStyle] }>
					<View className = 'h-full w-full rounded-full' style = {{ backgroundColor: 'rgba(196, 81, 53, 0.12)' }}/>
				</Animated.View>
			)}
			{state.routes.map((route, index) => {
				const isFocused = state.index === index;
				const Icon = ICONS[route.name];
				const label = LABELS[route.name];

				const onPress = () => {
					const event = navigation.emit({ type: 'tabPress', target: route.key, canPreventDefault: true });
					if(!isFocused && !event.defaultPrevented) navigation.navigate(route.name);
				}

				return (
					<Pressable key = { route.key } onPress= { onPress } className = 'flex-1 items-center gap-1 pb-1'>
						<View style = {{ height: PILL_HEIGHT }} className = 'items-center justify-center'>
							<Icon size = { 22 } color = { isFocused ? '#C45135' : '#8A847A' }/>
						</View>
						<Text className = { `text-xs ${isFocused ? 'font-semibold text-primary' : 'text-muted-foreground'}` }>
							{label}
						</Text>
					</Pressable>
				)
			})}
		</View>
	)
}

export default TabBar;