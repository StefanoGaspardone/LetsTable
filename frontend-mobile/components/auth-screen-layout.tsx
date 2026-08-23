import { View, ScrollView, ImageBackground } from 'react-native';

import { Text } from '@/components/ui/text';

interface AuthScreenLayoutProps {
	title: string;
	subtitle: string;
	children: React.ReactNode;
	footer?: React.ReactNode;
}

const AuthScreenLayout = ({ title, subtitle, children, footer }: AuthScreenLayoutProps) => {
	return (
		<ImageBackground source = { require('@/assets/images/welcome-bg.jpg') } className = 'flex-1' resizeMode = 'cover'>
			<View className = 'flex-1 bg-black/55'>
				<ScrollView contentContainerStyle = {{ flexGrow: 1, justifyContent: 'center', padding: 24 }}>
					<Text className = 'mb-1 text-center text-2xl font-bold text-white'>{title}</Text>
					<Text className = 'mb-8 text-center text-sm text-white/70'>{subtitle}</Text>
					{children}
					{footer}
				</ScrollView>
			</View>
		</ImageBackground>
	)
}

export default AuthScreenLayout;