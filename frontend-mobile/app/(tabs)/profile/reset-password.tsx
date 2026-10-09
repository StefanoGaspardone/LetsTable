import { ScrollView, View } from 'react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import ForgotPasswordForm, { ForgotPasswordLayoutProps } from '@/components/auth/forgot-password-form';

import { useAuth } from '@/contexts/auth-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

const ResetPasswordLayout = ({ title, subtitle, footer, children }: ForgotPasswordLayoutProps) => (
	<View className = 'flex-1 bg-background'>
		<ScreenHeader title = 'Reimposta password' leftElement = { <BackButton/> }/>
		<ScrollView keyboardShouldPersistTaps = 'handled' showsVerticalScrollIndicator = { false } contentContainerStyle = {{ paddingBottom: 28 }} className = 'flex-1 px-4 pt-4'>
			<Text className = 'mb-1 font-display text-2xl text-foreground'>{ title }</Text>
			<Text className = 'mb-6 text-sm text-muted-foreground'>{ subtitle }</Text>
			{ children }
			{ footer }
		</ScrollView>
	</View>
)

const ResetPasswordScreen = () => {
	const { user } = useAuth();
	const router = useNavigationStack();

	return <ForgotPasswordForm Layout = { ResetPasswordLayout } initialIdentifier = { user?.email } autoSend onSuccess = { () => router.back() }/>;
}

export default ResetPasswordScreen;