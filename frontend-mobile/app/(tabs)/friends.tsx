import { ReactNode, useCallback, useState } from 'react';
import { View, Pressable, ActivityIndicator, FlatList, SectionList } from 'react-native';
import { Image } from 'expo-image';
import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { RefreshControl } from 'react-native-gesture-handler';
import { Search, UserPlus, Check, X, UserX, Users, Clock } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import ComingSoon from '@/components/common/coming-soon';

import { useDebounce } from '@/hooks/use-debounce';
import { useUserSearch } from '@/hooks/use-user';
import { useFriends, usePendingReceived, usePendingSent, useSendFriendRequest, useAcceptFriendRequest, useRejectFriendRequest, useCancelFriendRequest, useRemoveFriend } from '@/hooks/use-friend';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { useNavigationStack } from '@/contexts/navigation-stack-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';

import { getAvatarUrl } from '@/lib/file';
import { cn } from '@/lib/utils';

type ViewMode = 'friends' | 'requests';

interface UserItem {
	id: string;
	username: string;
	avatarId: string | null;
}

interface PendingMutation {
	isPending: boolean;
	variables?: string;
}

const getViewOptions = (pendingRequestsCount: number) => [
	{ value: 'friends', label: 'Amici' },
	{ value: 'requests', label: 'Richieste', badge: pendingRequestsCount },
]

const isPendingFor = (mutation: PendingMutation, key: string) => mutation.isPending && mutation.variables === key;

const userKeyExtractor = (item: UserItem) => item.id;

