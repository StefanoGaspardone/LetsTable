import { useState } from 'react';
import { router, useLocalSearchParams } from 'expo-router';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AuthScreenLayout from '@/components/auth-screen-layout';
import OtpInput from '@/components/ui/otp-input';

import { activate, resendActivationOtp } from '@/api/auth';

import { useToast } from '@/contexts/toast-context';

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
				<Button variant = 'ghost' className = 'mt-4' onPress = { onResend } disabled = { isResending }>
					<Text className = 'text-sm text-white/70'>
						{isResending ? 'Invio...' : (
							<>Non hai ricevuto il codice? <Text className = 'text-[#C1502E]'>Rinvia</Text></>
						)}
					</Text>
				</Button>
			}
		>
			<OtpInput value = { otpCode } onChange = { setOtpCode } boxClassName = 'border-[#4A423C] bg-[#3A332E] text-white' placeholderTextColor = '#8A817A'/> 
 			<Button className = 'mt-6 h-14 rounded-full bg-[#C1502E]' onPress = { onSubmit } disabled = { !isComplete || isSubmitting }>
				<Text className = 'text-base font-semibold text-white'>
					{isSubmitting ? 'Verifica...' : 'Attiva account'}
				</Text>
			</Button>
		</AuthScreenLayout>
	)
}

export default ActivateScreen;