import { ReactNode } from 'react';
import { View } from 'react-native';
import Animated from 'react-native-reanimated';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

interface ScreenHeaderProps {
	title: string;
	leftElement?: ReactNode;
	rightElement?: ReactNode;
	renderBackground?: ReactNode;
	titleStyle?: any;
}

const ScreenHeader = ({ title, leftElement, rightElement, renderBackground, titleStyle }: ScreenHeaderProps) => {
	const insets = useSafeAreaInsets();

	return (
		<View className = { `px-4 pb-3 ${renderBackground ? '' : 'bg-background'}` } style = {{ paddingTop: insets.top + 12 }}>
			{renderBackground && (
				<View style = {{ position: 'absolute', top: 0, left: 0, right: 0, bottom: 0 }}>
					{renderBackground}
				</View>
			)}
			<View className = 'flex-row items-center justify-center'>
				<Animated.Text style = { [{ fontFamily: 'PlayfairDisplay_700Bold', fontSize: 24, color: '#1E1C1A' }, titleStyle] }>
					{title}
				</Animated.Text>
				{leftElement && (
					<View className = 'absolute left-0' style = {{ top: 0, bottom: 0, justifyContent: 'center' }}>
						{leftElement}
					</View>
				)}
				{rightElement && (
					<View className = 'absolute right-0' style = {{ top: 0, bottom: 0, justifyContent: 'center' }}>
						{rightElement}
					</View>
				)}
			</View>
		</View>
	)
}

export default ScreenHeader;