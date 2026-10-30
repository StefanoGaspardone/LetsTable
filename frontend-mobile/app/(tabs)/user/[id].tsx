import { useCallback, useState } from 'react';
import { View, ActivityIndicator, ScrollView, Pressable } from 'react-native';
import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { RefreshControl } from 'react-native-gesture-handler';
import { Trophy, Dices, Gamepad2, UserPlus, UserCheck, Clock, UserX, Users, Heart, Award } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import MatchListItem from '@/components/matches/match-list-item';
import EmptyState from '@/components/common/empty-state';
import SegmentedControl from '@/components/common/segmented-control';
import AchievementList from '@/components/achievements/achievement-list';

import { listPendingReceived, listPendingSent } from '@/api/friend';

import { useNavigationStack } from '@/contexts/navigation-stack-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';
import { useAuth } from '@/contexts/auth-context';

import { getAvatarUrl } from '@/lib/file';
import { cn } from '@/lib/utils';

import { useUserMatches, useUserFriends, useUserDefaultWishlistItems, useUserProfile } from '@/hooks/use-user';
import { useAcceptFriendRequest, useCancelFriendRequest, useRemoveFriend, useSendFriendRequest } from '@/hooks/use-friend';
import { useUserAchievements } from '@/hooks/use-achievement';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

type ProfileTab = 'matches' | 'friends' | 'wishlist' | 'achievements';

const TAB_OPTIONS = [
	{ value: 'matches', label: 'Partite' },
	{ value: 'friends', label: 'Amici' },
	{ value: 'wishlist', label: 'Wishlist' },
	{ value: 'achievements', label: 'Traguardi' },
]

const SELF_TAB_OPTIONS = TAB_OPTIONS.filter(option => option.value !== 'achievements');

const getTabQueryKey = (tab: ProfileTab, userId: string) => {
	if(tab === 'achievements') return ['achievements', 'user', userId];
	return ['users', tab, userId];
}

const UserProfileScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const router = useNavigationStack();
	const queryClient = useQueryClient();
	const { showToast } = useToast();
	const { user } = useAuth();
	const { confirm } = useConfirmDialog();

	const { colors } = useThemeColors();

	const [tab, setTab] = useState<ProfileTab>('matches');
	const [isLookingUpRequest, setIsLookingUpRequest] = useState(false);

	const isSelf = id === user?.id;

	const { data: profile, isLoading } = useUserProfile(id);

	const matchesQuery = useUserMatches(id, undefined, tab === 'matches');
	const friendsQuery = useUserFriends(id, tab === 'friends');
	const wishlistQuery = useUserDefaultWishlistItems(id, tab === 'wishlist');
	const achievementsQuery = useUserAchievements(id, !isSelf && tab === 'achievements');

	const sendRequest = useSendFriendRequest();
	const cancelRequest = useCancelFriendRequest();
	const acceptRequest = useAcceptFriendRequest();
	const removeFriend = useRemoveFriend();

	const matches = matchesQuery.data?.pages.flatMap(page => page.content) ?? [];
	const wishlistItems = wishlistQuery.data?.pages.flatMap(page => page.content) ?? [];

	useRefetchOnFocus(['users', 'profile', id]);
	useRefetchOnFocus(getTabQueryKey(tab, id));

	const { refreshing, onRefresh } = usePullToRefresh([['users', 'profile', id], getTabQueryKey(tab, id)]);

	useFocusEffect(
		useCallback(() => {
			setTab('matches');
		}, [id])
	);

	const isActionPending = isLookingUpRequest || sendRequest.isPending || cancelRequest.isPending || acceptRequest.isPending || removeFriend.isPending;

	const showError = (fallback: string) => (error: any) => showToast(error?.response?.data?.message ?? fallback, 'error');

	const handleSendRequest = () => {
		sendRequest.mutate(id, {
			onSuccess: () => showToast('Richiesta di amicizia inviata', 'success'),
			onError: showError('Errore durante l\'invio della richiesta'),
		});
	}

	const handleCancelRequest = async () => {
		setIsLookingUpRequest(true);

		try {
			const sent = await queryClient.fetchQuery({ queryKey: ['friends', 'requests', 'sent'], queryFn: listPendingSent });
			const request = sent.find(candidate => candidate.receiver.id === id);

			if(!request) return;

			cancelRequest.mutate(request.id, {
				onSuccess: () => showToast('Richiesta annullata', 'success'),
				onError: showError('Errore durante l\'annullamento'),
			});
		} catch(error: any) {
			showError('Errore durante l\'annullamento')(error);
		} finally {
			setIsLookingUpRequest(false);
		}
	}

	const handleAcceptRequest = async () => {
		setIsLookingUpRequest(true);

		try {
			const received = await queryClient.fetchQuery({ queryKey: ['friends', 'requests', 'received'], queryFn: listPendingReceived });
			const request = received.find(candidate => candidate.sender.id === id);

			if(!request) return;

			acceptRequest.mutate(request.id, {
				onSuccess: () => showToast('Richiesta accettata', 'success'),
				onError: showError('Errore durante l\'accettazione'),
			});
		} catch(error: any) {
			showError('Errore durante l\'accettazione')(error);
		} finally {
			setIsLookingUpRequest(false);
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

		removeFriend.mutate(id, {
			onSuccess: () => showToast('Amico rimosso', 'success'),
			onError: showError('Errore durante la rimozione'),
		});
	}

	if(isLoading || !profile) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = { colors.primary }/>
			</View>
		)
	}

	const tabLoader = (
		<View className = 'flex-1 items-center justify-center py-8'>
			<ActivityIndicator color = { colors.primary }/>
		</View>
	)

	const renderFriendshipAction = () => {
		if(profile.friendshipStatus === 'SELF') return null;

		if(isActionPending) {
			return (
				<View className = 'mt-3 h-11 w-full items-center justify-center rounded-full bg-secondary'>
					<ActivityIndicator size = 'small' color = { colors.primary }/>
				</View>
			)
		}

		if(profile.friendshipStatus === 'FRIENDS' || profile.friendshipStatus === 'REQUEST_SENT') {
			const isFriend = profile.friendshipStatus === 'FRIENDS';
			const Icon = isFriend ? UserX : Clock;

			return (
				<Pressable onPress = { isFriend ? handleRemoveFriend : handleCancelRequest } className = 'mt-3 h-11 w-full flex-row items-center justify-center gap-2 rounded-full border border-border active:border-primary/90 active:bg-primary/90 active:scale-[0.98]'>
					{({ pressed }) => (
						<>
							<Icon size = { 16 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
							<Text className = { cn('text-sm font-semibold', pressed ? 'text-white' : 'text-muted-foreground') }>
								{isFriend ? 'Rimuovi amico' : 'Annulla richiesta'}
							</Text>
						</>
					)}
				</Pressable>
			)
		}

		const isReceived = profile.friendshipStatus === 'REQUEST_RECEIVED';
		const Icon = isReceived ? UserCheck : UserPlus;

		return (
			<Pressable onPress = { isReceived ? handleAcceptRequest : handleSendRequest } className = 'mt-3 h-11 w-full flex-row items-center justify-center gap-2 rounded-full bg-primary active:bg-primary/90 active:scale-[0.98]'>
				<Icon size = { 16 } color = '#FFFFFF'/>
				<Text className = 'text-sm font-semibold text-primary-foreground'>
					{isReceived ? 'Accetta richiesta' : 'Aggiungi amico'}
				</Text>
			</Pressable>
		)
	}

	const renderLoadMore = (hasNext: boolean, isFetchingNext: boolean, fetchNext: () => void, label: string) => hasNext && (
		<Pressable onPress = { () => { if(!isFetchingNext) fetchNext(); } } className = 'items-center py-3'>
			{isFetchingNext ? (
				<ActivityIndicator color = { colors.primary }/>
			) : (
				<Text className = 'text-sm font-medium text-primary'>{label}</Text>
			)}
		</Pressable>
	)

	const renderTabContent = () => {
		if(tab === 'matches') {
			if(matchesQuery.isLoading) return tabLoader;

			if(matches.length === 0) {
				return <EmptyState icon = { <Dices size = { 32 } color = { colors.primary }/> } title = 'Nessuna partita' subtitle = 'Questo utente non ha ancora registrato partite.'/>;
			}

			return (
				<View className = 'gap-2'>
					{matches.map(match => (
						<MatchListItem key = { match.id } match = { match }/>
					))}
					{renderLoadMore(!!matchesQuery.hasNextPage, matchesQuery.isFetchingNextPage, matchesQuery.fetchNextPage, 'Carica altre')}
				</View>
			)
		}

		if(tab === 'friends') {
			const friends = friendsQuery.data ?? [];

			if(friendsQuery.isLoading) return tabLoader;

			if(friends.length === 0) {
				return <EmptyState icon = { <Users size = { 32 } color = { colors.primary }/> } title = 'Nessun amico' subtitle = 'Questo utente non ha ancora amici.'/>;
			}

			return (
				<View className = 'gap-2'>
					{friends.map(friend => (
						<Pressable key = { friend.id } onPress = { () => friend.id === user?.id ? router.push('/profile') : router.push(`/user/${friend.id}`) } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2.5 active:scale-[0.98] active:opacity-75'>
							<Image source = {{ uri: getAvatarUrl(friend.avatarId, friend.username) }} style = {{ width: 40, height: 40, borderRadius: 100 }} contentFit = 'cover'/>
							<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>
								{friend.username}
								{friend.id === user?.id && <Text className = 'font-semibold text-sm text-muted-foreground'> (io)</Text>}
							</Text>
						</Pressable>
					))}
				</View>
			)
		}

		if(tab === 'wishlist') {
			if(wishlistQuery.isLoading) return tabLoader;

			if(wishlistItems.length === 0) {
				return <EmptyState icon = { <Heart size = { 32 } color = { colors.primary }/> } title = 'Wishlist vuota' subtitle = 'Questo utente non ha ancora giochi nella sua wishlist.'/>;
			}

			return (
				<View className = 'gap-2'>
					{wishlistItems.map(item => (
						<Pressable key = { item.id } onPress = { () => router.push(`/game/${item.game.bggId}`) } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75'>
							<View style = {{ width: 44, height: 44 }} className = 'overflow-hidden rounded-xl bg-secondary'>
								{item.game.thumbnailUrl ? (
									<Image source = {{ uri: item.game.thumbnailUrl }} style = {{ width: 44, height: 44 }} contentFit = 'cover'/>
								) : (
									<View className = 'h-full w-full items-center justify-center'>
										<Dices size = { 16 } color = { colors.mutedForeground }/>
									</View>
								)}
							</View>
							<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{item.game.name}</Text>
						</Pressable>
					))}
					{renderLoadMore(!!wishlistQuery.hasNextPage, wishlistQuery.isFetchingNextPage, wishlistQuery.fetchNextPage, 'Carica altri')}
				</View>
			)
		}

		if(achievementsQuery.isLoading) return tabLoader;

		if(!achievementsQuery.data) {
			return <EmptyState icon = { <Award size = { 32 } color = { colors.primary }/> } title = 'Traguardi non disponibili' subtitle = 'Impossibile caricare i traguardi di questo utente.'/>;
		}

		return <AchievementList achievements = { achievementsQuery.data }/>;
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Profilo' leftElement = { <BackButton/> }/>
			<ScrollView contentContainerStyle = {{ paddingBottom: 40, flexGrow: 1 }} className = 'flex-1 px-4 pt-2' refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/> }>
				<View className = 'items-center justify-center pb-4'>
					<View className = 'h-28 w-28 items-center justify-center overflow-hidden rounded-full border-2 border-border/50 bg-secondary shadow-sm'>
						<Image source = {{ uri: getAvatarUrl(profile.user.avatarId, profile.user.username) }} style = {{ width: 112, height: 112 }} contentFit = 'cover'/>
					</View>
					<Text className = 'text-xl font-bold text-foreground'>{profile.user.username}</Text>
					<Text className = 'text-xs text-muted-foreground'>{profile.user.email}</Text>
					{renderFriendshipAction()}
				</View>
				<View className = 'mb-6 rounded-2xl border border-border bg-card p-3 shadow-sm'>
					<View className = 'flex-row items-center justify-around'>
						<View className = 'flex-1 items-center px-2'>
							<View className = 'mb-1 flex-row items-center gap-1.5'>
								<Gamepad2 size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
								<Text className = 'font-medium text-xs uppercase tracking-wider text-muted-foreground'>
									Partite
								</Text>
							</View>
							<Text className = 'font-display text-lg'>{profile.totalMatches}</Text>
						</View>
						<View className = 'h-8 w-[1px] bg-border'/>
						<View className = 'flex-1 items-center px-2'>
							<View className = 'mb-1 flex-row items-center gap-1.5'>
								<Trophy size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
								<Text className = 'font-medium text-xs uppercase tracking-wider text-muted-foreground'>
									Vittorie
								</Text>
							</View>
							<Text className = 'font-display text-lg'>{profile.totalWins}</Text>
						</View>
					</View>
				</View>
				<View className = 'mb-3'>
					<SegmentedControl options = { isSelf ? SELF_TAB_OPTIONS : TAB_OPTIONS } selected = { tab } onSelect = { value => setTab(value as ProfileTab) }/>
				</View>
				{renderTabContent()}
			</ScrollView>
		</View>
	)
}

export default UserProfileScreen;