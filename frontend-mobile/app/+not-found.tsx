import { router, Stack } from 'expo-router';
import { View } from 'react-native';
import { Compass } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

const NotFoundScreen = () => {
	return (
		<>
			<Stack.Screen options = {{ headerShown: false }}/>
			<View className = 'flex-1 items-center justify-center bg-background px-8'>
				<View className = 'h-20 w-20 items-center justify-center rounded-full bg-[#C45135]/10'>
					<Compass size = { 40 } color = '#C45135'/>
				</View>
				<Text className = 'mt-6 font-display text-3xl text-foreground'>404</Text>
				<Text className = 'mt-2 text-center font-display text-xl text-foreground'>
					Pagina non trovata
				</Text>
				<Text className = 'mt-2 text-center text-sm text-muted-foreground'>
					La schermata che cerchi non esiste o è stata spostata.
				</Text>
				<Button onPress = { () => router.replace('/(tabs)/home') } className = 'mt-8 h-12 rounded-full px-8 active:scale-[0.98]'>
					<Text className = 'text-sm font-semibold text-primary-foreground'>Torna alla home</Text>
				</Button>
			</View>
		</>
	)
}

export default NotFoundScreen;