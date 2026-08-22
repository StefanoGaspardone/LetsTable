import { useMemo, useState } from 'react';
import { View } from 'react-native';
import { Calendar, DateData } from 'react-native-calendars';

import { Text } from '@/components/ui/text';

import { useMatchCalendar } from '@/hooks/use-match';

interface MatchesCalendarViewProps {
	onSelectDay: (date: string) => void;
}

const MatchesCalendarView = ({ onSelectDay }: MatchesCalendarViewProps) => {
	const today = new Date();
	const [year, setYear] = useState(today.getFullYear());
	const [month, setMonth] = useState(today.getMonth() + 1);

	const { data: counts } = useMatchCalendar(year, month);

	const markedDates = useMemo(() => {
		const marks: Record<string, any> = {};
		counts?.forEach((day) => {
			marks[day.date] = {
				marked: true,
				dotColor: 'hsl(165, 48%, 24%)',
				customText: day.count,
			};
		});

		return marks;
	}, [counts]);

	const handleMonthChange = (dateData: DateData) => {
		setYear(dateData.year);
		setMonth(dateData.month);
	}

	const handleDayPress = (dateData: DateData) => {
		onSelectDay(dateData.dateString);
	}

	return (
		<View>
			<Calendar current = { `${year}-${String(month).padStart(2, '0')}-01` } markedDates = { markedDates } onDayPress = { handleDayPress } onMonthChange = { handleMonthChange }
                theme = {{
					backgroundColor: 'transparent',
					calendarBackground: 'transparent',
					dayTextColor: 'hsl(30, 20%, 11%)',
					monthTextColor: 'hsl(30, 20%, 11%)',
					textDisabledColor: 'hsl(30, 10%, 70%)',
					todayTextColor: 'hsl(165, 48%, 24%)',
					arrowColor: 'hsl(165, 48%, 24%)',
					dotColor: 'hsl(38, 62%, 55%)',
					selectedDayBackgroundColor: 'hsl(165, 48%, 24%)',
				}}
			/>
			{counts && counts.length > 0 && (
				<View className = 'px-4 pb-2'>
					<Text className = 'text-xs text-muted-foreground'>
						{counts.reduce((sum, d) => sum + d.count, 0)} partite questo mese
					</Text>
				</View>
			)}
		</View>
	)
}

export default MatchesCalendarView;