import { View } from 'react-native';

import { Text } from '@/components/ui/text';

interface QuickStatCardProps {
	icon: React.ReactNode;
	label: string;
	value: number;
}

const QuickStatCard = ({ icon, label, value }: QuickStatCardProps) => {
	return (
		<View className = 'flex-1 flex-row items-center gap-3 rounded-2xl border border-border bg-card p-4'>
			<View className = 'h-9 w-9 items-center justify-center'>{icon}</View>
			<View className = 'flex-1'>
				<Text className = 'text-xs uppercase tracking-wide text-muted-foreground' numberOfLines={1}>
					{label}
				</Text>
				<Text className = 'mt-0.5 font-display text-3xl text-foreground'>{value}</Text>
			</View>
		</View>
	)
}

export default QuickStatCard;