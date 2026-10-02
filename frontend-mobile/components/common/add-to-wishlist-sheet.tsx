import { forwardRef, useImperativeHandle, useRef, useState } from 'react';
import { View, Pressable, ActivityIndicator } from 'react-native';
import { BottomSheetModal, BottomSheetFlatList } from '@gorhom/bottom-sheet';
import { Plus } from 'lucide-react-native';
import { useQueries, useQueryClient } from '@tanstack/react-query';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AppBottomSheet from '@/components/common/app-bottom-sheet';
import CreateWishlistSheet, { CreateWishlistSheetRef } from '@/components/common/create-wishlist-sheet';
import WishlistMiniCard from '@/components/common/wishlist-mini-card';

import { addItemToWishlist, getItemStatusInWishlist, removeItemFromWishlist } from '@/api/wishlist';

import { useToast } from '@/contexts/toast-context';

import { useMyWishlists } from '@/hooks/use-wishlist';
import { useThemeColors } from '@/hooks/use-theme-colors';

export interface AddToWishlistSheetRef {
	present: (game: { id: string; name: string }) => void;
	dismiss: () => void;
}

const SHEET_SWITCH_DELAY = 300;

const AddToWishlistSheet = forwardRef<AddToWishlistSheetRef>((_, ref) => {
	const sheetRef = useRef<BottomSheetModal>(null);
	const createSheetRef = useRef<CreateWishlistSheetRef>(null);

	const queryClient = useQueryClient();

	const { showToast } = useToast();
	const { colors } = useThemeColors();

	const [game, setGame] = useState<{ id: string; name: string } | null>(null);
	const [overrides, setOverrides] = useState<Record<string, boolean>>({});
	const [isSubmitting, setIsSubmitting] = useState(false);

	const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage } = useMyWishlists();

	const wishlists = (data?.pages.flatMap(page => page.content) ?? []).sort((a, b) => Number(b.isDefault) - Number(a.isDefault));

	const statusQueries = useQueries({
		queries: wishlists.map(wishlist => ({
			queryKey: ['wishlists', 'item-status', wishlist.id, game?.id],
			queryFn: () => getItemStatusInWishlist(wishlist.id, game!.id),
			enabled: !!game,
		})),
	});

	const statusById = new Map(wishlists.map((wishlist, index) => [wishlist.id, statusQueries[index]?.data]));
	const areStatusesLoading = statusQueries.some(query => query.isLoading);

	const isChecked = (wishlistId: string) => overrides[wishlistId] ?? statusById.get(wishlistId)?.inWishlist ?? false;

	const changes = wishlists.filter(wishlist => {
		const status = statusById.get(wishlist.id);
		return status && isChecked(wishlist.id) !== status.inWishlist;
	});

	useImperativeHandle(ref, () => ({
		present: (gameToAdd) => {
			setGame(gameToAdd);
			setOverrides({});
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'item-status'] });
			sheetRef.current?.present();
		},
		dismiss: () => sheetRef.current?.dismiss(),
	}));

	const toggle = (wishlistId: string) => {
		setOverrides(prev => ({ ...prev, [wishlistId]: !isChecked(wishlistId) }));
	}

	const reopen = () => {
		setTimeout(() => sheetRef.current?.present(), SHEET_SWITCH_DELAY);
	}

	const handleOpenCreate = () => {
		sheetRef.current?.dismiss();
		setTimeout(() => createSheetRef.current?.present(), SHEET_SWITCH_DELAY);
	}

	const handleCreateBack = () => {
		createSheetRef.current?.dismiss();
		reopen();
	}

	const handleCreated = () => {
		queryClient.invalidateQueries({ queryKey: ['wishlists'] });
		reopen();
	}

	const handleConfirm = async () => {
		if(!game || changes.length === 0) return;

		setIsSubmitting(true);

		const results = await Promise.allSettled(
			changes.map(wishlist => {
				const status = statusById.get(wishlist.id)!;

				return isChecked(wishlist.id)
					? addItemToWishlist(wishlist.id, game.id)
					: removeItemFromWishlist(wishlist.id, status.itemId!);
			})
		);

		const failed = results.filter(result => result.status === 'rejected').length;

		queryClient.invalidateQueries({ queryKey: ['wishlists'] });
        queryClient.invalidateQueries({ queryKey: ['wishlists', 'preview-items'] });

		setIsSubmitting(false);

		if(failed === 0) {
			showToast('Wishlist aggiornate', 'success');
			sheetRef.current?.dismiss();
		} else {
			showToast(`${failed} modifiche non riuscite`, 'error');
		}
	}

	return (
		<>
			<AppBottomSheet ref = { sheetRef }>
				<View className = 'px-4 pt-2'>
					<Text className = 'font-display text-xl text-foreground'>Aggiungi a una wishlist</Text>
					{game && (
						<Text className = 'mb-3 mt-0.5 text-sm text-muted-foreground' numberOfLines = { 1 }>{game.name}</Text>
					)}
					<Pressable onPress = { handleOpenCreate } className = 'mb-2 flex-row items-center gap-3 rounded-xl border border-dashed border-border px-3 py-3 active:bg-secondary'>
						<Plus size = { 18 } color = { colors.primary }/>
						<Text className = 'text-sm font-medium text-primary'>Crea nuova wishlist</Text>
					</Pressable>
					<BottomSheetFlatList data = { wishlists } keyExtractor = { item => item.id } style = {{ maxHeight: 360 }} contentContainerStyle = {{ paddingBottom: 12, gap: 8 }} extraData = { overrides }
						renderItem = { ({ item }) => (
							<WishlistMiniCard wishlist = { item } selected = { isChecked(item.id) } onPress = { () => toggle(item.id) }/>
						)}
						onEndReached = { () => { if(hasNextPage && !isFetchingNextPage) fetchNextPage(); }} onEndReachedThreshold = { 0.4 }
						ListEmptyComponent = { isLoading ? (<View className = 'items-center py-6'><ActivityIndicator color = { colors.primary }/></View>) : (<Text className = 'py-4 text-center text-sm text-muted-foreground'>Non hai ancora nessuna wishlist</Text>) }
						ListFooterComponent = { isFetchingNextPage ? (<View className = 'items-center py-3'><ActivityIndicator size = 'small' color = { colors.primary }/></View>) : null }
					/>
					<Button className = 'mb-4 mt-2 h-12 rounded-full' onPress = { handleConfirm } disabled = { isSubmitting || areStatusesLoading || changes.length === 0 }>
						<Text className = 'text-sm font-semibold text-primary-foreground'>
							{isSubmitting ? 'Salvataggio...' : `Conferma${changes.length > 0 ? ` (${changes.length})` : ''}`}
						</Text>
					</Button>
				</View>
			</AppBottomSheet>
			<CreateWishlistSheet ref = { createSheetRef } onCreated = { handleCreated } onBack = { handleCreateBack }/>
		</>
	)
});

AddToWishlistSheet.displayName = 'AddToWishlistSheet';

export default AddToWishlistSheet;