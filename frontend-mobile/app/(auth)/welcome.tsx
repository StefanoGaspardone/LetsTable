import { View } from 'react-native';
import { router } from 'expo-router';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import MeepleIllustration from '@/components/common/meeple-illustration';

const WelcomeScreen = () => {
	return (
		<View className = 'flex-1 bg-background px-8'>
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
				<Button className = 'h-14 rounded-full' onPress = { () => router.push('/(auth)/login') }>
					<Text className = 'text-base font-semibold text-primary-foreground'>Accedi</Text>
				</Button>
				<Button variant = 'outline' className = 'h-14 rounded-full border-2 border-border bg-card' onPress = { () => router.push('/(auth)/signup') }>
					<Text className = 'text-base font-semibold text-foreground'>Registrati</Text>
				</Button>
			</View>
		</View>
	)
}

export default WelcomeScreen;