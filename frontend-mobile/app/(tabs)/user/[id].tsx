import { useCallback, useEffect, useState } from 'react';
import { View, ActivityIndicator, ScrollView, Pressable } from 'react-native';
import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { RefreshControl } from 'react-native-gesture-handler';
import { Trophy, Dices, Gamepad2, UserPlus, UserCheck, Clock, UserX, Users, Heart } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import MatchListItem from '@/components/common/match-list-item';
import EmptyState from '@/components/common/empty-state';
import SegmentedControl from '@/components/common/segmented-control';

import { getUserProfile } from '@/api/user';
import { sendFriendRequest, removeFriend, listPendingSent, cancelFriendRequest, listPendingReceived, acceptFriendRequest } from '@/api/friend';

import { useNavigationStack } from '@/contexts/navigation-stack-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';

import { getAvatarUrl } from '@/lib/file';

import { useUserMatches, useUserFriends, useUserDefaultWishlistItems } from '@/hooks/use-user';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useAuth } from '@/contexts/auth-context';

const TAB_OPTIONS = [
	{ value: 'matches', label: 'Partite' },
	{ value: 'friends', label: 'Amici' },
	{ value: 'wishlist', label: 'Wishlist' },
]

const UserProfileScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const router = useNavigationStack();
	const queryClient = useQueryClient();
	const { showToast } = useToast();
	const { user } = useAuth();
	const { confirm } = useConfirmDialog();

	const [isActionPending, setIsActionPending] = useState(false);
	const [tab, setTab] = useState<'matches' | 'friends' | 'wishlist'>('matches');

	const { data: profile, isLoading } = useQuery({
		queryKey: ['users', 'profile', id],
		queryFn: () => getUserProfile(id),
	});

	const { data: matchesData, isLoading: isLoadingMatches, fetchNextPage: fetchNextMatches, hasNextPage: hasNextMatches, isFetchingNextPage: isFetchingNextMatches, refetch: refetchMatches } = useUserMatches(id);
	const { data: friends, isLoading: isLoadingFriends, refetch: refetchFriends } = useUserFriends(id);
	const { data: wishlistData, isLoading: isLoadingWishlist, fetchNextPage: fetchNextWishlist, hasNextPage: hasNextWishlist, isFetchingNextPage: isFetchingNextWishlist, refetch: refetchWishlist } = useUserDefaultWishlistItems(id);

	const matches = matchesData?.pages.flatMap(page => page.content) ?? [];
	const wishlistItems = wishlistData?.pages.flatMap(page => page.content) ?? [];

	useRefetchOnFocus(['users', 'profile', id]);
	useRefetchOnFocus(['users', 'matches', id]);
	useRefetchOnFocus(['users', 'friends', id]);
	useRefetchOnFocus(['users', 'wishlist', id]);

	const { refreshing, onRefresh } = usePullToRefresh(
		tab === 'matches' ? [['users', 'profile', id], ['users', 'matches', id]] :
		tab === 'friends' ? [['users', 'profile', id], ['users', 'friends', id]] :
		[['users', 'profile', id], ['users', 'wishlist', id]]
	);

	useEffect(() => {
		if(tab === 'matches') {
			refetchMatches();
		} else if(tab === 'friends') {
			refetchFriends();
		} else if(tab === 'wishlist') {
			refetchWishlist();
		}
	}, [tab]);

	useEffect(() => {
		setTab('matches');
	}, [id]);

	useFocusEffect(
		useCallback(() => {
			setTab('matches');
		}, [])
	);

	const invalidateProfile = () => {
		queryClient.invalidateQueries({ queryKey: ['users', 'profile', id] });
		queryClient.invalidateQueries({ queryKey: ['friends'] });
	}

	const handleSendRequest = async () => {
		setIsActionPending(true);

		try {
			await sendFriendRequest(id);
			
			invalidateProfile();
			showToast('Richiesta di amicizia inviata', 'success');
		} catch(error: any) {
			showToast(error?.response?.data?.message ?? 'Errore durante l\'invio della richiesta', 'error');
		} finally {
			setIsActionPending(false);
		}
	}

	const handleCancelRequest = async () => {
		setIsActionPending(true);

		try {
			const sent = await listPendingSent();
			const request = sent.find(r => r.receiver.id === id);

			if(request) {
				await cancelFriendRequest(request.id);
				
				invalidateProfile();
				showToast('Richiesta annullata', 'success');
			}
		} catch(error: any) {
			showToast(error?.response?.data?.message ?? 'Errore durante l\'annullamento', 'error');
		} finally {
			setIsActionPending(false);
		}
	}

	const handleAcceptRequest = async () => {
		setIsActionPending(true);

		try {
			const received = await listPendingReceived();
			const request = received.find(r => r.sender.id === id);

			if(request) {
				await acceptFriendRequest(request.id);
				
				invalidateProfile();
				showToast('Richiesta accettata', 'success');
			}
		} catch(error: any) {
			showToast(error?.response?.data?.message ?? 'Errore durante l\'accettazione', 'error');
		} finally {
			setIsActionPending(false);
		}
	}

	const handleRemoveFriend = async () => {
		const ok = await confirm({
			title: 'Rimuovi amico',
			message: `Vuoi rimuovere ${profile?.user.username} dai tuoi amici?`,
			confirmLabel: 'Rimuovi',
			destructive: true,
		});

		if(!ok) return;

		setIsActionPending(true);

		try {
			await removeFriend(id);
			
			invalidateProfile();
			showToast('Amico rimosso', 'success');
		} catch(error: any) {
			showToast(error?.response?.data?.message ?? 'Errore durante la rimozione', 'error');
		} finally {
			setIsActionPending(false);
		}
	}

	if(isLoading || !profile) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = '#C45135'/>
			</View>
		)
	}

	const renderFriendshipAction = () => {
		if(profile.friendshipStatus === 'SELF') return null;

		if(isActionPending) {
			return (
				<View className = 'mt-3 h-11 w-full items-center justify-center rounded-full bg-secondary'>
					<ActivityIndicator size = 'small' color = '#C45135'/>
				</View>
			)
		}

		if(profile.friendshipStatus === 'FRIENDS') {
			return (
				<Pressable onPress = { handleRemoveFriend } className = 'mt-3 h-11 w-full flex-row items-center justify-center gap-2 rounded-full border border-border active:border-primary/90 active:bg-primary/90 active:scale-[0.98]'>
					{({ pressed }) => (
                        <>
                            <UserX size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65'}/>
                            <Text className = { `text-sm font-semibold text-muted-foreground ${pressed && 'text-[#FFFFFF]'}` }>Rimuovi amico</Text>
                        </>
                    )}
				</Pressable>
			)
		}

		if(profile.friendshipStatus === 'REQUEST_SENT') {
			return (
				<Pressable onPress = { handleCancelRequest } className = 'mt-3 h-11 w-full flex-row items-center justify-center gap-2 rounded-full border border-border active:border-primary/90 active:bg-primary/90 active:scale-[0.98]'>
                    {({ pressed }) => (
                        <>   
                            <Clock size = { 16 } color = { pressed ? '#FFFFFF' : '#736E65'}/>
                            <Text className = { `text-sm font-semibold text-muted-foreground ${pressed && 'text-[#FFFFFF]'}` }>Annulla richiesta</Text>
                        </>
                    )}
				</Pressable>
			)
		}

		if(profile.friendshipStatus === 'REQUEST_RECEIVED') {
			return (
				<Pressable onPress = { handleAcceptRequest } className = 'mt-3 h-11 w-full flex-row items-center justify-center gap-2 rounded-full bg-primary active:bg-primary/90 active:scale-[0.98]'>
					<UserCheck size = { 16 } color = '#FFFFFF'/>
					<Text className = 'text-sm font-semibold text-primary-foreground'>Accetta richiesta</Text>
				</Pressable>
			)
		}

		return (
			<Pressable onPress = { handleSendRequest } className = 'mt-3 h-11 w-full flex-row items-center justify-center gap-2 rounded-full bg-primary active:bg-primary/90 active:scale-[0.98]'>
				<UserPlus size = { 16 } color = '#FFFFFF'/>
				<Text className = 'text-sm font-semibold text-primary-foreground'>Aggiungi amico</Text>
			</Pressable>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Profilo' leftElement = { <BackButton/> }/>
			<ScrollView contentContainerStyle = {{ paddingBottom: 40, flexGrow: 1 }} className = 'flex-1 px-4 pt-2' refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = '#C45135' colors = { ['#C45135'] } progressBackgroundColor = '#F2EFE9'/> }>
				<View className = 'items-center justify-center pb-4'>
					<View className = 'h-28 w-28 items-center justify-center overflow-hidden rounded-full border-2 border-border/50 bg-secondary shadow-sm'>
						<Image source = {{ uri: getAvatarUrl(profile.user.avatarId, profile.user.username) }} style = {{ width: 112, height: 112 }} contentFit = 'cover'/>
					</View>
					<Text className = 'text-xl font-bold text-foreground'>{profile.user.username}</Text>
					<Text className = 'text-xs text-muted-foreground'>{profile.user.email}</Text>
					{renderFriendshipAction()}
				</View>
                <View className = 'mb-6 rounded-2xl border border-border bg-white p-3 shadow-sm'>
					<View className = 'flex-row items-center justify-around'>
						<View className = 'flex-1 items-center px-2'>
							<View className = 'mb-1 flex-row items-center gap-1.5'>
								<Gamepad2 size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
								<Text className = 'font-medium text-xs uppercase tracking-wider text-muted-foreground'>
									Partite
								</Text>
							</View>
							<Text className = 'font-display text-lg'>{profile.totalMatches}</Text>
						</View>
						<View className = 'h-8 w-[1px] bg-border'/>
						<View className = 'flex-1 items-center px-2'>
							<View className = 'mb-1 flex-row items-center gap-1.5'>
								<Trophy size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
								<Text className = 'font-medium text-xs uppercase tracking-wider text-muted-foreground'>
									Vittorie
								</Text>
							</View>
							<Text className = 'font-display text-lg'>{profile.totalWins}</Text>
						</View>
					</View>
				</View>
				<View className = 'mb-3'>
					<SegmentedControl options = { TAB_OPTIONS } selected = { tab } onSelect = { value => setTab(value as 'matches' | 'friends' | 'wishlist') }/>
				</View>
				{tab === 'matches' ? (
					isLoadingMatches ? (
						<View className = 'flex-1 items-center justify-center py-8'>
							<ActivityIndicator color = '#C45135'/>
						</View>
					) : matches.length > 0 ? (
						<View className = 'gap-2'>
							{matches.map(match => (
								<MatchListItem key = { match.id } match = { match }/>
							))}
							{hasNextMatches && (
								<Pressable onPress = { () => { if(!isFetchingNextMatches) fetchNextMatches(); } } className = 'items-center py-3'>
									{isFetchingNextMatches ? (
										<ActivityIndicator color = '#C45135'/>
									) : (
										<Text className = 'text-sm font-medium text-[#C45135]'>Carica altre</Text>
									)}
								</Pressable>
							)}
						</View>
					) : (
						<EmptyState icon = { <Dices size = { 32 } color = '#C45135'/> } title = 'Nessuna partita' subtitle = 'Questo utente non ha ancora registrato partite.'/>
					)
				) : tab === 'friends' ? (
					isLoadingFriends ? (
						<View className = 'flex-1 items-center justify-center py-8'>
							<ActivityIndicator color = '#C45135'/>
						</View>
					) : friends && friends.length > 0 ? (
						<View className = 'gap-2'>
							{friends.map(friend => (
								<Pressable key = { friend.id } onPress = { () => friend.id === user?.id ? router.push('/profile') : router.push(`/user/${friend.id}`) } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2.5 active:scale-[0.98] active:opacity-75'>
									<Image source = {{ uri: getAvatarUrl(friend.avatarId, friend.username) }} style = {{ width: 40, height: 40, borderRadius: 100 }} contentFit = 'cover'/>
									<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{friend.username}{ friend.id === user?.id && <Text className = 'font-semibold text-sm text-muted-foreground'> (io)</Text>}</Text>
								</Pressable>
							))}
						</View>
					) : (
						<EmptyState icon = { <Users size = { 32 } color = '#C45135'/> } title = 'Nessun amico' subtitle = 'Questo utente non ha ancora amici.'/>
					)
				) : (
					isLoadingWishlist ? (
						<View className = 'flex-1 items-center justify-center py-8'>
							<ActivityIndicator color = '#C45135'/>
						</View>
					) : wishlistItems.length > 0 ? (
						<View className = 'gap-2'>
							{wishlistItems.map(item => (
								<Pressable key = { item.id } onPress = { () => router.push(`/game/${item.game.bggId}`) } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75'>
									<View style = {{ width: 44, height: 44 }} className = 'overflow-hidden rounded-xl bg-secondary'>
										{item.game.thumbnailUrl ? (
											<Image source = {{ uri: item.game.thumbnailUrl }} style = {{ width: 44, height: 44 }} contentFit = 'cover'/>
										) : (
											<View className = 'h-full w-full items-center justify-center'>
												<Dices size = { 16 } color = '#736E65'/>
											</View>
										)}
									</View>
									<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{item.game.name}</Text>
								</Pressable>
							))}
							{hasNextWishlist && (
								<Pressable onPress = { () => { if(!isFetchingNextWishlist) fetchNextWishlist(); } } className = 'items-center py-3'>
									{isFetchingNextWishlist ? (
										<ActivityIndicator color = '#C45135'/>
									) : (
										<Text className = 'text-sm font-medium text-[#C45135]'>Carica altri</Text>
									)}
								</Pressable>
							)}
						</View>
					) : (
						<EmptyState icon = { <Heart size = { 32 } color = '#C45135'/> } title = 'Wishlist vuota' subtitle = 'Questo utente non ha ancora giochi nella sua wishlist.'/>
					)
				)}
			</ScrollView>
		</View>
	)
}

export default UserProfileScreen;