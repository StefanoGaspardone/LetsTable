import { useMemo, useState } from 'react';
import { Pressable, Modal } from 'react-native';
import { Calendar } from 'react-native-calendars';
import { CalendarDays } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { useThemeColors } from '@/hooks/use-theme-colors';

import { formatFullDate, toLocalDateString } from '@/lib/date';

interface DatePickerFieldProps {
	value: string;
	onChange: (date: string) => void;
}

const DatePickerField = ({ value, onChange }: DatePickerFieldProps) => {
	const [isOpen, setIsOpen] = useState(false);

	const { colors, colorScheme } = useThemeColors();

	const calendarTheme = useMemo(() => ({
		backgroundColor: 'transparent',
		calendarBackground: 'transparent',
		textSectionTitleColor: colors.mutedForeground,
		selectedDayBackgroundColor: colors.primary,
		selectedDayTextColor: colors.primaryForeground,
		todayTextColor: colors.primary,
		arrowColor: colors.primary,
		monthTextColor: colors.foreground,
		dayTextColor: colors.foreground,
		textDisabledColor: `${colors.mutedForeground}50`,
		...({
			'stylesheet.calendar.main': {
				container: {
					backgroundColor: 'transparent',
				},
			},
			'stylesheet.calendar.header': {
				header: {
					flexDirection: 'row',
					justifyContent: 'space-between',
					paddingLeft: 10,
					paddingRight: 10,
					marginTop: 6,
					alignItems: 'center',
					backgroundColor: 'transparent',
				},
			},
		} as any),
	}), [colors]);

	const markedDates = useMemo(() => ({ [value]: { selected: true, selectedColor: colors.primary } }), [value, colors.primary]);

	return (
		<>
			<Pressable onPress = { () => setIsOpen(true) } className = 'flex-row items-center gap-2 rounded-xl border border-border bg-secondary px-3 py-2.5 active:opacity-75 active:scale-[0.98]'>
				<CalendarDays size = { 16 } color = { colors.primary }/>
				<Text className = 'text-sm text-foreground'>{formatFullDate(`${value}T00:00:00`)}</Text>
			</Pressable>
			<Modal visible = { isOpen } transparent animationType = 'fade' onRequestClose = { () => setIsOpen(false) }>
				<Pressable className = 'flex-1 items-center justify-center bg-black/40' onPress = { () => setIsOpen(false) }>
					<Pressable className = 'w-[90%] overflow-hidden rounded-2xl bg-card' onPress = { e => e.stopPropagation() }>
						<Calendar key = { colorScheme } current = { value } onDayPress = { day => { onChange(day.dateString); setIsOpen(false); } } markedDates = { markedDates } maxDate = { toLocalDateString(new Date()) } style = {{ backgroundColor: 'transparent' }} theme = { calendarTheme }/>
					</Pressable>
				</Pressable>
			</Modal>
		</>
	)
}

export default DatePickerField;