import { View } from 'react-native';
import { Trophy } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

interface WinRateCardProps {
	totalWins: number;
	totalMatches: number;
}

const WinRateCard = ({ totalWins, totalMatches }: WinRateCardProps) => {
	const winRate = totalMatches > 0 ? Math.round((totalWins / totalMatches) * 100) : 0;

	return (
		<View className = 'flex-row items-center gap-3 rounded-2xl border border-border bg-card p-4'>
			<View className = 'h-9 w-9 items-center justify-center'>
				<Trophy size = { 36 } color = '#C45135'/>
			</View>
			<View className = 'flex-1'>
				<View className = 'flex-row items-center justify-between'>
					<Text className = 'text-xs font-semibold uppercase tracking-wide text-muted-foreground' numberOfLines = { 1 }>
						Vittorie
					</Text>
					<Text className = 'text-sm text-muted-foreground'>
						<Text className = 'font-semibold text-foreground'>{totalWins}</Text>/{totalMatches} · <Text className = 'font-semibold text-[#C45135]'>{winRate}%</Text>
					</Text>
				</View>
				<View className = 'mt-2 h-3 w-full overflow-hidden rounded-full bg-secondary'>
					{totalMatches > 0 && (
						<View style = {{ width: `${winRate}%` }} className = 'h-full rounded-full bg-[#C45135]'/>
					)}
				</View>
			</View>
		</View>
	)
}

export default WinRateCard;