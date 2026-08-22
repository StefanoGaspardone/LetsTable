import { View } from 'react-native';
import { Crown } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

interface PlayerBadgeProps {
	name: string;
	color?: string | null;
	score?: number | null;
	isWinner?: boolean | null;
	startingPosition?: number | null;
}

const PlayerBadge = ({ name, color, score, isWinner, startingPosition }: PlayerBadgeProps) => {
	return (
		<View className = 'flex-row items-center gap-3 border-b border-border px-4 py-3'>
			{startingPosition != null && (
				<Text className = 'w-5 text-center text-xs text-muted-foreground'>{startingPosition}</Text>
			)}
			{!!(color) && (
				<View className = 'h-4 w-4 rounded-full border border-border' style = {{ backgroundColor: color }}/>
			)}
			<Text className = 'flex-1 text-base text-foreground'>{name}</Text>
			{score != null && <Text className = 'text-sm text-muted-foreground'>{score} pt</Text>}
			{isWinner && <Crown size = { 18 } className = 'text-accent'/>}
		</View>
	)
}

export default PlayerBadge;