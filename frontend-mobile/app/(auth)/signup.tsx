import { useState } from 'react';
import { Pressable, View } from 'react-native';
import { router } from 'expo-router';
import * as WebBrowser from 'expo-web-browser';
import { Check } from 'lucide-react-native';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AuthScreenLayout from '@/components/auth/auth-screen-layout';
import AuthField from '@/components/auth/auth-field';
import PasswordInput from '@/components/common/password-input';

import { useToast } from '@/contexts/toast-context';

import { SignupFormValues, signupSchema } from '@/schemas/auth-schema';

import { signup } from '@/api/auth';

const SignupScreen = () => {
	const { showToast } = useToast();
	const [isSubmitting, setIsSubmitting] = useState(false);

	const { control, handleSubmit, formState: { errors, isValid } } = useForm<SignupFormValues>({
		resolver: zodResolver(signupSchema),
		defaultValues: {
			username: '',
			email: '',
			password: '',
			confirmPassword: '',
			acceptTerms: false
		},
		mode: 'onChange',
	});

	const openTerms = async () => {
		const url = process.env.EXPO_PUBLIC_TERMS_URL ?? 'https://letstable.onrender.com/terms.html'
        await WebBrowser.openBrowserAsync(url);
    }

	const onSubmit = async (values: SignupFormValues) => {
		setIsSubmitting(true);
		
		try {
			const { acceptTerms, ...signupPayload } = values; 
            const response = await signup(signupPayload);
			
			router.push({ pathname: '/(auth)/activate', params: { identifier: response.email } });
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la registrazione';
			showToast(message, 'error');
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<AuthScreenLayout title = 'Unisciti al tavolo' subtitle = 'Crea il tuo profilo e inizia a condividere.' footer = {
				<>
					<Button className = 'mt-2 h-14 rounded-full' onPress = { handleSubmit(onSubmit) } disabled = { !isValid || isSubmitting }>
						<Text className = 'text-base font-semibold text-primary-foreground'>
							{isSubmitting ? 'Registrazione...' : 'Crea account'}
						</Text>
					</Button>
					<Text className = 'text-sm text-muted-foreground mt-3 text-center'>
						Hai già un account? <Text className = 'text-sm font-semibold text-primary active:underline' onPress = { () => router.push('/(auth)/login') }>Accedi</Text>
					</Text>
				</>
			}
		>
			<Controller control = { control } name = 'username'
				render = { ({ field: { onChange, onBlur, value } }) => (
					<AuthField label = 'Username giocatore' placeholder = 'es. MeepleKing99' autoCapitalize = 'none' value = { value } onChangeText = { onChange } onBlur = { onBlur } error = { errors.username?.message }/>
				)}
			/>
			<Controller
				control = { control }
				name = 'email'
				render = { ({ field: { onChange, onBlur, value } }) => (
					<AuthField label = 'Email' placeholder = 'nome@esempio.it' autoCapitalize = 'none' keyboardType = 'email-address' value = { value } onChangeText = { onChange } onBlur = { onBlur } error = { errors.email?.message }/>
				)}
			/>
			<View className = 'mb-4'>
				<Text className = 'mb-1.5 text-sm font-medium text-foreground'>Password</Text>
				<Controller control = { control } name = 'password'
					render = { ({ field: { onChange, onBlur, value } }) => (
						<PasswordInput placeholder = 'Almeno 8 caratteri' value = { value } onChangeText = { onChange } onBlur = { onBlur }/>
					)}
				/>
				{errors.password && (
					<Text className = 'mt-1 text-xs text-destructive'>{errors.password.message}</Text>
				)}
			</View>
			<View className = 'mb-2'>
				<Text className = 'mb-1.5 text-sm font-medium text-foreground'>Conferma password</Text>
				<Controller
					control = { control }
					name = 'confirmPassword'
					render = { ({ field: { onChange, onBlur, value } }) => (
						<PasswordInput placeholder = 'Ripeti la password' value = { value } onChangeText = { onChange } onBlur = { onBlur }/>
					)}
				/>
				{errors.confirmPassword && (
					<Text className = 'mt-1 text-xs text-destructive'>{errors.confirmPassword.message}</Text>
				)}
			</View>
			<View className = 'mb-2 mt-2'>
                <Controller
                    control = { control }
                    name = 'acceptTerms'
                    render = { ({ field: { onChange, value } }) => (
                        <View className  ='flex-row items-center space-x-2'>
                            <Pressable onPress = { () => onChange(!value) } className = { `h-5 w-5 rounded border items-center justify-center mr-2 ${value ? 'bg-primary border-primary' : 'border-input bg-background'}` }>
                                {value && <Check size = { 14 } className = 'text-primary-foreground' color = '#ffffff'/>}
                            </Pressable>
                            <Text className = 'text-xs text-muted-foreground flex-1'>
                                Ho letto e accetto i{' '}
                                <Text className = 'text-xs font-semibold text-primary active:underline' onPress = { openTerms }>
                                    Termini e Condizioni
                                </Text>
                            </Text>
                        </View>
                    )}
                />
                {errors.acceptTerms && (
                    <Text className = 'mt-1 text-xs text-destructive'>{errors.acceptTerms.message}</Text>
                )}
            </View>
		</AuthScreenLayout>
	)
}

export default SignupScreen;