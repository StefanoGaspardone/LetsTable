import { useCallback, useState } from 'react';
import { View, ScrollView, Pressable, ActivityIndicator, Alert, KeyboardAvoidingView, Platform } from 'react-native';
import { Image } from 'expo-image';
import * as ImagePicker from 'expo-image-picker';
import { useFocusEffect } from 'expo-router';
import { Camera, User, Mail, Trash2 } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import { Button } from '@/components/ui/button';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';

import { useUpdateMe } from '@/hooks/use-user';

const AccountScreen = () => {
	const { user, updateUser } = useAuth();
	const { showToast } = useToast();
	const updateMe = useUpdateMe(updateUser);

	const [username, setUsername] = useState(user?.username ?? '');
	const [avatarUri, setAvatarUri] = useState<string | null>(user?.avatarUrl ?? null);

	useFocusEffect(
		useCallback(() => {
			setUsername(user?.username ?? '');
			setAvatarUri(user?.avatarUrl ?? null);
		}, [user?.username, user?.avatarUrl])
	);

	const hasChanges = username.trim() !== user?.username;

	const handlePickImage = async () => {
		const permissionResult = await ImagePicker.requestMediaLibraryPermissionsAsync();

		if(!permissionResult.granted) {
			Alert.alert('Permesso negato', 'Serve l\'accesso alla galleria per cambiare l\'avatar.');
			return;
		}

		const result = await ImagePicker.launchImageLibraryAsync({
			mediaTypes: ['images'],
			allowsEditing: true,
			aspect: [1, 1],
			quality: 0.8,
		});

		if(!result.canceled && result.assets[0]?.uri) {
			setAvatarUri(result.assets[0].uri);
			showToast('Caricamento avatar non ancora disponibile', 'info');
		}
	}

	const handleRemoveImage = () => {
		setAvatarUri(null);
	}

	const handleSave = () => {
		const trimmed = username.trim();

		if(!trimmed) {
			showToast('Lo username non può essere vuoto', 'error');
			return;
		}

		updateMe.mutate(
			{ username: trimmed },
			{
				onSuccess: () => {
					showToast('Profilo aggiornato', 'success');
				},
				onError: (error: any) => {
					const message = error?.response?.data?.message ?? 'Errore durante l\'aggiornamento';
					showToast(message, 'error');
				},
			}
		);
	}

	const initial = username ? username.charAt(0).toUpperCase() : '?';

	return (
		<KeyboardAvoidingView behavior = { Platform.OS === 'ios' ? 'padding' : undefined } className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Modifica Profilo' leftElement = { <BackButton/> }/>
			<ScrollView showsVerticalScrollIndicator = { false } contentContainerStyle = {{ paddingBottom: 40 }} className = 'flex-1 px-4 pt-2'>
				<View className = 'mb-8 items-center justify-center'>
					<View className = 'relative'>
						<View className = 'h-28 w-28 items-center justify-center overflow-hidden rounded-full border-2 border-border/50 bg-secondary shadow-sm'>
							{avatarUri ? (
								<Image source = {{ uri: avatarUri }} style = {{ width: 112, height: 112 }} contentFit = 'cover'/>
							) : (
								<Text className = 'text-4xl font-bold text-muted-foreground'>{initial}</Text>
							)}
						</View>
						<Pressable onPress = { handlePickImage } className = 'absolute bottom-0 right-0 h-9 w-9 items-center justify-center rounded-full border-2 border-background bg-[#C45135] shadow-md active:opacity-80'>
							<Camera size = { 16 } color = '#FFFFFF'/>
						</Pressable>
					</View>
					{avatarUri && (
						<Pressable onPress = { handleRemoveImage } className = 'mt-3 flex-row items-center gap-1.5 active:opacity-60'>
							<Trash2 size = { 13 } color = '#EF4444'/>
							<Text className = 'text-xs font-medium text-red-500'>Rimuovi foto</Text>
						</Pressable>
					)}
				</View>
				<Text className = 'mb-2 pl-1 text-xs font-bold uppercase tracking-wider text-muted-foreground'>
					Informazioni Utente
				</Text>
				<View className = 'mb-8 gap-4 rounded-2xl border border-border/50 bg-card p-4 shadow-sm'>
					<View className = 'gap-1.5'>
						<Text className = 'text-xs font-semibold text-foreground'>Username</Text>
						<View className = 'relative'>
							<Input value = { username } onChangeText = { setUsername } placeholder = 'Inserisci username' className = 'h-11 rounded-xl border-0 bg-secondary/70 pl-10 text-sm' autoCapitalize = 'none'/>
							<View className = 'pointer-events-none absolute bottom-0 left-3 top-0 justify-center'>
								<User size = { 17 } color = '#8E8E93'/>
							</View>
						</View>
					</View>
					<View className = 'gap-1.5'>
						<Text className = 'text-xs font-semibold text-foreground'>Email</Text>
						<View className = 'relative'>
							<Input value = { user?.email ?? '' } editable = { false } className = 'h-11 rounded-xl border-0 bg-secondary/70 pl-10 text-sm text-muted-foreground'/>
							<View className = 'pointer-events-none absolute bottom-0 left-3 top-0 justify-center'>
								<Mail size = { 17 } color = '#8E8E93'/>
							</View>
						</View>
						<Text className = 'text-xs text-muted-foreground'>L'email non può essere modificata</Text>
					</View>
				</View>
				<Button className = 'h-12 rounded-full active:scale-[0.98]' disabled = { !hasChanges || updateMe.isPending } onPress = { handleSave }>
					{updateMe.isPending ? (
						<ActivityIndicator color = '#FFFFFF'/>
					) : (
						<Text className = 'text-sm font-semibold text-primary-foreground'>Salva e concludi</Text>
					)}
				</Button>
			</ScrollView>
		</KeyboardAvoidingView>
	)
}

export default AccountScreen;