import { useRef, useState } from 'react';
import { View, TextInput as RNTextInput } from 'react-native';

import { Input } from '@/components/ui/input';

interface OtpInputProps {
	value: string;
	onChange: (value: string) => void;
	length?: number;
}

const onlyDigits = (text: string) => text.replace(/\D/g, '');

const OtpInput = ({ value, onChange, length = 6 }: OtpInputProps) => {
	const inputRefs = useRef<Array<RNTextInput | null>>([]);
	const [focusedIndex, setFocusedIndex] = useState<number | null>(null);

	const digits = value.split('');

	const applyPaste = (pasted: string) => {
		const code = pasted.slice(0, length);

		onChange(code);
		inputRefs.current[Math.min(code.length, length) - 1]?.focus();
	}

	const handleChangeDigit = (text: string, index: number) => {
		const sanitized = onlyDigits(text);
		const current = digits[index];

		const isPaste = sanitized.length > 2 || (sanitized.length === 2 && !current);

		if(isPaste) {
			applyPaste(sanitized);
			return;
		}

		const nextDigit = sanitized.length === 2
			? (sanitized.startsWith(current) ? sanitized[1] : sanitized[0])
			: sanitized;

		const nextDigits = [...digits];
		nextDigits[index] = nextDigit;
		onChange(nextDigits.join('').slice(0, length));

		if(nextDigit && index < length - 1) inputRefs.current[index + 1]?.focus();
	}

	const handleKeyPress = (key: string, index: number) => {
		if(key === 'Backspace' && !digits[index] && index > 0) inputRefs.current[index - 1]?.focus();
	}

	return (
		<View className = 'flex-row justify-between gap-2'>
			{Array.from({ length }).map((_, index) => (
				<Input
					key = { index }
					ref = { ref => { inputRefs.current[index] = ref; } }
					value = { digits[index] ?? '' }
					onChangeText = { text => handleChangeDigit(text, index) }
					onKeyPress = { ({ nativeEvent }) => handleKeyPress(nativeEvent.key, index) }
					onFocus = { () => setFocusedIndex(index) }
					onBlur = { () => setFocusedIndex(prev => (prev === index ? null : prev)) }
					keyboardType = 'number-pad'
					textContentType = { index === 0 ? 'oneTimeCode' : 'none' }
					autoComplete = { index === 0 ? 'one-time-code' : 'off' }
					maxLength = { index === 0 ? length : 2 }
					selectTextOnFocus
					textAlign = 'center'
					className = { `h-14 w-12 rounded-2xl bg-input text-center text-2xl ${focusedIndex === index ? 'border-2 border-primary' : 'border-border'}` }
				/>
			))}
		</View>
	)
}

export default OtpInput;