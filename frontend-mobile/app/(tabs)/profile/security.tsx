import { Pressable, ScrollView, View } from 'react-native';
import { ChevronRight, KeyRound } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';

import { useAuth } from '@/contexts/auth-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useThemeColors } from '@/hooks/use-theme-colors';

const SecurityScreen = () => {
	const { user } = useAuth();
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const handleResetPassword = () => {
		router.push('/profile/reset-password');
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Sicurezza' leftElement = { <BackButton/> }/>
			<ScrollView showsVerticalScrollIndicator = { false } contentContainerStyle = {{ paddingBottom: 28 }} className = 'flex-1 px-4 pt-2'>
				<Text className = 'mb-2 pl-2 text-xs font-bold uppercase tracking-wider text-muted-foreground'>
					Password
				</Text>
				<View className = 'mb-2 overflow-hidden rounded-2xl border border-border/50 bg-card shadow-sm'>
					<Pressable onPress = { handleResetPassword } className = 'flex-row items-center justify-between p-3.5 active:bg-secondary/40'>
						<View className = 'flex-row items-center gap-3'>
							<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-primary/15'>
								<KeyRound size = { 17 } color = { colors.primary }/>
							</View>
							<Text className = 'text-sm font-medium text-foreground'>Reimposta password</Text>
						</View>
						<ChevronRight size = { 18 } color = { colors.mutedForeground }/>
					</Pressable>
				</View>
				<Text className = 'pl-2 text-xs text-muted-foreground'>
					Ti invieremo un codice a {user?.email} per scegliere una nuova password.
				</Text>
			</ScrollView>
		</View>
	)
}

export default SecurityScreen;