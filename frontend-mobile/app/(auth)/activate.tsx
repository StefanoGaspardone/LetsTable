import { useState } from 'react';
import { View, Alert } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { OtpInput } from '@/components/ui/otp-input';

import { activate, resendActivationOtp } from '@/api/auth';

export default function ActivateScreen() {
	const { identifier } = useLocalSearchParams<{ identifier: string }>();

	const [otpCode, setOtpCode] = useState('');
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [isResending, setIsResending] = useState(false);

	const isComplete = otpCode.length === 6;

	const onSubmit = async () => {
		setIsSubmitting(true);
		
		try {
			await activate(identifier, otpCode);
			Alert.alert('Account attivato', 'Ora puoi accedere', [
				{ text: 'OK', onPress: () => router.replace('/(auth)/login') },
			]);
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Codice non valido o scaduto';
			Alert.alert('Errore', message);
		} finally {
			setIsSubmitting(false);
		}
	}

	const onResend = async () => {
		setIsResending(true);
		
		try {
			await resendActivationOtp(identifier);
			Alert.alert('Codice inviato', 'Controlla la tua email');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Impossibile inviare un nuovo codice';
			Alert.alert('Errore', message);
		} finally {
			setIsResending(false);
		}
	}

	return (
		<View className = 'flex-1 justify-center bg-background px-6'>
			<Text className = 'mb-2 text-center font-display text-3xl text-foreground'>
				Verifica email
			</Text>
			<Text className = 'mb-8 text-center text-base text-muted-foreground'>
				Abbiamo inviato un codice a {identifier}
			</Text>
			<View className = 'mb-6'>
				<OtpInput value = { otpCode } onChange = { setOtpCode }/>
			</View>
			<Button onPress = { onSubmit } disabled = { !isComplete || isSubmitting }>
				<Text>{isSubmitting ? 'Verifica...' : 'Attiva account'}</Text>
			</Button>
			<Button variant = 'ghost' className = 'mt-4' onPress = { onResend } disabled = { isResending }>
				<Text>{isResending ? 'Invio...' : 'Non hai ricevuto il codice? Rinvia'}</Text>
			</Button>
		</View>
	)
}