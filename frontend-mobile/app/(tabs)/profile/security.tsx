import { ActivityIndicator, Pressable, ScrollView, View } from 'react-native';
import { ChevronRight, KeyRound, Trash2 } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';

import { useAuth } from '@/contexts/auth-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';
import { useToast } from '@/contexts/toast-context';

import { useDeleteMe } from '@/hooks/use-user';
import { useThemeColors } from '@/hooks/use-theme-colors';

const DELETE_ACCOUNT_MESSAGE = 'Verranno eliminati collezione, wishlist, amicizie, traguardi e le partite giocate da solo. Nelle partite con altri giocatori resterai come ospite con il tuo nome. L\'operazione è irreversibile.';

const SecurityScreen = () => {
	const { user, logout } = useAuth();
	const { confirm } = useConfirmDialog();
	const { showToast } = useToast();
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const deleteMe = useDeleteMe();

	const handleResetPassword = () => {
		router.push('/profile/reset-password');
	}

	const handleDeleteAccount = async () => {
		if(!user || deleteMe.isPending) return;

		const ok = await confirm({
			title: 'Elimina account',
			message: DELETE_ACCOUNT_MESSAGE,
			confirmLabel: 'Elimina',
			destructive: true,
			confirmText: user.username,
		});

		if(!ok) return;

		try {
			await deleteMe.mutateAsync();
			await logout();

			router.replace('/(auth)/welcome');
			showToast('Account eliminato', 'success');
		} catch(error: any) {
			showToast(error?.response?.data?.message ?? 'Errore durante l\'eliminazione dell\'account', 'error');
		}
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
				<Text className = 'mb-6 pl-2 text-xs text-muted-foreground'>
					Ti invieremo un codice a {user?.email} per scegliere una nuova password.
				</Text>
				<Text className = 'mb-2 pl-2 text-xs font-bold uppercase tracking-wider text-destructive'>
					Zona pericolosa
				</Text>
				<View className = 'mb-2 overflow-hidden rounded-2xl border border-destructive/30 bg-card shadow-sm'>
					<Pressable onPress = { handleDeleteAccount } disabled = { deleteMe.isPending } className = 'flex-row items-center justify-between p-3.5 active:bg-destructive/10'>
						<View className = 'flex-row items-center gap-3'>
							<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-destructive/10'>
								<Trash2 size = { 17 } color = { colors.destructive }/>
							</View>
							<Text className = 'text-sm font-medium text-destructive'>Elimina account</Text>
						</View>
						{deleteMe.isPending ? (
							<ActivityIndicator size = 'small' color = { colors.destructive }/>
						) : (
							<ChevronRight size = { 18 } color = { colors.mutedForeground }/>
						)}
					</Pressable>
				</View>
				<Text className = 'pl-2 text-xs text-muted-foreground'>
					Elimina definitivamente il tuo account e i tuoi dati. Le partite giocate con altri restano nel loro storico.
				</Text>
			</ScrollView>
		</View>
	)
}

export default SecurityScreen;