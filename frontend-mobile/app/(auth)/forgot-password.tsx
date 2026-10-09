import { useLocalSearchParams } from 'expo-router';

import AuthScreenLayout from '@/components/auth/auth-screen-layout';
import ForgotPasswordForm from '@/components/auth/forgot-password-form';

import { useNavigationStack } from '@/contexts/navigation-stack-context';

const ForgotPasswordScreen = () => {
	const { identifier } = useLocalSearchParams<{ identifier?: string }>();
	const router = useNavigationStack();

	return <ForgotPasswordForm Layout = { AuthScreenLayout } initialIdentifier = { identifier } onSuccess = { () => router.back() }/>;
}

export default ForgotPasswordScreen;