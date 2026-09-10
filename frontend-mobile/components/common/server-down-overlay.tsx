import { View, ActivityIndicator } from 'react-native';
import { ServerCrash } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

interface ServerDownOverlayProps {
	isChecking: boolean;
	onRetry: () => void;
}

const ServerDownOverlay = ({ isChecking, onRetry }: ServerDownOverlayProps) => {
	return (
		<View className = 'absolute inset-0 z-50 items-center justify-center bg-background px-8'>
			<View className = 'h-16 w-16 items-center justify-center rounded-full bg-[#C45135]/10'>
				<ServerCrash size = { 32 } color = '#C45135'/>
			</View>
			<Text className = 'mt-4 text-center font-display text-xl text-foreground'>
				Qualcosa è andato storto
			</Text>
			<Text className = 'mt-2 text-center text-sm text-muted-foreground'>
				Non riusciamo a raggiungere il server. Assicurati che sia attivo e riprova.
			</Text>
			<Button onPress = { onRetry } disabled = { isChecking } className = 'mt-6 h-12 rounded-full px-8'>
				{isChecking ? (
					<ActivityIndicator color = '#FFFFFF'/>
				) : (
					<Text className = 'text-sm font-semibold text-primary-foreground'>Riprova</Text>
				)}
			</Button>
		</View>
	)
}

export default ServerDownOverlay;