import { View } from 'react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';

interface AuthFieldProps {
	label: string;
	placeholder: string;
	value: string;
	onChangeText: (text: string) => void;
	onBlur?: () => void;
	secureTextEntry?: boolean;
	autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
	keyboardType?: 'default' | 'email-address' | 'number-pad';
	error?: string;
}

const AuthField = ({ label, placeholder, value, onChangeText, onBlur, secureTextEntry, autoCapitalize, keyboardType, error }: AuthFieldProps) => {
	return (
		<View className = 'mb-4'>
			<Text className = 'mb-1.5 text-sm text-white/70'>{label}</Text>
			<Input placeholder = { placeholder } placeholderTextColor = '#8A817A' value = { value } onChangeText = { onChangeText } onBlur = { onBlur } secureTextEntry = { secureTextEntry } autoCapitalize = { autoCapitalize } keyboardType = { keyboardType } className = 'border-[#4A423C] bg-[#3A332E] text-white'/>
			{error && <Text className = 'mt-1 text-xs text-red-400'>{error}</Text>}
		</View>
	)
}

export default AuthField;