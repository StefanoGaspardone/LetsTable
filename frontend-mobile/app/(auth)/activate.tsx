import { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AuthScreenLayout from '@/components/auth/auth-screen-layout';
import OtpInput from '@/components/common/otp-input';

import { useToast } from '@/contexts/toast-context';

import { activate, resendActivationOtp } from '@/api/auth';

const ActivateScreen = () => {
	const { identifier } = useLocalSearchParams<{ identifier: string }>();
	const { showToast } = useToast();

	const [otpCode, setOtpCode] = useState('');
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [isResending, setIsResending] = useState(false);

	const isComplete = otpCode.length === 6;

	const onSubmit = async () => {
		setIsSubmitting(true);
		
		try {
			await activate(identifier, otpCode);
			showToast('Account attivato con successo', 'success');
			
			router.replace('/(auth)/login');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Codice non valido o scaduto';
			showToast(message, 'error');
		} finally {
			setIsSubmitting(false);
		}
	}

	const onResend = async () => {
		setIsResending(true);
		
		try {
			await resendActivationOtp(identifier);
			showToast('Codice inviato, controlla la tua email', 'success');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Impossibile inviare un nuovo codice';
			showToast(message, 'error');
		} finally {
			setIsResending(false);
		}
	}

	return (
		<AuthScreenLayout title = 'Verifica il tuo account' subtitle = { `Abbiamo inviato un codice a ${identifier}` }
			footer = {
				<Button variant = 'ghost' className = 'mt-6' onPress = { onResend } disabled = { isResending }>
					<Text className = 'text-sm text-muted-foreground'>
						{isResending ? (
							'Invio...'
						) : (
							<>
								Non hai ricevuto il codice? <Text className = 'font-semibold text-primary'>Rinvia</Text>
							</>
						)}
					</Text>
				</Button>
			}
		>
			<OtpInput value = { otpCode } onChange = { setOtpCode }/>
			<Button className = 'mt-6 h-14 rounded-full' onPress = { onSubmit } disabled = { !isComplete || isSubmitting }>
				<Text className = 'text-base font-semibold text-primary-foreground'>
					{isSubmitting ? 'Verifica...' : 'Attiva account'}
				</Text>
			</Button>
		</AuthScreenLayout>
	)
}

export default ActivateScreen;