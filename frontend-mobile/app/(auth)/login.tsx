import { useState } from 'react';
import { View } from 'react-native';
import { router } from 'expo-router';
import { useForm, Controller } from 'react-hook-form';

import { zodResolver } from '@hookform/resolvers/zod';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AuthScreenLayout from '@/components/auth-screen-layout';
import AuthField from '@/components/auth-field';
import { PasswordInput } from '@/components/ui/password-input';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';

import { LoginFormValues, loginSchema } from '@/schemas/auth-schema';

import { login as log_in } from '@/api/auth';

const LoginScreen = () => {
	const { login } = useAuth();
	const { showToast } = useToast();

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
			showToast(message, 'error');
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<AuthScreenLayout title = 'Bentornato!' subtitle = 'Accedi per tracciare le tue partite.'
			footer = {
				<Text className = 'text-sm text-white/70 text-center mt-3'>
					Non hai un account? <Text className = 'text-[#C1502E] hover:underline text-sm font-bold' onPress = { () => router.push('/(auth)/signup') }>Registrati</Text>
				</Text>
			}
		>
			<Controller control = { control } name = 'identifier'
				render = { ({ field: { onChange, onBlur, value } }) => (
					<AuthField label = 'Email o username' placeholder = 'boardgamer@example.com' autoCapitalize = 'none' value = { value } onChangeText = { onChange } onBlur = { onBlur } error = { errors.identifier?.message }/>
				)}
			/>
			<Controller control = { control } name = 'password' render = { ({ field: { onChange, onBlur, value } }) => (
					<View className = 'mb-2'>
						<Text className = 'mb-1.5 text-sm text-white/70'>Password</Text>
						<PasswordInput placeholder = '••••••••' placeholderTextColor = '#8A817A' value = { value } onChangeText = { onChange } onBlur = { onBlur } className = 'border-[#4A423C] bg-[#3A332E] text-white' iconColor = '#8A817A'/>
						{errors.password && (
							<Text className = 'mt-1 text-xs text-red-400'>{errors.password.message}</Text>
						)}
					</View>
				)}
			/>
			<Button className = 'mt-5 h-14 rounded-full bg-[#C1502E]' onPress = { handleSubmit(onSubmit) } disabled = { !isValid || isSubmitting }>
				<Text className = 'text-base font-semibold text-white'>
					{isSubmitting ? 'Accesso in corso...' : 'Accedi'}
				</Text>
			</Button>
		</AuthScreenLayout>
	)
}

export default LoginScreen;