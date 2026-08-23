import { View, ScrollView } from 'react-native';

import { Text } from '@/components/ui/text';

interface AuthScreenLayoutProps {
	title: string;
	subtitle: string;
	children: React.ReactNode;
	footer?: React.ReactNode;
}

const AuthScreenLayout = ({ title, subtitle, children, footer }: AuthScreenLayoutProps) => {
	return (
		<View className = 'flex-1 bg-background'>
			<ScrollView contentContainerStyle = {{ flexGrow: 1, justifyContent: 'center', padding: 24 }} keyboardShouldPersistTaps = 'handled'>
				<Text className = 'mb-1 text-center font-display text-3xl text-foreground'>{title}</Text>
				<Text className = 'mb-8 text-center text-base text-muted-foreground'>{subtitle}</Text>
				{children}
				{footer}
			</ScrollView>
		</View>
	)
}

export default AuthScreenLayout;