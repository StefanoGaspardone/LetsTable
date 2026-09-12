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
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';

import { useUpdateMe } from '@/hooks/use-user';

import { uploadAvatar, deleteAvatar } from '@/api/avatar';

import { getAvatarUrl } from '@/lib/file';

const AccountScreen = () => {
	const { user, updateUser } = useAuth();
	const { showToast } = useToast();
	const { confirm } = useConfirmDialog();
	const updateMe = useUpdateMe(updateUser);

	const [username, setUsername] = useState(user?.username ?? '');
	const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
	const [isRemovingAvatar, setIsRemovingAvatar] = useState(false);

	useFocusEffect(
		useCallback(() => {
			setUsername(user?.username ?? '');
		}, [user?.username])
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

		if(result.canceled) return;

		const asset = result.assets[0];
		setIsUploadingAvatar(true);

		try {
			const uploaded = await uploadAvatar(asset.uri, asset.fileName ?? 'avatar.jpg', asset.mimeType ?? 'image/jpeg');

			updateMe.mutate(
				{ avatarId: uploaded.id },
				{
					onSuccess: () => {
						showToast('Avatar aggiornato', 'success');
					},
					onError: (error: any) => {
						const message = error?.response?.data?.message ?? 'Errore durante l\'aggiornamento';
						showToast(message, 'error');
					},
				}
			);
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante il caricamento dell\'immagine';
			showToast(message, 'error');
		} finally {
			setIsUploadingAvatar(false);
		}
	}

	const handleRemoveImage = async () => {
		if(!user?.avatarId) return;

		const ok = await confirm({
			title: 'Rimuovi avatar',
			message: 'Vuoi rimuovere la tua immagine del profilo?',
			confirmLabel: 'Rimuovi',
			destructive: true,
		});

		if(!ok) return;

		setIsRemovingAvatar(true);

		try {
			await deleteAvatar(user.avatarId);

			updateMe.mutate(
				{ removeAvatar: true },
				{
					onSuccess: () => {
						showToast('Avatar rimosso', 'success');
					},
					onError: (error: any) => {
						const message = error?.response?.data?.message ?? 'Errore durante l\'aggiornamento del profilo';
						showToast(message, 'error');
					},
				}
			);
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la rimozione dell\'avatar';
			showToast(message, 'error');
		} finally {
			setIsRemovingAvatar(false);
		}
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

	return (
		<KeyboardAvoidingView behavior = { Platform.OS === 'ios' ? 'padding' : undefined } className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Modifica Profilo' leftElement = { <BackButton/> }/>
			<ScrollView showsVerticalScrollIndicator = { false } contentContainerStyle = {{ paddingBottom: 40 }} className = 'flex-1 px-4 pt-2'>
				<View className = 'mb-8 items-center justify-center'>
					<View className = 'relative'>
						<View className = 'h-28 w-28 items-center justify-center overflow-hidden rounded-full border-2 border-border/50 bg-secondary shadow-sm'>
							<Image source = {{ uri: getAvatarUrl(user?.avatarId ?? null, user?.username ?? '') }} style = {{ width: 112, height: 112 }} contentFit = 'cover'/>
						</View>
						<Pressable onPress = { handlePickImage } disabled = { isUploadingAvatar } className = 'absolute bottom-0 left-0 h-9 w-9 items-center justify-center rounded-full border-2 border-background bg-[#C45135] shadow-md active:opacity-80'>
							{isUploadingAvatar ? (
								<ActivityIndicator size = 'small' color = '#FFFFFF'/>
							) : (
								<Camera size = { 16 } color = '#FFFFFF'/>
							)}
						</Pressable>
						{user?.avatarId && (
							<Pressable onPress = { handleRemoveImage } disabled = { isRemovingAvatar } className = 'absolute bottom-0 right-0 h-9 w-9 items-center justify-center rounded-full border-2 border-background bg-red-500 shadow-md active:opacity-80'>
								{isRemovingAvatar ? (
									<ActivityIndicator size = 'small' color = '#FFFFFF'/>
								) : (
									<Trash2 size = { 16 } color = '#FFFFFF'/>
								)}
							</Pressable>
						)}
					</View>
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