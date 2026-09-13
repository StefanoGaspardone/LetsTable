import { useState } from 'react';
import { View, ActivityIndicator, ScrollView, Pressable } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Trophy, Dices, Gamepad2, UserPlus, UserCheck, Clock, UserX } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import MatchListItem from '@/components/common/match-list-item';
import EmptyState from '@/components/common/empty-state';

import { getUserProfile } from '@/api/user';
import { sendFriendRequest, removeFriend, listPendingSent, cancelFriendRequest, listPendingReceived, acceptFriendRequest } from '@/api/friend';

import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';

import { getAvatarUrl } from '@/lib/file';

const UserProfileScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const queryClient = useQueryClient();
	const { showToast } = useToast();
	const { confirm } = useConfirmDialog();

	const [isActionPending, setIsActionPending] = useState(false);

	const { data: profile, isLoading } = useQuery({
		queryKey: ['users', 'profile', id],
		queryFn: () => getUserProfile(id),
	});

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
			<ScrollView contentContainerStyle = {{ paddingBottom: 40 }} className = 'flex-1 px-4 pt-2'>
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
				<Text className = 'mb-2 font-display text-xl text-foreground'>Partite Recenti</Text>
				{profile.recentMatches.length > 0 ? (
					<View className = 'gap-2'>
						{profile.recentMatches.map(match => (
							<MatchListItem key = { match.id } match = { match }/>
						))}
					</View>
				) : (
					<EmptyState icon = { <Dices size = { 32 } color = '#C45135'/> } title = 'Nessuna partita' subtitle = 'Questo utente non ha ancora registrato partite.'/>
				)}
			</ScrollView>
		</View>
	)
}

export default UserProfileScreen;