import { View, ImageBackground } from 'react-native';
import { router } from 'expo-router';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

const WelcomeScreen = () => {
	return (
		<ImageBackground source = { require('@/assets/images/welcome-bg.jpg') } className = 'flex-1' resizeMode = 'cover'>
			<View className = 'flex-1 justify-end bg-black/40 px-8 pb-12'>
				<Text className = 'mb-1 text-center font-display text-3xl font-bold tracking-wide text-white'>
					LET&apos;S TABLE
				</Text>
				<Text className = 'mb-8 text-center text-sm text-white/80'>
					Scopri, gioca, condividi.
				</Text>
				<View className = 'gap-3'>
					<Button className = 'h-14 rounded-full' onPress={() => router.push('/(auth)/login')}>
						<Text className = 'text-base font-semibold'>Log in</Text>
					</Button>
					<Button className = 'h-14 rounded-full bg-white active:bg-neutral-200' onPress = { () => router.push('/(auth)/signup') }>
						<Text className = 'text-base font-semibold text-foreground'>Crea un account</Text>
					</Button>
				</View>
			</View>
		</ImageBackground>
	)
}

export default WelcomeScreen; 