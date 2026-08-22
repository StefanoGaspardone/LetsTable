import { useState } from 'react';
import { View, FlatList, ActivityIndicator, Alert, Pressable } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { ChevronLeft, Users, Lock, Heart, LogOut, Trash2 } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import AddMemberDialog from '@/components/add-member-dialog';
import WishlistItemRow from '@/components/wishlist-item-row';

import { useWishlist, useWishlistItems, useWishlistMembers } from '@/hooks/use-wishlist';

import { useAuth } from '@/contexts/auth-context';

import { addMemberToWishlist, deleteWishlist, leaveWishlist, removeItemFromWishlist, removeMemberFromWishlist } from '@/api/wishlist';

const WishlistDetailScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();

	const { user } = useAuth();
	const queryClient = useQueryClient();

	const [showMembers, setShowMembers] = useState(false);

	const { data: wishlist, isLoading: isLoadingWishlist } = useWishlist(id);
	const { data: itemsData, fetchNextPage, hasNextPage, isFetchingNextPage, isLoading: isLoadingItems } = useWishlistItems(id);
	const { data: members } = useWishlistMembers(id, showMembers && !!wishlist?.isShared);

	const items = itemsData?.pages.flatMap((page) => page.content) ?? [];
	const isOwner = wishlist?.owner.id === user?.id;

	const handleRemoveItem = async (itemId: string) => {
		try {
			await removeItemFromWishlist(id, itemId);
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'items', id] });
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la rimozione';
			Alert.alert('Errore', message);
		}
	}

	const handleAddMember = async (userId: string) => {
		try {
			await addMemberToWishlist(id, userId);
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'members', id] });
			
            Alert.alert('Aggiunto', 'Membro aggiunto alla wishlist');
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante l\'aggiunta';
			Alert.alert('Errore', message);
		}
	}

	const handleRemoveMember = async (memberUserId: string) => {
		try {
			await removeMemberFromWishlist(id, memberUserId);
			queryClient.invalidateQueries({ queryKey: ['wishlists', 'members', id] });
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore durante la rimozione';
			Alert.alert('Errore', message);
		}
	}

	const handleDeleteWishlist = () => {
		Alert.alert('Elimina wishlist', `Vuoi eliminare '${wishlist?.name}'? L'azione è irreversibile.`, [
			{ text: 'Annulla', style: 'cancel' },
			{
				text: 'Elimina',
				style: 'destructive',
				onPress: async () => {
					try {
						await deleteWishlist(id);
						queryClient.invalidateQueries({ queryKey: ['wishlists'] });
						
                        router.back();
					} catch(error: any) {
						const message = error?.response?.data?.message ?? 'Errore durante l\'eliminazione';
						Alert.alert('Errore', message);
					}
				},
			},
		]);
	}

	const handleLeaveWishlist = async () => {
		try {
			await leaveWishlist(id);
			queryClient.invalidateQueries({ queryKey: ['wishlists'] });
			
            router.back();
		} catch(error: any) {
			const message = error?.response?.data?.message ?? 'Errore';
			Alert.alert('Errore', message);
		}
	}

	if(isLoadingWishlist || !wishlist) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator/>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<View className = 'flex-row items-center gap-3 px-4 pb-3 pt-14'>
				<Button variant = 'ghost' size = 'icon' onPress = { () => router.back() }>
					<ChevronLeft size = { 22 } className = 'text-foreground'/>
				</Button>
				<View className = 'flex-1 flex-row items-center gap-2'>
					{wishlist.isDefault ? (
						<Heart size = { 18 } className = 'text-accent'/>
					) : wishlist.isShared ? (
						<Users size = { 18 } className = 'text-muted-foreground'/>
					) : (
						<Lock size = { 18 } className = 'text-muted-foreground'/>
					)}
					<Text className = 'font-display text-xl text-foreground' numberOfLines = { 1 }>
						{wishlist.name}
					</Text>
				</View>
				{!wishlist.isDefault && isOwner && (
					<Pressable onPress = { handleDeleteWishlist } hitSlop = { 8 }>
						<Trash2 size = { 20 } className = 'text-destructive'/>
					</Pressable>
				)}
				{!wishlist.isDefault && !isOwner && (
					<Pressable onPress = { handleLeaveWishlist } hitSlop = { 8 }>
						<LogOut size = { 20 } className = 'text-destructive'/>
					</Pressable>
				)}
			</View>
			{wishlist.isShared && (
				<View className = 'flex-row items-center justify-between px-4 pb-3'>
					<Pressable onPress = { () => setShowMembers(prev => !prev) }>
						<Text className = 'text-sm text-primary'>
							{showMembers ? 'Nascondi membri' : `Vedi membri${members ? ` (${members.length})` : ''}`}
						</Text>
					</Pressable>
					{isOwner && <AddMemberDialog onAdd = { handleAddMember }/>}
				</View>
			)}
			{showMembers && wishlist.isShared && (
				<View className = 'border-b border-border pb-2'>
					{members?.map(member => (
						<View key = { member.id } className = 'flex-row items-center justify-between px-4 py-2'>
							<Text className = 'text-sm text-foreground'>
								{member.user.username}
								{member.user.id === wishlist.owner.id ? ' (proprietario)' : ''}
							</Text>
							{isOwner && member.user.id !== wishlist.owner.id && (
								<Pressable onPress = { () => handleRemoveMember(member.user.id) } hitSlop = { 8 }>
									<Text className = 'text-xs text-destructive'>Rimuovi</Text>
								</Pressable>
							)}
						</View>
					))}
				</View>
			)}
			{isLoadingItems ? (
				<View className = 'flex-1 items-center justify-center'>
					<ActivityIndicator/>
				</View>
			) : (
				<FlatList data = { items } keyExtractor = { item => item.id }
					renderItem = { ({ item }) => (
						<WishlistItemRow item = { item } canRemove = { isOwner || wishlist.isShared } onPress = { () => router.push(`/game/${item.game.bggId}`) } onRemove = { () => handleRemoveItem(item.id) }/>
					)}
					onEndReached = { () => {
						if(hasNextPage && !isFetchingNextPage) fetchNextPage();
					}}
					onEndReachedThreshold = { 0.4 }
					ListFooterComponent = {
						isFetchingNextPage ? (
							<View className = 'py-6'>
								<ActivityIndicator/>
							</View>
						) : null
					}
					ListEmptyComponent = {
						<View className = 'flex-1 items-center justify-center py-20'>
							<Text className = 'text-center text-muted-foreground'>
								Nessun gioco in questa wishlist
							</Text>
						</View>
					}
				/>
			)}
		</View>
	)
}

export default WishlistDetailScreen;