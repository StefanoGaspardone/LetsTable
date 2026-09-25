import { useState } from 'react';
import { Pressable, Modal } from 'react-native';
import { Calendar } from 'react-native-calendars';
import { CalendarDays } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

import { useThemeColors } from '@/hooks/use-theme-colors';

interface DatePickerFieldProps {
	value: string; // YYYY-MM-DD
	onChange: (date: string) => void;
}

const DatePickerField = ({ value, onChange }: DatePickerFieldProps) => {
	const [isOpen, setIsOpen] = useState(false);

	const { colors, colorScheme } = useThemeColors();

	const formattedLabel = new Date(value + 'T00:00:00').toLocaleDateString('it-IT', {
		day: 'numeric',
		month: 'long',
		year: 'numeric',
	});

	return (
		<>
			<Pressable onPress = { () => setIsOpen(true) } className = 'flex-row items-center gap-2 rounded-xl border border-border bg-secondary px-3 py-2.5 active:opacity-75 active:scale-[0.98]'>
				<CalendarDays size = { 16 } color = { colors.primary }/>
				<Text className = 'text-sm text-foreground'>{formattedLabel}</Text>
			</Pressable>
			<Modal visible = { isOpen } transparent animationType = 'fade' onRequestClose = { () => setIsOpen(false) }>
				<Pressable className = 'flex-1 items-center justify-center bg-black/40' onPress = { () => setIsOpen(false) }>
					<Pressable className = 'w-[90%] overflow-hidden rounded-2xl bg-card' onPress = { e => e.stopPropagation()}>
						<Calendar key = { colorScheme } current = { value } onDayPress = { day => { onChange(day.dateString); setIsOpen(false); } } markedDates = {{ [value]: { selected: true, selectedColor: colors.primary } }} maxDate = { new Date().toISOString().slice(0, 10) } style = {{ backgroundColor: 'transparent' }}
							theme = {{
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
							}}
						/>
					</Pressable>
				</Pressable>
			</Modal>
		</>
	)
}

export default DatePickerField;