import { ReactNode } from 'react';
import { View } from 'react-native';
import { Hammer } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface ComingSoonProps {
	title?: string;
	subtitle?: string;
	icon?: ReactNode;
}

const ComingSoon = ({ title = 'In arrivo', subtitle = 'Questa funzionalità è ancora in lavorazione. Torna a trovarci presto!', icon }: ComingSoonProps) => {
	const { colors } = useThemeColors();

	return (
		<View className = 'flex-1 items-center justify-center bg-background px-8'>
			<View className = 'h-20 w-20 items-center justify-center rounded-full bg-primary/10'>
				{icon ?? <Hammer size = { 36 } color = { colors.primary }/>}
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