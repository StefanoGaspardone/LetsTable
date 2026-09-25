import { useEffect, useRef, useState } from 'react';
import { View, ScrollView, Pressable, ActivityIndicator } from 'react-native';
import { RefreshControl } from 'react-native-gesture-handler';
import { useLocalSearchParams } from 'expo-router';
import { Image } from 'expo-image';
import { Heart, Users, Lock, Dices, UserPlus, Trash2, LogOut, X } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import FabMenu from '@/components/common/fab-menu';
import ComingSoon from '@/components/common/coming-soon';
import SegmentedControl from '@/components/common/segmented-control';
import GamePickerSheet, { GamePickerSheetRef } from '@/components/common/game-picker-sheet';
import WishlistMemberPickerSheet, { WishlistMemberPickerSheetRef } from '@/components/common/wishlist-member-picker-sheet';

import { useWishlist, useWishlistItems, useWishlistMembers, useAddItemToWishlist, useRemoveItemFromWishlist, useAddMemberToWishlist, useRemoveMemberFromWishlist, useDeleteWishlist, useLeaveWishlist } from '@/hooks/use-wishlist';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { getAvatarUrl } from '@/lib/file';
import { formatFullDate } from '@/lib/date';

const TAB_OPTIONS = [
	{ value: 'games', label: 'Giochi' },
	{ value: 'members', label: 'Membri' },
]

const WishlistDetailScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const router = useNavigationStack();
	const { user } = useAuth();
	const { showToast } = useToast();
	const { confirm } = useConfirmDialog();

	const { colors } = useThemeColors();

	const gamePickerRef = useRef<GamePickerSheetRef>(null);
	const memberPickerRef = useRef<WishlistMemberPickerSheetRef>(null);

	const [isDeleting, setIsDeleting] = useState(false);
	const [tab, setTab] = useState<'games' | 'members'>('games');

	const { data: wishlist, isLoading: isLoadingWishlist } = useWishlist(id);
	const { data: itemsData, isLoading: isLoadingItems, fetchNextPage, hasNextPage, isFetchingNextPage, refetch: refetchItems } = useWishlistItems(id);
	const { data: members, refetch: refetchMembers } = useWishlistMembers(id, !!wishlist?.isShared);

	useRefetchOnFocus(['wishlists', 'detail', id]);
	useRefetchOnFocus(['wishlists', 'items', id]);
	const { refreshing, onRefresh } = usePullToRefresh([['wishlists', 'detail', id], ['wishlists', 'items', id]]);

	useEffect(() => {
		if(tab === 'games') {
			refetchItems();
		} else if(tab === 'members') {
			refetchMembers();
		}
	}, [tab]);

	const addItem = useAddItemToWishlist(id);
	const removeItem = useRemoveItemFromWishlist(id);
	const addMember = useAddMemberToWishlist(id);
	const removeMember = useRemoveMemberFromWishlist(id);
	const deleteWishlist = useDeleteWishlist();
	const leaveWishlist = useLeaveWishlist();

	const items = itemsData?.pages.flatMap(page => page.content) ?? [];
	const isOwner = wishlist?.owner.id === user?.id;
	const isMember = !isOwner && (members ?? []).some(m => m.user.id === user?.id);
	const canEdit = isOwner || isMember;

	const handleAddGame = (game: { id: string }) => {
		addItem.mutate(game.id, {
			onError: (error: any) => {
				const message = error?.response?.data?.message ?? 'Errore durante l\'aggiunta';
				showToast(message, 'error');
			},
		});
	}

	const handleRemoveItem = (itemId: string) => {
		removeItem.mutate(itemId, {
			onError: (error: any) => {
				const message = error?.response?.data?.message ?? 'Errore durante la rimozione';
				showToast(message, 'error');
			},
		});
	}

	const handleInviteMembers = (userIds: string[]) => {
		userIds.forEach(userId => {
			addMember.mutate(userId, {
				onError: (error: any) => {
					const message = error?.response?.data?.message ?? 'Errore durante l\'invito';
					showToast(message, 'error');
				},
			});
		});
	}

	const handleRemoveMember = async (memberUserId: string, memberName: string) => {
		const ok = await confirm({
			title: 'Rimuovi membro',
			message: `Vuoi rimuovere ${memberName} da questa wishlist?`,
			confirmLabel: 'Rimuovi',
			destructive: true,
		});

		if(!ok) return;

		removeMember.mutate(memberUserId);
	}

	const handleDelete = async () => {
		const ok = await confirm({
			title: 'Elimina wishlist',
			message: 'Vuoi eliminare questa wishlist? L\'azione è irreversibile.',
			confirmLabel: 'Elimina',
			destructive: true,
		});

		if(!ok) return;

		setIsDeleting(true);

		try {
			await deleteWishlist.mutateAsync(id);

			router.push('/(tabs)/my-wishlists');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante l\'eliminazione';

			showToast(message, 'error');
			setIsDeleting(false);
		}
	}

	const handleLeave = async () => {
		const ok = await confirm({
			title: 'Abbandona wishlist',
			message: 'Vuoi uscire da questa wishlist condivisa?',
			confirmLabel: 'Esci',
			destructive: true,
		});

		if(!ok) return;

		try {
			await leaveWishlist.mutateAsync(id);

			router.push('/(tabs)/my-wishlists');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante l\'operazione';
			showToast(message, 'error');
		}
	}

	if(isLoadingWishlist || !wishlist) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = { colors.primary }/>
			</View>
		)
	}

	const showTabs = wishlist.isShared;
	const activeTab = showTabs ? tab : 'games';

	const fabActions = [
		...(isOwner && !wishlist.isDefault ? [{
			label: 'Elimina wishlist',
			icon: <Trash2 size = { 18 }/>,
			onPress: handleDelete,
		}] : []),
		...(isMember ? [{
			label: 'Abbandona wishlist',
			icon: <LogOut size = { 18 }/>,
			onPress: handleLeave,
		}] : []),
		...(isOwner ? [{
			label: 'Invita membri',
			icon: <UserPlus size = { 18 }/>,
			onPress: () => memberPickerRef.current?.present(),
		}] : []),
		...(canEdit ? [{
			label: 'Aggiungi gioco',
			icon: <Dices size = { 18 }/>,
			onPress: () => gamePickerRef.current?.present(),
		}] : []),
	];

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Dettagli Wishlist' leftElement = { <BackButton/> }/>
			<View className = 'px-4 pt-3'>
				<View className = 'mb-3 flex-row items-center justify-between gap-3'>
					<Text className = 'flex-1 font-display text-2xl text-foreground'>{wishlist.name}</Text>
					<View className = 'flex-row items-center gap-1 rounded-full bg-primary/10 px-3 py-2'>
						{wishlist.isDefault ? (
							<Heart size = { 16 } color = { colors.primary } strokeWidth = { 2.5 }/>
						) : wishlist.isShared ? (
							<Users size = { 13 } color = { colors.primary } strokeWidth = { 2.5 }/>
						) : (
							<Lock size = { 13 } color = { colors.primary } strokeWidth = { 2.5 }/>
						)}
						<Text className = 'text-sm font-medium text-primary'>
							{wishlist.isDefault ? 'Principale' : wishlist.isShared ? 'Condivisa' : 'Privata'}
						</Text>
					</View>
				</View>
				<View className = 'mb-4 flex-row items-center justify-between gap-4'>
					<Pressable className = 'flex-row items-center gap-1.5 group' onPress = { () => router.push(wishlist.owner.id === user?.id ? `/user/${wishlist.owner.id}` : `/profile`) }>
						<Image source = {{ uri: getAvatarUrl(wishlist.owner.avatarId ?? null, wishlist.owner.username) }} style = {{ width: 24, height: 24, borderRadius: 100 }} contentFit = 'cover'/>
						<Text className = 'text-xs font-medium text-muted-foreground group-active:underline'>{wishlist.owner.username}</Text>
					</Pressable>
					<Text className = 'text-xs text-muted-foreground'>Aggiornata il {formatFullDate(wishlist.createdAt)}</Text>
				</View>
				{showTabs && (
					<View className = 'mb-3'>
						<SegmentedControl options = { TAB_OPTIONS } selected = { activeTab } onSelect = { value => setTab(value as 'games' | 'members') }/>
					</View>
				)}
			</View>
			<ScrollView className = 'flex-1' contentContainerStyle = {{ padding: 16, paddingTop: 8, paddingBottom: 100, flexGrow: 1 }} refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/> }>
				{activeTab === 'games' ? (
					isLoadingItems ? (
						<View className = 'flex-1 items-center justify-center'>
							<ActivityIndicator color = { colors.primary }/>
						</View>
					) : items.length > 0 ? (
						<View className = 'gap-2'>
							{items.map(item => (
								<Pressable key = { item.id } onPress = { () => router.push(`/game/${item.game.bggId}`) } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75'>
									<View style = {{ width: 48, height: 48 }} className = 'overflow-hidden rounded-xl bg-secondary'>
										{item.game.thumbnailUrl ? (
											<Image source = {{ uri: item.game.thumbnailUrl }} style = {{ width: 48, height: 48 }} contentFit = 'cover'/>
										) : (
											<View className = 'h-full w-full items-center justify-center'>
												<Dices size = { 18 } color = { colors.mutedForeground }/>
											</View>
										)}
									</View>
									<View className = 'flex-1'>
										<Text className = 'text-sm font-medium text-foreground' numberOfLines = { 1 }>{item.game.name}</Text>
										<Text className = 'text-xs text-muted-foreground'>Aggiunto da <Text className = 'font-medium text-xs text-muted-foreground'>{item.addedBy.username}</Text></Text>
									</View>
									{canEdit && (
										<Pressable onPress = { () => handleRemoveItem(item.id) } hitSlop = { 8 } className = 'p-2'>
											<X size = { 16 } color = { colors.mutedForeground }/>
										</Pressable>
									)}
								</Pressable>
							))}
							{hasNextPage && (
								<Pressable onPress = { () => { if(!isFetchingNextPage) fetchNextPage(); } } className = 'items-center py-3'>
									{isFetchingNextPage ? (
										<ActivityIndicator color = { colors.primary }/>
									) : (
										<Text className = 'text-sm font-medium text-primary'>Carica altri</Text>
									)}
								</Pressable>
							)}
						</View>
					) : (
						<View className = 'flex-1 items-center justify-center'>
							<ComingSoon icon = { <Dices size = { 40 } color = { colors.primary }/> } title = 'Nessun gioco' subtitle = 'Aggiungi il primo gioco con il pulsante qui sotto.'/>
						</View>
					)
				) : (
					<View className = 'gap-2'>
						<View className = 'flex-row items-center gap-2.5 rounded-xl border border-border bg-card px-3 py-2.5'>
							<Image source = {{ uri: getAvatarUrl(wishlist.owner.avatarId ?? null, wishlist.owner.username) }} style = {{ width: 36, height: 36, borderRadius: 100 }} contentFit = 'cover'/>
							<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{wishlist.owner.username}</Text>
							<Text className = 'text-xs font-medium text-muted-foreground'>Proprietario</Text>
						</View>
						{(members ?? []).map(member => (
							<View key = { member.id } className = 'flex-row items-center gap-2.5 rounded-xl border border-border bg-card px-3 py-2.5'>
								<Image source = {{ uri: getAvatarUrl(member.user.avatarId ?? null, member.user.username) }} style = {{ width: 36, height: 36, borderRadius: 100 }} contentFit = 'cover'/>
								<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{member.user.username}</Text>
								{isOwner && (
									<Pressable onPress = { () => handleRemoveMember(member.user.id, member.user.username) } hitSlop = { 8 }>
										<X size = { 16 } color = { colors.mutedForeground }/>
									</Pressable>
								)}
							</View>
						))}
					</View>
				)}
			</ScrollView>
			{fabActions.length > 0 && (
				<FabMenu actions = { fabActions }/>
			)}
			<GamePickerSheet ref = { gamePickerRef } onSelect = { handleAddGame } onBack = { () => gamePickerRef.current?.dismiss() }/>
			<WishlistMemberPickerSheet ref = { memberPickerRef } excludeUserIds = { [wishlist.owner.id, ...(members ?? []).map(m => m.user.id)] } onConfirm = { handleInviteMembers } onBack = { () => memberPickerRef.current?.dismiss() }/>
		</View>
	)
}

export default WishlistDetailScreen;