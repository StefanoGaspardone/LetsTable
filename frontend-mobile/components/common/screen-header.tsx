import { View } from 'react-native';
import { useSafeAreaInsets } from 'react-native-safe-area-context';

import { Text } from '@/components/ui/text';

interface ScreenHeaderProps {
	title: string;
	rightElement?: React.ReactNode;
}

const ScreenHeader = ({ title, rightElement }: ScreenHeaderProps) => {
	const insets = useSafeAreaInsets();

	return (
		<View className = 'flex-row items-center justify-center bg-background px-4 pb-3' style = {{ paddingTop: insets.top + 12 }}>
			<View className = 'flex-row items-center justify-center flex-1'>
				<Text className = 'font-display text-3xl text-foreground'>{title}</Text>
				{rightElement && (
					<View className = 'absolute right-0' style={{ top: 0, bottom: 0, justifyContent: 'center' }}>
						<View className = 'h-9 w-9 items-center justify-center rounded-full bg-secondary'>
							{rightElement}
						</View>
					</View>
				)}
			</View>
		</View>
	)
}

export default ScreenHeader;