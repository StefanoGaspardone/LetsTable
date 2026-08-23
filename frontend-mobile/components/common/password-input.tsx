import { useState, forwardRef } from 'react';
import { View, Pressable, TextInput as RNTextInput } from 'react-native';
import { Eye, EyeOff } from 'lucide-react-native';
import { Input } from '@/components/ui/input';

interface PasswordInputProps {
	value: string;
	onChangeText: (text: string) => void;
	onBlur?: () => void;
	placeholder?: string;
}

const PasswordInput = forwardRef<RNTextInput, PasswordInputProps>(
	({ value, onChangeText, onBlur, placeholder }, ref) => {
		const [isVisible, setIsVisible] = useState(false);

		return (
			<View className="h-14 flex-row items-center rounded-2xl border border-border bg-input pr-2">
				<Input
					ref={ref}
					value={value}
					onChangeText={onChangeText}
					onBlur={onBlur}
					placeholder={placeholder}
					secureTextEntry={!isVisible}
					className="h-14 flex-1 border-0 bg-transparent"
				/>
				<Pressable onPress={() => setIsVisible((prev) => !prev)} className="p-2" hitSlop={8}>
					{isVisible ? (
						<EyeOff size={20} className="text-muted-foreground" />
					) : (
						<Eye size={20} className="text-muted-foreground" />
					)}
				</Pressable>
			</View>
		);
	}
);

PasswordInput.displayName = 'PasswordInput';

export default PasswordInput;