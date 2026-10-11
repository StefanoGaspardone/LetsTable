import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { BottomSheetModal, BottomSheetScrollView } from '@gorhom/bottom-sheet';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import AppBottomSheet from '@/components/common/app-bottom-sheet';

export interface RenameWishlistSheetRef {
	present: (currentName: string) => void;
	dismiss: () => void;
}

interface RenameWishlistSheetProps {
	isSubmitting: boolean;
	onSubmit: (name: string) => void;
}

const MAX_NAME_LENGTH = 100;

const RenameWishlistSheet = forwardRef<RenameWishlistSheetRef, RenameWishlistSheetProps>(({ isSubmitting, onSubmit }, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);

	const [originalName, setOriginalName] = useState('');
	const [name, setName] = useState('');

	useImperativeHandle(ref, () => ({
		present: (currentName) => {
			setOriginalName(currentName);
			setName(currentName);
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const trimmed = name.trim();
	const canSubmit = !isSubmitting && trimmed.length > 0 && trimmed !== originalName.trim();

	const handleSubmit = () => {
		if(!canSubmit) return;
		onSubmit(trimmed);
	}

	return (
		<AppBottomSheet ref = { sheetRef }>
			<BottomSheetScrollView contentContainerStyle = {{ padding: 16, paddingBottom: 32 }} keyboardShouldPersistTaps = 'handled'>
				<Text className = 'mb-4 font-display text-xl text-foreground'>Rinomina Wishlist</Text>
				<Text className = 'mb-1.5 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Nome</Text>
				<Input value = { name } onChangeText = { setName } maxLength = { MAX_NAME_LENGTH } placeholder = 'Es. Giochi da provare' returnKeyType = 'done' onSubmitEditing = { handleSubmit } className = 'mb-6 h-11 border-0'/>
				<Button className = 'h-14 rounded-full' onPress = { handleSubmit } disabled = { !canSubmit }>
					<Text className = 'text-base font-semibold text-primary-foreground'>
						{isSubmitting ? 'Salvataggio...' : 'Salva'}
					</Text>
				</Button>
			</BottomSheetScrollView>
		</AppBottomSheet>
	)
});

RenameWishlistSheet.displayName = 'RenameWishlistSheet';

export default RenameWishlistSheet;