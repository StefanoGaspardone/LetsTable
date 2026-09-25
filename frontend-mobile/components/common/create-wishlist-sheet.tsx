import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable } from 'react-native';
import { BottomSheetModal, BottomSheetScrollView } from '@gorhom/bottom-sheet';
import { Users, Lock } from 'lucide-react-native';
import { useQueryClient } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

import { createWishlist } from '@/api/wishlist';

import { useToast } from '@/contexts/toast-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { useThemeColors } from '@/hooks/use-theme-colors';

export interface CreateWishlistSheetRef {
	present: () => void;
	dismiss: () => void;
}

const CreateWishlistSheet = forwardRef<CreateWishlistSheetRef>((_, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const queryClient = useQueryClient();
	
    const { showToast } = useToast();
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const [name, setName] = useState('');
	const [isShared, setIsShared] = useState(false);
	const [isSubmitting, setIsSubmitting] = useState(false);

	useImperativeHandle(ref, () => ({
		present: () => {
			setName('');
			setIsShared(false);

			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const handleSubmit = async () => {
		if(!name.trim()) {
			showToast('Inserisci un nome per la wishlist', 'error');
			return;
		}

		setIsSubmitting(true);

		try {
			const created = await createWishlist(name.trim(), isShared);
			
            queryClient.invalidateQueries({ queryKey: ['wishlists'] });
			sheetRef.current?.dismiss();
			
            router.push(`/wishlist/${created.id}`);
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la creazione';
			showToast(message, 'error');
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<BottomSheetScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 32 }}>
				<Text className = 'mb-4 font-display text-xl text-foreground'>Nuova Wishlist</Text>
				<Text className = 'mb-1.5 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Nome</Text>
				<Input value = { name } onChangeText = { setName } placeholder = 'Es. Giochi da provare' className = 'mb-4 h-11 border-0'/>
				<Text className = 'mb-1.5 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Visibilità</Text>
				<View className = 'mb-6 flex-row gap-2'>
					<Pressable onPress = { () => setIsShared(false) } className = { `flex-1 flex-row items-center justify-center gap-2 rounded-xl border px-3 py-3 ${!isShared ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
						<Lock size = { 16 } color = { !isShared ? colors.primary : colors.mutedForeground }/>
						<Text className = { `text-sm font-medium ${!isShared ? 'text-primary' : 'text-muted-foreground'}` }>Privata</Text>
					</Pressable>
					<Pressable onPress = { () => setIsShared(true) } className = { `flex-1 flex-row items-center justify-center gap-2 rounded-xl border px-3 py-3 ${isShared ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
						<Users size = { 16 } color = { isShared ? colors.primary : colors.mutedForeground }/>
						<Text className = { `text-sm font-medium ${isShared ? 'text-primary' : 'text-muted-foreground'}` }>Condivisa</Text>
					</Pressable>
				</View>
				<Button className = 'h-14 rounded-full' onPress = { handleSubmit } disabled = { isSubmitting || !name.trim() }>
					<Text className = 'text-base font-semibold text-primary-foreground'>
						{isSubmitting ? 'Creazione...' : 'Crea Wishlist'}
					</Text>
				</Button>
			</BottomSheetScrollView>
		</AppBottomSheet>
	)
});

CreateWishlistSheet.displayName = 'CreateWishlistSheet';

export default CreateWishlistSheet;