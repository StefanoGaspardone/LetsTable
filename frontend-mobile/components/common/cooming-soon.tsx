import { View } from 'react-native';
import { Hammer } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

interface ComingSoonProps {
	title?: string;
	subtitle?: string;
}

const ComingSoon = ({ title = 'In arrivo', subtitle = 'Questa funzionalità è ancora in lavorazione. Torna a trovarci presto!' }: ComingSoonProps) => {
	return (
		<View className = 'flex-1 items-center justify-center bg-background px-8'>
			<View className = 'h-20 w-20 items-center justify-center rounded-full bg-[#C45135]/10'>
				<Hammer size = { 36 } color = '#C45135'/>
			</View>
			<Text className = 'mt-6 text-center font-display text-xl text-foreground'>
				{title}
			</Text>
			<Text className = 'mt-2 text-center text-sm text-muted-foreground'>
				{subtitle}
			</Text>
		</View>
	)
}

export default ComingSoon;