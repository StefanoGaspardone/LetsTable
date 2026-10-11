import { useRef, useState } from 'react';
import { View, TextInput as RNTextInput } from 'react-native';

import { Input } from '@/components/ui/input';

interface OtpInputProps {
	value: string;
	onChange: (value: string) => void;
	length?: number;
}

const OtpInput = ({ value, onChange, length = 6 }: OtpInputProps) => {
	const inputRefs = useRef<Array<RNTextInput | null>>([]);
	const [focusedIndex, setFocusedIndex] = useState<number | null>(null);
	const digits = value.split('');

	const handleChangeDigit = (text: string, index: number) => {
		if(text.length > 1) {
			onChange(text.slice(0, length));
			inputRefs.current[length - 1]?.focus();
			
            return;
		}

		const nextDigits = [...digits];
		nextDigits[index] = text;
		onChange(nextDigits.join('').slice(0, length));

		if(text && index < length - 1) inputRefs.current[index + 1]?.focus();
	}

	const handleKeyPress = (key: string, index: number) => {
		if(key === 'Backspace' && !digits[index] && index > 0) inputRefs.current[index - 1]?.focus();
	}

	return (
		<View className = 'flex-row justify-between gap-2'>
			{Array.from({ length }).map((_, index) => (
				<Input key = { index } ref = { ref => { inputRefs.current[index] = ref; } } value = { digits[index] ?? '' } onChangeText = { text => handleChangeDigit(text, index) } onKeyPress = { ({ nativeEvent }) => handleKeyPress(nativeEvent.key, index) } onFocus = { () => setFocusedIndex(index) } onBlur = { () => setFocusedIndex(prev => (prev === index ? null : prev)) } keyboardType = 'number-pad' maxLength = { index === 0 ? length : 1 } textAlign = 'center' className = { `h-14 w-12 rounded-2xl bg-input text-center text-2xl ${focusedIndex === index ? 'border-2 border-primary' : 'border-border'}` }/>
			))}
		</View>
	)
}

export default OtpInput;