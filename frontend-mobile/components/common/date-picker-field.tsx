import { useState } from 'react';
import { Pressable, Modal } from 'react-native';
import { Calendar } from 'react-native-calendars';
import { CalendarDays } from 'lucide-react-native';

import { Text } from '@/components/ui/text';

interface DatePickerFieldProps {
	value: string; // YYYY-MM-DD
	onChange: (date: string) => void;
}

const DatePickerField = ({ value, onChange }: DatePickerFieldProps) => {
	const [isOpen, setIsOpen] = useState(false);

	const formattedLabel = new Date(value + 'T00:00:00').toLocaleDateString('it-IT', {
		day: 'numeric',
		month: 'long',
		year: 'numeric',
	});

	return (
		<>
			<Pressable onPress = { () => setIsOpen(true) } className = 'flex-row items-center gap-2 rounded-xl border border-border bg-secondary px-3 py-2.5'>
				<CalendarDays size = { 16 } color = '#C45135'/>
				<Text className = 'text-sm text-foreground'>{formattedLabel}</Text>
			</Pressable>
			<Modal visible = { isOpen } transparent animationType = 'fade' onRequestClose = { () => setIsOpen(false) }>
				<Pressable className = 'flex-1 items-center justify-center bg-black/40' onPress = { () => setIsOpen(false) }>
					<Pressable className = 'w-[90%] overflow-hidden rounded-2xl bg-card' onPress = { e => e.stopPropagation()}>
						<Calendar current = { value } onDayPress = { day => { onChange(day.dateString); setIsOpen(false); } } markedDates = {{ [value]: { selected: true, selectedColor: '#C45135' } }} maxDate = { new Date().toISOString().slice(0, 10) } theme = {{ backgroundColor: '#FFFFFF', calendarBackground: '#FFFFFF', textSectionTitleColor: '#736E65', selectedDayBackgroundColor: '#C45135', todayTextColor: '#C45135', arrowColor: '#C45135', monthTextColor: '#1E1C1A' }}/>
					</Pressable>
				</Pressable>
			</Modal>
		</>
	)
}

export default DatePickerField;