const FriendsScreen = () => {
	const { tab: tabParam } = useLocalSearchParams<{ tab?: string }>();

	const [viewMode, setViewMode] = useState<ViewMode>('friends');
	const [search, setSearch] = useState('');

	const router = useNavigationStack();
	const { showToast } = useToast();
	const { confirm } = useConfirmDialog();
	const { colors, colorScheme } = useThemeColors();

	const debouncedSearch = useDebounce(search);
	const isSearching = debouncedSearch.length > 0;

	useFocusEffect(
		useCallback(() => {
			setSearch('');
			setViewMode(tabParam === 'requests' ? 'requests' : 'friends');
		}, [tabParam])
	);

	const { data: friends, isLoading: isLoadingFriends } = useFriends();
	const { data: received, isLoading: isLoadingReceived } = usePendingReceived();
	const { data: sent, isLoading: isLoadingSent } = usePendingSent();
	const { data: searchResults, isLoading: isLoadingSearch } = useUserSearch(debouncedSearch);

	useRefetchOnFocus(['friends']);

	const { refreshing, onRefresh } = usePullToRefresh([['friends']]);
	const refreshControl = <RefreshControl key = { colorScheme } refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/>;

	const sendRequest = useSendFriendRequest();
	const acceptRequest = useAcceptFriendRequest();
	const rejectRequest = useRejectFriendRequest();
	const cancelRequest = useCancelFriendRequest();
	const removeFriend = useRemoveFriend();

	const showError = (fallback: string) => (error: any) => showToast(error?.response?.data?.message ?? fallback, 'error');

	const handleRemoveFriend = async (friend: UserItem) => {
		const ok = await confirm({
			title: 'Rimuovi amico',
			message: `Vuoi rimuovere ${friend.username} dai tuoi amici?`,
			confirmLabel: 'Rimuovi',
			destructive: true,
		});

		if(!ok) return;

		removeFriend.mutate(friend.id, { onError: showError('Errore durante la rimozione') });
	}

	const friendIds = new Set((friends ?? []).map(friend => friend.id));
	const sentIds = new Set((sent ?? []).map(request => request.receiver.id));
	const receivedIds = new Set((received ?? []).map(request => request.sender.id));

	const visibleSearchResults = (searchResults ?? []).filter(searchUser => !friendIds.has(searchUser.id));

	const spinner = <ActivityIndicator size = 'small' color = { colors.primary }/>;

	const renderUserCard = (user: UserItem, action: ReactNode, subtitle?: string) => (
		<Pressable onPress = { () => router.push(`/user/${user.id}`) } key = { user.id } className = 'mb-2.5 flex-row items-center justify-between rounded-2xl bg-card p-3 mx-4 border border-border/50 shadow-sm active:scale-[0.98] active:opacity-75'>
			<View className = 'flex-row items-center gap-3 flex-1 mr-2'>
				<View className = 'h-12 w-12 overflow-hidden rounded-full bg-secondary items-center justify-center border border-border/30'>
					<Image source = {{ uri: getAvatarUrl(user.avatarId ?? null, user.username ?? '') }} style = {{ width: 48, height: 48 }} contentFit = 'cover' transition = { 200 }/>
				</View>
				<View className = 'flex-1 justify-center'>
					<Text className = 'text-base font-semibold text-foreground' numberOfLines = { 1 }>
						{user.username}
					</Text>
					<Text className = 'text-xs text-muted-foreground' numberOfLines = { 1 }>
						{subtitle ?? `@${user.username.toLowerCase()}`}
					</Text>
				</View>
			</View>
			<View className = 'flex-row items-center'>{action}</View>
		</Pressable>
	)

	const renderSearchAction = (item: UserItem) => {
		if(sentIds.has(item.id)) {
			return (
				<View className = 'flex-row items-center gap-1 rounded-full bg-secondary px-3 py-1.5'>
					<Clock size = { 13 } color = { colors.mutedForeground }/>
					<Text className = 'text-xs font-medium text-muted-foreground'>Inviata</Text>
				</View>
			)
		}

		if(receivedIds.has(item.id)) {
			return (
				<View className = 'rounded-full bg-amber-500/10 px-3 py-1.5 border border-amber-500/20'>
					<Text className = 'text-xs font-semibold text-amber-600'>Da accettare</Text>
				</View>
			)
		}

		if(isPendingFor(sendRequest, item.id)) return spinner;

		return (
			<Pressable onPress = { () => sendRequest.mutate(item.id, { onError: showError('Errore durante l\'invio della richiesta') }) } className = 'flex-row items-center gap-1.5 rounded-full bg-primary px-3.5 py-2 active:bg-primary/90 active:scale-[0.98]'>
				<UserPlus size = { 15 } color = '#FFFFFF'/>
				<Text className = 'text-xs font-semibold text-white'>Aggiungi</Text>
			</Pressable>
		)
	}

	const loader = (
		<View className = 'flex-1 items-center justify-center'>
			<ActivityIndicator color = { colors.primary } size = 'large'/>
		</View>
	)

	const requestSections = [
		{
			title: 'Richieste Ricevute',
			data: (received ?? []).map(request => ({ ...request.sender, requestId: request.id })),
			type: 'received',
		},
		{
			title: 'Richieste Inviate',
			data: (sent ?? []).map(request => ({ ...request.receiver, requestId: request.id })),
			type: 'sent',
		},
	].filter(section => section.data.length > 0);

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Amici'/>
			<View className = 'gap-3 px-4 pt-4 pb-2'>
				<View className = 'relative'>
					<Input placeholder = 'Cerca per nome utente...' value = { search } onChangeText = { setSearch } className = 'rounded-2xl bg-secondary/80 pl-11 pr-10 border-0 text-sm h-11'/>
					<View className = 'pointer-events-none absolute left-3.5 top-0 bottom-0 justify-center'>
						<Search size = { 18 } color = { colors.mutedForeground }/>
					</View>
					{search.length > 0 && (
						<Pressable onPress = { () => setSearch('') } hitSlop = { 10 } className = 'absolute right-3.5 top-0 bottom-0 justify-center'>
							<X size = { 18 } color = { colors.mutedForeground }/>
						</Pressable>
					)}
				</View>
				{!isSearching && (
					<SegmentedControl options = { getViewOptions(received?.length ?? 0) } selected = { viewMode } onSelect = { value => setViewMode(value as ViewMode) }/>
				)}
			</View>
			{isSearching ? (
				isLoadingSearch ? loader : (
					<FlatList
						className = 'pt-2'
						data = { visibleSearchResults }
						keyExtractor = { userKeyExtractor }
						showsVerticalScrollIndicator = { false }
						keyboardShouldPersistTaps = 'handled'
						renderItem = { ({ item }) => renderUserCard(item, renderSearchAction(item)) }
						ListEmptyComponent = {
							<View className = 'py-12 items-center justify-center'>
								<Text className = 'text-sm font-medium text-muted-foreground'>Nessun utente trovato</Text>
							</View>
						}
					/>
				)
			) : viewMode === 'friends' ? (
				isLoadingFriends ? loader : friends && friends.length > 0 ? (
					<FlatList
						className = 'pt-2'
						data = { friends }
						keyExtractor = { userKeyExtractor }
						showsVerticalScrollIndicator = { false }
						refreshControl = { refreshControl }
						renderItem = { ({ item }) => renderUserCard(item,
							isPendingFor(removeFriend, item.id) ? spinner : (
								<Pressable onPress = { () => handleRemoveFriend(item) } className = 'h-9 w-9 items-center justify-center rounded-full bg-secondary/80 active:bg-primary/90 active:scale-[0.98]' hitSlop = { 6 }>
									{({ pressed }) => (
										<UserX size = { 17 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
									)}
								</Pressable>
							)
						) }
					/>
				) : (
					<ComingSoon icon = { <Users size = { 40 } color = { colors.primary }/> } title = 'Nessun amico ancora' subtitle = 'Usa la barra in alto per cercare e aggiungere nuovi amici.'/>
				)
			) : (
				<View className = 'flex-1'>
					{(isLoadingReceived || isLoadingSent) ? loader : requestSections.length === 0 ? (
						<ComingSoon icon = { <Users size = { 40 } color = { colors.primary }/> } title = 'Nessuna richiesta' subtitle = 'Le richieste di amicizia inviate e ricevute compariranno qui.'/>
					) : (
						<SectionList
							className = 'pt-2'
							showsVerticalScrollIndicator = { false }
							refreshControl = { refreshControl }
							sections = { requestSections }
							keyExtractor = { item => item.requestId }
							renderSectionHeader = { ({ section: { title } }) => (
								<Text className = 'px-5 pb-2 pt-3 text-xs font-bold uppercase tracking-wider text-muted-foreground'>
									{title}
								</Text>
							) }
							renderItem = { ({ item, section }) => {
								if(section.type === 'received') {
									const isBusy = isPendingFor(acceptRequest, item.requestId) || isPendingFor(rejectRequest, item.requestId);

									return renderUserCard(item,
										isBusy ? spinner : (
											<View className = 'flex-row items-center gap-2'>
												<Pressable onPress = { () => acceptRequest.mutate(item.requestId, { onError: showError('Errore durante l\'accettazione') }) } className = 'flex-row items-center gap-1 rounded-full bg-primary px-3 py-1.5 active:bg-primary/90 active:scale-[0.98]'>
													<Check size = { 14 } color = '#FFFFFF'/>
													<Text className = 'text-xs font-semibold text-white'>Accetta</Text>
												</Pressable>
												<Pressable onPress = { () => rejectRequest.mutate(item.requestId, { onError: showError('Errore durante il rifiuto') }) } className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary active:bg-primary/90 active:scale-[0.98]'>
													{({ pressed }) => (
														<X size = { 15 } color = { pressed ? '#FFFFFF' : colors.mutedForeground }/>
													)}
												</Pressable>
											</View>
										),
										'Ti ha inviato una richiesta'
									)
								}

								return renderUserCard(item,
									isPendingFor(cancelRequest, item.requestId) ? spinner : (
										<Pressable onPress = { () => cancelRequest.mutate(item.requestId, { onError: showError('Errore durante l\'annullamento') }) } className = 'rounded-full bg-secondary px-3 py-1.5 active:bg-primary/90 active:scale-[0.98]'>
											{({ pressed }) => (
												<Text className = { cn('text-xs font-semibold', pressed ? 'text-white' : 'text-muted-foreground') }>Annulla</Text>
											)}
										</Pressable>
									),
									'In attesa di risposta...'
								)
							}}
						/>
					)}
				</View>
			)}
		</View>
	)
}

export default FriendsScreen;