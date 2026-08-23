import { useState, forwardRef } from 'react';
import { View, Pressable, TextInput as RNTextInput } from 'react-native';
import { Eye, EyeOff } from 'lucide-react-native';

import { Input } from '@/components/ui/input';

interface PasswordInputProps {
	value: string;
	onChangeText: (text: string) => void;
	onBlur?: () => void;
	placeholder?: string;
	className?: string;
	placeholderTextColor?: string;
	iconColor?: string;
}

export const PasswordInput = forwardRef<RNTextInput, PasswordInputProps>(({ value, onChangeText, onBlur, placeholder, className, placeholderTextColor, iconColor }, ref) => {
    const [isVisible, setIsVisible] = useState(false);

    return (
        <View className = 'relative justify-center'>
            <Input ref = { ref } value = { value } onChangeText = { onChangeText } onBlur = { onBlur } placeholder = { placeholder } placeholderTextColor = { placeholderTextColor } secureTextEntry = { !isVisible } className = { `pr-11 ${className ?? ''}` }/>
            <Pressable onPress = { () => setIsVisible(prev => !prev) } className = 'absolute right-3' hitSlop = { 8 }>
                {isVisible ? (
                    <EyeOff size = { 20 } color = { iconColor } className = { iconColor ? undefined : 'text-muted-foreground' }/>
                ) : (
                    <Eye size = { 20 } color = { iconColor } className = { iconColor ? undefined : 'text-muted-foreground' }/>
                )}
            </Pressable>
        </View>
    )
});

PasswordInput.displayName = 'PasswordInput';