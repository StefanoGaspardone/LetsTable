import { View, Pressable } from 'react-native';
import { Text } from '@/components/ui/text';

interface MatchesCalendarDayProps {
	date?: { day: number; dateString: string };
	state?: 'disabled' | 'today' | '';
	marking?: { count?: number; selected?: boolean };
	onPress?: (date: any) => void;
}

const MatchesCalendarDay = ({ date, state, marking, onPress }: MatchesCalendarDayProps) => {
	if(!date) return null;

	const count = marking?.count ?? 0;
	const isSelected = marking?.selected ?? false;
	const isToday = state === 'today';
	const isDisabled = state === 'disabled';

	return (
		<Pressable onPress = { () => onPress?.(date) } className = 'items-center justify-center p-0.5'>
			<View
	className='h-7 w-7 items-center justify-center rounded-full'
	style={[
		isSelected && { backgroundColor: '#C45135' },
		isToday && !isSelected && { backgroundColor: '#C45135B3' },
	]}
>
	<Text
		className={`text-sm ${
			isSelected
				? 'font-semibold text-white'
				: isDisabled
					? 'text-muted-foreground/40'
					: isToday
						? 'font-semibold text-[#FFFFFF]'
						: 'text-foreground'
		}`}
	>
		{date.day}
	</Text>
</View>
			<View className = 'mt-0.5 h-4 min-w-4 items-center justify-center rounded-full px-1' style = {{ backgroundColor: count > 0 ? 'rgba(196,81,53,0.15)' : 'transparent' }}>
				{count > 0 && (
					<Text className = 'text-[9px] font-bold text-[#C45135]'>{count > 9 ? '9+' : count}</Text>
				)}
			</View>
		</Pressable>
	)
}

export default MatchesCalendarDay;