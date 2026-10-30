import { useState } from 'react';
import * as ImagePicker from 'expo-image-picker';

import { deleteAvatar, uploadAvatar } from '@/api/avatar';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';

import { useUpdateMe } from '@/hooks/use-user';

const getErrorMessage = (error: any, fallback: string): string => error?.response?.data?.message ?? fallback;

export const useAvatarActions = () => {
	const { user, updateUser } = useAuth();
	const { showToast } = useToast();
	const { confirm } = useConfirmDialog();

	const updateMe = useUpdateMe(updateUser);

	const [isUploading, setIsUploading] = useState(false);
	const [isRemoving, setIsRemoving] = useState(false);

	const changeAvatar = async () => {
		const result = await ImagePicker.launchImageLibraryAsync({
			mediaTypes: ['images'],
			allowsEditing: true,
			aspect: [1, 1],
			quality: 0.8,
		});

		if(result.canceled) return;

		const asset = result.assets[0];
		setIsUploading(true);

		try {
			const uploaded = await uploadAvatar(asset.uri, asset.fileName ?? 'avatar.jpg', asset.mimeType ?? 'image/jpeg');
			await updateMe.mutateAsync({ avatarId: uploaded.id });

			showToast('Avatar aggiornato', 'success');
		} catch(error) {
			showToast(getErrorMessage(error, 'Errore durante il caricamento dell\'immagine'), 'error');
		} finally {
			setIsUploading(false);
		}
	}

	const removeAvatar = async () => {
		if(!user?.avatarId) return;

		const ok = await confirm({
			title: 'Rimuovi avatar',
			message: 'Vuoi rimuovere la tua immagine del profilo?',
			confirmLabel: 'Rimuovi',
			destructive: true,
		});

		if(!ok) return;

		setIsRemoving(true);

		try {
			await deleteAvatar(user.avatarId);
			await updateMe.mutateAsync({ removeAvatar: true });

			showToast('Avatar rimosso', 'success');
		} catch(error) {
			showToast(getErrorMessage(error, 'Errore durante la rimozione dell\'avatar'), 'error');
		} finally {
			setIsRemoving(false);
		}
	}

	return { changeAvatar, removeAvatar, isUploading, isRemoving };
}