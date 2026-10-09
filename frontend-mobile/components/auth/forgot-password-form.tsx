import { ComponentType, ReactNode, useEffect, useRef, useState } from 'react';
import { View } from 'react-native';
import { useForm, Controller } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AuthField from '@/components/auth/auth-field';
import PasswordInput from '@/components/common/password-input';
import OtpInput from '@/components/common/otp-input';

import { useToast } from '@/contexts/toast-context';

import { ForgotPasswordFormValues, forgotPasswordSchema, ResetPasswordFormValues, resetPasswordSchema } from '@/schemas/auth-schema';

import { forgotPassword, resetPassword } from '@/api/auth';

export interface ForgotPasswordLayoutProps {
	title: string;
	subtitle: string;
	footer?: ReactNode;
	children: ReactNode;
}

interface ForgotPasswordFormProps {
	Layout: ComponentType<ForgotPasswordLayoutProps>;
	initialIdentifier?: string;
	autoSend?: boolean;
	onSuccess: () => void;
}

type Step = 'request' | 'reset';

const ForgotPasswordForm = ({ Layout, initialIdentifier, autoSend = false, onSuccess }: ForgotPasswordFormProps) => {
	const { showToast } = useToast();

	const [step, setStep] = useState<Step>('request');
	const [identifier, setIdentifier] = useState('');
	const [otpCode, setOtpCode] = useState('');
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [isResending, setIsResending] = useState(false);

	const hasAutoSent = useRef(false);

	const requestForm = useForm<ForgotPasswordFormValues>({
		resolver: zodResolver(forgotPasswordSchema),
		defaultValues: { identifier: initialIdentifier ?? '' },
		mode: 'onChange',
	});

	const resetForm = useForm<ResetPasswordFormValues>({
		resolver: zodResolver(resetPasswordSchema),
		defaultValues: { newPassword: '', confirmPassword: '' },
		mode: 'onChange',
	});

	const sendCode = async (target: string): Promise<boolean> => {
		try {
			await forgotPassword({ identifier: target });
			showToast('Se l\'account esiste, ti abbiamo inviato un codice via email', 'success');

			return true;
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Impossibile inviare il codice';
			showToast(message, 'error');

			return false;
		}
	}

	useEffect(() => {
		if(!autoSend || !initialIdentifier || hasAutoSent.current) return;

		hasAutoSent.current = true;
		setIsSubmitting(true);

		sendCode(initialIdentifier).then(sent => {
			if(sent) {
				setIdentifier(initialIdentifier);
				setStep('reset');
			}

			setIsSubmitting(false);
		});
	}, []);

	const onRequest = async (values: ForgotPasswordFormValues) => {
		setIsSubmitting(true);

		const target = values.identifier.trim();
		const sent = await sendCode(target);

		if(sent) {
			setIdentifier(target);
			setStep('reset');
		}

		setIsSubmitting(false);
	}

	const onResend = async () => {
		setIsResending(true);
		await sendCode(identifier);
		setIsResending(false);
	}

	const onReset = async (values: ResetPasswordFormValues) => {
		setIsSubmitting(true);

		try {
			await resetPassword({ identifier, otpCode, newPassword: values.newPassword });
			showToast('Password reimpostata con successo', 'success');

			onSuccess();
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Codice non valido o scaduto';
			showToast(message, 'error');

			if(error?.response?.status === 429) {
				setOtpCode('');
				resetForm.reset();
				setStep('request');
			}
		} finally {
			setIsSubmitting(false);
		}
	}

	if(step === 'request') {
		return (
			<Layout title = 'Password dimenticata' subtitle = 'Inserisci la tua email o il tuo username: ti invieremo un codice per reimpostarla.'>
				<Controller control = { requestForm.control } name = 'identifier'
					render = { ({ field: { onChange, onBlur, value } }) => (
						<AuthField label = 'Email o username' placeholder = 'boardgamer@example.com' autoCapitalize = 'none' value = { value } onChangeText = { onChange } onBlur = { onBlur } error = { requestForm.formState.errors.identifier?.message }/>
					)}
				/>
				<Button className = 'mt-4 h-14 rounded-full' onPress = { requestForm.handleSubmit(onRequest) } disabled = { !requestForm.formState.isValid || isSubmitting }>
					<Text className = 'text-base font-semibold text-primary-foreground'>
						{isSubmitting ? 'Invio in corso...' : 'Invia codice'}
					</Text>
				</Button>
			</Layout>
		)
	}

	const { errors, isValid } = resetForm.formState;

	return (
		<Layout title = 'Reimposta password' subtitle = 'Inserisci il codice che ti abbiamo inviato e scegli una nuova password.'
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
			<View className = 'mt-6'>
				<Controller control = { resetForm.control } name = 'newPassword'
					render = { ({ field: { onChange, onBlur, value } }) => (
						<View className = 'mb-2'>
							<Text className = 'mb-1.5 text-sm font-medium text-foreground'>Nuova password</Text>
							<PasswordInput placeholder = '••••••••' value = { value } onChangeText = { onChange } onBlur = { onBlur }/>
							{errors.newPassword && (
								<Text className = 'mt-1 text-xs text-destructive'>{errors.newPassword.message}</Text>
							)}
						</View>
					)}
				/>
				<Controller control = { resetForm.control } name = 'confirmPassword'
					render = { ({ field: { onChange, onBlur, value } }) => (
						<View className = 'mb-2'>
							<Text className = 'mb-1.5 text-sm font-medium text-foreground'>Conferma password</Text>
							<PasswordInput placeholder = '••••••••' value = { value } onChangeText = { onChange } onBlur = { onBlur }/>
							{errors.confirmPassword && (
								<Text className = 'mt-1 text-xs text-destructive'>{errors.confirmPassword.message}</Text>
							)}
						</View>
					)}
				/>
			</View>
			<Button className = 'mt-4 h-14 rounded-full' onPress = { resetForm.handleSubmit(onReset) } disabled = { otpCode.length !== 6 || !isValid || isSubmitting }>
				<Text className = 'text-base font-semibold text-primary-foreground'>
					{isSubmitting ? 'Salvataggio...' : 'Reimposta password'}
				</Text>
			</Button>
		</Layout>
	)
}

export default ForgotPasswordForm;