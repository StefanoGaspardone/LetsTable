import { View } from 'react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';

interface AuthFieldProps {
	label: string;
	placeholder: string;
	value: string;
	onChangeText: (text: string) => void;
	onBlur?: () => void;
	autoCapitalize?: 'none' | 'sentences' | 'words' | 'characters';
	keyboardType?: 'default' | 'email-address' | 'number-pad';
	error?: string;
}

const AuthField = ({ label, placeholder, value, onChangeText, onBlur, autoCapitalize, keyboardType, error }: AuthFieldProps) => {
	return (
		<View className = 'mb-4'>
			<Text className = 'mb-1.5 text-sm font-medium text-foreground'>{label}</Text>
			<Input placeholder = { placeholder } value = { value } onChangeText = { onChangeText } onBlur = { onBlur } autoCapitalize = { autoCapitalize } keyboardType = { keyboardType } className = 'h-14 rounded-2xl border-border bg-input'/>
			{error && <Text className = 'mt-1 text-xs text-destructive'>{error}</Text>}
		</View> 
	)
}

export default AuthField;