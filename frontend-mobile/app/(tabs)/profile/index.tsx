import { useRef } from 'react';
import { View, ScrollView, Pressable, Switch } from 'react-native';
import { router } from 'expo-router';
import { Image } from 'expo-image';
import { Bell, Moon, Shield, LogOut, ChevronRight, Camera, Users, Trophy, Gamepad2, Edit3 } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import ThemePickerSheet, { ThemePickerSheetRef } from '@/components/common/theme-picker-sheet';

import { useAuth } from '@/contexts/auth-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';
import { useTheme } from '@/contexts/theme-context';

import { useHomeStats } from '@/hooks/use-stat';
import { useFriends } from '@/hooks/use-friend';
import { useUpdateMe } from '@/hooks/use-user';

const ProfileScreen = () => {
	const { user, logout, updateUser } = useAuth();
	const { confirm } = useConfirmDialog();
	const { themePreference } = useTheme();
	const themeSheetRef = useRef<ThemePickerSheetRef>(null);

	const { totalMatches, totalWins } = useHomeStats();
	const { data: friends } = useFriends();

	const updateMe = useUpdateMe(updateUser);
	const notificationsEnabled = user?.notificationsEnabled ?? true;

	const handleToggleNotifications = (value: boolean) => {
		updateMe.mutate({ notificationsEnabled: value });
	}

	const initial = user?.username ? user.username.charAt(0).toUpperCase() : '?';

	const handleLogout = async () => {
		const ok = await confirm({
			title: 'Disconnetti',
			message: 'Sei sicuro di volerti disconnettere?',
			confirmLabel: 'Disconnetti',
			destructive: true,
		});

		if(!ok) return;

		await logout();
		router.replace('/(auth)/welcome');
	}

	const themeLabel = themePreference === 'light' ? 'Chiaro' : themePreference === 'dark' ? 'Scuro' : 'Sistema';

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Profilo'/>
			<ScrollView showsVerticalScrollIndicator = { false } contentContainerStyle = {{ paddingBottom: 40 }} className = 'flex-1 px-4 pt-2'>
				<View className = 'items-center justify-center pb-4'>
					<View className = 'relative mb-3'>
						<View className = 'h-28 w-28 items-center justify-center overflow-hidden rounded-full border-2 border-border/50 bg-secondary shadow-sm'>
							{user?.avatarUrl ? (
								<Image source = {{ uri: user?.avatarUrl }} style = {{ width: 112, height: 112 }} contentFit = 'cover'/>
							) : (
								<Text className = 'text-4xl font-bold text-muted-foreground'>{initial}</Text>
							)}
						</View>
						<Pressable onPress = { () => {} } className = 'absolute bottom-0 right-0 h-9 w-9 items-center justify-center rounded-full border-2 border-background bg-[#C45135] shadow-md active:opacity-80'>
							<Camera size = { 16 } color = '#FFFFFF'/>
						</Pressable>
					</View>
					<Text className = 'text-xl font-bold text-foreground'>{user?.username}</Text>
					<Text className = 'text-xs text-muted-foreground'>{user?.email}</Text>
				</View>
				<View className = 'mb-6 rounded-2xl border border-border bg-white p-3 shadow-sm'>
					<View className = 'flex-row items-center justify-around'>
						<View className = 'flex-1 items-center px-2'>
							<View className = 'mb-1 flex-row items-center gap-1.5'>
								<Users size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
								<Text className = 'font-medium text-xs uppercase tracking-wider text-muted-foreground'>
									Amici
								</Text>
							</View>
							<Text className = 'font-display text-lg'>{friends?.length ?? 0}</Text>
						</View>
						<View className = 'h-8 w-[1px] bg-border'/>
						<View className = 'flex-1 items-center px-2'>
							<View className = 'mb-1 flex-row items-center gap-1.5'>
								<Gamepad2 size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
								<Text className = 'font-medium text-xs uppercase tracking-wider text-muted-foreground'>
									Partite
								</Text>
							</View>
							<Text className = 'font-display text-lg'>{totalMatches}</Text>
						</View>
						<View className = 'h-8 w-[1px] bg-border'/>
						<View className = 'flex-1 items-center px-2'>
							<View className = 'mb-1 flex-row items-center gap-1.5'>
								<Trophy size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
								<Text className = 'font-medium text-xs uppercase tracking-wider text-muted-foreground'>
									Vittorie
								</Text>
							</View>
							<Text className = 'font-display text-lg'>{totalWins}</Text>
						</View>
					</View>
				</View>
				<Text className = 'mb-2 pl-2 text-xs font-bold uppercase tracking-wider text-muted-foreground'>
					Account
				</Text>
				<View className = 'mb-6 overflow-hidden rounded-2xl border border-border/50 bg-card shadow-sm'>
					<Pressable onPress = { () => router.push('/profile/account') } className = 'flex-row items-center justify-between border-b border-border/40 p-3.5 active:bg-secondary/40'>
						<View className = 'flex-row items-center gap-3'>
							<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-[#C45135]/15'>
								<Edit3 size = { 17 } color = '#C45135'/>
							</View>
							<Text className = 'text-sm font-medium text-foreground'>Modifica Profilo</Text>
						</View>
						<ChevronRight size = { 18 } color = '#8E8E93'/>
					</Pressable>
					<Pressable onPress = { () => router.push('/profile/security') } className = 'flex-row items-center justify-between p-3.5 active:bg-secondary/40'>
						<View className = 'flex-row items-center gap-3'>
							<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-[#C45135]/15'>
								<Shield size = { 17 } color = '#C45135'/>
							</View>
							<Text className = 'text-sm font-medium text-foreground'>Sicurezza & Password</Text>
						</View>
						<ChevronRight size = { 18 } color = '#8E8E93'/>
					</Pressable>
				</View>
				<Text className = 'mb-2 pl-2 text-xs font-bold uppercase tracking-wider text-muted-foreground'>
					Preferenze
				</Text>
				<View className = 'mb-6 overflow-hidden rounded-2xl border border-border/50 bg-card shadow-sm'>
					<View className = 'flex-row items-center justify-between border-b border-border/40 p-3.5'>
						<View className = 'flex-row items-center gap-3'>
							<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-[#C45135]/15'>
								<Bell size = { 17 } color = '#C45135'/>
							</View>
							<Text className = 'text-sm font-medium text-foreground'>Notifiche Push</Text>
						</View>
						<Switch value = { notificationsEnabled } onValueChange = { handleToggleNotifications } trackColor = {{ false: '#DDD8CE', true: '#F2EFE9' }} thumbColor = { notificationsEnabled ? '#C45135' : '#f4f3f4' } ios_backgroundColor = '#DDD8CE'/>
					</View>
					<Pressable onPress = { () => themeSheetRef.current?.present() } className = 'flex-row items-center justify-between p-3.5 active:bg-secondary/40'>
						<View className = 'flex-row items-center gap-3'>
							<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-[#C45135]/15'>
								<Moon size = { 17 } color = '#C45135'/>
							</View>
							<Text className = 'text-sm font-medium text-foreground'>Tema</Text>
						</View>
						<View className = 'flex-row items-center gap-1'>
							<Text className = 'text-xs text-muted-foreground'>{themeLabel}</Text>
							<ChevronRight size = { 16 } color = '#8E8E93'/>
						</View>
					</Pressable>
				</View>
				<View className = 'mb-4 overflow-hidden rounded-2xl border border-border/50 bg-card shadow-sm'>
					<Pressable className = 'flex-row items-center justify-between p-3.5 active:bg-red-500/10' onPress = { handleLogout }>
						<View className = 'flex-row items-center gap-3'>
							<View className = 'h-8 w-8 items-center justify-center rounded-xl bg-red-500/10'>
								<LogOut size = { 17 } color = '#EF4444'/>
							</View>
							<Text className = 'text-sm font-semibold text-red-500'>Disconnetti</Text>
						</View>
					</Pressable>
				</View>
				<Text className = 'mt-2 text-center text-xs text-muted-foreground/60'>
					Versione 1.0.0
				</Text>
			</ScrollView>
			<ThemePickerSheet ref = { themeSheetRef }/>
		</View>
	)
}

export default ProfileScreen;