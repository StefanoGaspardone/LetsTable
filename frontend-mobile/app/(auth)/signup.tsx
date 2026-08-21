import { useState } from 'react';
import { View, Alert } from 'react-native';
import { router } from 'expo-router';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';

import { signupSchema, SignupFormValues } from '@/schemas/auth-schema';

import { signup } from '@/api/auth';

const SignupScreen = () => {
	const [isSubmitting, setIsSubmitting] = useState(false);

	const { control, handleSubmit, formState: { errors, isValid } } = useForm<SignupFormValues>({
		resolver: zodResolver(signupSchema),
		defaultValues: { username: '', email: '', password: '', confirmPassword: '' },
		mode: 'onChange',
	});

	const onSubmit = async (values: SignupFormValues) => {
		setIsSubmitting(true);
		
		try {
			const response = await signup(values);
			router.push({
				pathname: '/(auth)/activate',
				params: { identifier: response.email },
			});
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la registrazione';
			Alert.alert('Errore', message);
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<View className = 'flex-1 justify-center bg-background px-6'>
			<Text className = 'mb-8 text-center font-display text-3xl text-foreground'>
				Crea account
			</Text>
			<Controller control = { control } name = 'username' render = { ({ field: { onChange, onBlur, value } }) => (
				<View className = 'mb-4'>
					<Input placeholder = 'Username' autoCapitalize = 'none' onBlur = { onBlur } onChangeText = { onChange } value = { value }/>
					{errors.username && (
						<Text className = 'mt-1 text-sm text-destructive'>{errors.username.message}</Text>
					)}
				</View>
			) }/>
			<Controller control = { control } name = 'email' render = { ({ field: { onChange, onBlur, value } }) => (
				<View className = 'mb-4'>
					<Input placeholder = 'Email' autoCapitalize = 'none' keyboardType = 'email-address' onBlur = { onBlur } onChangeText = { onChange } value = { value }/>
					{errors.email && (
						<Text className = 'mt-1 text-sm text-destructive'>{errors.email.message}</Text>
					)}
				</View>
			) }/>
			<Controller
			control = { control }
			name = 'password'
			render = { ({ field: { onChange, onBlur, value } }) => (
				<View className = 'mb-4'>
					<PasswordInput placeholder = 'Password' onBlur = { onBlur } onChangeText = { onChange } value = { value }/>
					{errors.password && (
						<Text className = 'mt-1 text-sm text-destructive'>{errors.password.message}</Text>
					)}
				</View>
			) }/>
			<Controller control = { control } name = 'confirmPassword' render = { ({ field: { onChange, onBlur, value } }) => (
				<View className = 'mb-6'>
					<PasswordInput placeholder = 'Conferma password' onBlur = { onBlur } onChangeText = { onChange } value = { value }/>
					{errors.confirmPassword && (
						<Text className = 'mt-1 text-sm text-destructive'>{errors.confirmPassword.message}</Text>
					)}
				</View>
			) }/>
			<Button onPress = { handleSubmit(onSubmit) } disabled = { !isValid || isSubmitting }>
				<Text>{isSubmitting ? 'Registrazione...' : 'Registrati'}</Text>
			</Button>
			<Button variant = 'ghost' className = 'mt-4' onPress = { () => router.back() }>
				<Text>Hai già un account? Accedi</Text>
			</Button>
		</View>
	)
}

export default SignupScreen;