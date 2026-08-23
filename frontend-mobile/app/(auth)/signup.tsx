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

import { SignupFormValues, signupSchema } from '@/schemas/auth-schema';

import { signup } from '@/api/auth';

import { useToast } from '@/contexts/toast-context';

const SignupScreen = () => {
	const { showToast } = useToast();
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
			router.push({ pathname: '/(auth)/activate', params: { identifier: response.email } });
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la registrazione';
			showToast(message, 'error');
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<AuthScreenLayout title = 'Unisciti alla community!' subtitle = 'Crea il tuo profilo e inizia a condividere.'
			footer = {
				<>
					<Button className = 'mt-5 h-14 rounded-full bg-[#C1502E]' onPress = { handleSubmit(onSubmit) } disabled = { !isValid || isSubmitting }>
						<Text className = 'text-base font-semibold text-white'>
							{isSubmitting ? 'Registrazione...' : 'Crea account'}
						</Text>
					</Button>
					<Text className = 'mt-3 text-center text-xs text-white/40'>
						Creando un account accetti i Termini e Condizioni.
					</Text>
					<Text className = 'text-sm text-white/70 mt-3 text-center'>
						Hai già un account? <Text className = 'text-[#C1502E] hover:underline text-sm font-bold' onPress = { () => router.push('/(auth)/login') }>Accedi</Text>
					</Text>
				</>
			}
		>
			<Controller control = { control } name = 'username'
				render = { ({ field: { onChange, onBlur, value } }) => (
					<AuthField label = 'Username giocatore' placeholder = 'es. MeepleKing99' autoCapitalize = 'none' value = { value } onChangeText = { onChange } onBlur = { onBlur } error = { errors.username?.message }/>
				)}
			/>
			<Controller control = { control } name = 'email'
				render = { ({ field: { onChange, onBlur, value } }) => (
					<AuthField label = 'Email' placeholder = 'nome@esempio.it' autoCapitalize = 'none' keyboardType = 'email-address' value = { value } onChangeText = { onChange } onBlur = { onBlur } error = { errors.email?.message }/>
				)}
			/>
			<View className = 'mb-4'>
				<Text className = 'mb-1.5 text-sm text-white/70'>Password</Text>
				<Controller control = { control } name = 'password'
					render = { ({ field: { onChange, onBlur, value } }) => (
						<PasswordInput placeholder = 'Almeno 8 caratteri' placeholderTextColor = '#8A817A' value = { value } onChangeText = { onChange } onBlur = { onBlur } className = 'border-[#4A423C] bg-[#3A332E] text-white' iconColor = '#8A817A'/>
					)}
				/>
				{errors.password && <Text className = 'mt-1 text-xs text-red-400'>{errors.password.message}</Text>}
			</View>
			<View className = 'mb-2'>
				<Text className = 'mb-1.5 text-sm text-white/70'>Conferma password</Text>
				<Controller control = { control } name = 'confirmPassword'
					render = { ({ field: { onChange, onBlur, value } }) => (
						<PasswordInput placeholder = 'Ripeti la password' placeholderTextColor = '#8A817A' value = { value } onChangeText = { onChange } onBlur = { onBlur } className = 'border-[#4A423C] bg-[#3A332E] text-white' iconColor = '#8A817A'/>
					)}
				/>
				{errors.confirmPassword && (
					<Text className = 'mt-1 text-xs text-red-400'>{errors.confirmPassword.message}</Text>
				)}
			</View>
		</AuthScreenLayout>
	)
}

export default SignupScreen;