import { View } from 'react-native';
import { router } from 'expo-router';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

const WelcomeScreen = () => {
	return (
		<View className = 'flex-1 justify-between bg-background px-6 py-12'>
			<View className = 'flex-1 items-center justify-center'>
				<Text className = 'mb-4 text-center font-display text-5xl text-primary'>
					Let&apos;s Table
				</Text>
				<Text className = 'text-center text-base leading-6 text-muted-foreground'>
					Traccia la tua collezione di giochi da tavolo, gestisci le wishlist e registra le partite con i tuoi amici.
				</Text>
			</View>
			<View>
				<Button onPress = { () => router.push('/(auth)/login') }>
					<Text>Accedi</Text>
				</Button>
				<Button variant = 'ghost' className = 'mt-3' onPress = { () => router.push('/(auth)/signup') }>
					<Text>Crea un account</Text>
				</Button>
			</View>
		</View>
	)
}

export default WelcomeScreen;