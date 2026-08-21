import { useState } from 'react';
import { View, Alert } from 'react-native';
import { router } from 'expo-router';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { PasswordInput } from '@/components/ui/password-input';
import { useAuth } from '@/contexts/auth-context';

import { login as log_in } from '@/api/auth';

import { loginSchema, LoginFormValues } from '@/schemas/auth-schema';

const LoginScreen = () => {
	const { login } = useAuth();
	const [isSubmitting, setIsSubmitting] = useState(false);

	const { control, handleSubmit, formState: { errors, isValid } } = useForm<LoginFormValues>({
		resolver: zodResolver(loginSchema),
		defaultValues: { identifier: '', password: '' },
		mode: 'onChange',
	});

	const onSubmit = async (values: LoginFormValues) => {
		setIsSubmitting(true);
		
		try {
			const response = await log_in(values);
			await login(response.accessToken, response.refreshToken, response.user);
			
			router.replace('/(tabs)/collection');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Credenziali non valide';
			Alert.alert('Errore', message);
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<View className = 'flex-1 justify-center bg-background px-6'>
			<Text className = 'mb-8 text-center font-display text-4xl text-primary'>
				Let&apos;s Table
			</Text>
			<Controller control = { control } name = 'identifier' render = { ({ field: { onChange, onBlur, value } }) => (
				<View className = 'mb-4'>
					<Input placeholder = 'Email o username' autoCapitalize = 'none' onBlur = { onBlur } onChangeText = { onChange } value = { value }/>
					{errors.identifier && (
						<Text className = 'mt-1 text-sm text-destructive'>{errors.identifier.message}</Text>
					)}
				</View>
			) }/>
			<Controller control = { control }
			name = 'password'
			render = { ({ field: { onChange, onBlur, value } }) => (
				<View className = 'mb-6'>
					<PasswordInput placeholder = 'Password' onBlur = { onBlur } onChangeText = { onChange } value = { value }/>
					{errors.password && (
						<Text className = 'mt-1 text-sm text-destructive'>{errors.password.message}</Text>
					)}
				</View>
			) }/>
			<Button onPress = { handleSubmit(onSubmit) } disabled = { !isValid || isSubmitting }>
				<Text>{isSubmitting ? 'Accesso in corso...' : 'Accedi'}</Text>
			</Button>
			<Button variant = 'ghost' className = 'mt-4' onPress = { () => router.push('/(auth)/signup') }>
				<Text>Non hai un account? Registrati</Text>
			</Button>
		</View>
	)
}

export default LoginScreen;