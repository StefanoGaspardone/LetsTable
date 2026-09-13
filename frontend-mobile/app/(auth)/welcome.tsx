import { View } from 'react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import MeepleIllustration from '@/components/common/meeple-illustration';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

const WelcomeScreen = () => {
	const router = useNavigationStack();

	return (
		<View className = 'flex-1 bg-background px-8 pb-6'>
			<View className = 'flex-1 items-center justify-center'>
				<MeepleIllustration size = { 140 } color = '#C45135'/>
				<Text className = 'mt-8 text-center font-display text-4xl text-foreground'>
					Let&apos;s Table
				</Text>
				<Text className = 'mt-2 text-center text-base text-muted-foreground'>
					La tua passione, organizzata.
				</Text>
			</View>
			<View className = 'mb-12 gap-3'>
				<Button className = 'h-14 rounded-full' onPress = { () => router.replace('/(auth)/login') }>
					<Text className = 'text-base font-semibold text-primary-foreground'>Accedi</Text>
				</Button>
				<Button variant = 'outline' className = 'h-14 rounded-full border-2 border-border bg-card' onPress = { () => router.replace('/(auth)/signup') }>
					<Text className = 'text-base font-semibold text-foreground'>Registrati</Text>
				</Button>
			</View>
		</View>
	)
}

export default WelcomeScreen;