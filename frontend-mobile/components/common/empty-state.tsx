import { View } from 'react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';

interface EmptyStateProps {
	icon: React.ReactNode;
	title: string;
	subtitle: string;
	actionLabel?: string;
	onAction?: () => void;
}

const EmptyState = ({ icon, title, subtitle, actionLabel, onAction }: EmptyStateProps) => {
	return (
		<View className = 'items-center rounded-2xl border border-border bg-card p-6'>
			<View className = 'mb-3 h-12 w-12 items-center justify-center'>{icon}</View>
			<Text className = 'text-center font-display text-lg text-foreground'>{title}</Text>
			<Text className = 'mt-1 text-center text-sm text-muted-foreground'>{subtitle}</Text>
			{actionLabel && onAction && (
				<Button className = 'mt-4 h-11 rounded-full px-6' onPress = { onAction }>
					<Text className = 'text-sm font-semibold text-primary-foreground'>{actionLabel}</Text>
				</Button>
			)}
		</View>
	)
}

export default EmptyState;