import { ReactNode, useCallback, useEffect, useState } from 'react';
import { View, Pressable, ActivityIndicator, FlatList, SectionList } from 'react-native';
import { Image } from 'expo-image';
import { useFocusEffect } from 'expo-router';
import { Search, UserPlus, Check, X, UserX, Users, Clock } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import ScreenHeader from '@/components/common/screen-header';
import SegmentedControl from '@/components/common/segmented-control';
import ComingSoon from '@/components/common/cooming-soon';

import { useDebounce } from '@/hooks/use-debounce';
import { useUserSearch } from '@/hooks/use-user';
import {  useFriends, usePendingReceived, usePendingSent, useSendFriendRequest, useAcceptFriendRequest, useRejectFriendRequest, useCancelFriendRequest, useRemoveFriend } from '@/hooks/use-friend';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { getAvatarUrl } from '@/lib/file';

const getViewOptions = (pendingRequestsCount: number) => [
    { value: 'friends', label: 'Amici' },
    { value: 'requests', label: 'Richieste', badge: pendingRequestsCount },
]

interface UserItem {
    id: string;
    username: string;
    avatarId: string | null;
}

const FriendsScreen = () => {
    const [viewMode, setViewMode] = useState<'friends' | 'requests'>('friends');
    const [search, setSearch] = useState('');

    const debouncedSearch = useDebounce(search);
    const isSearching = debouncedSearch.length > 0;

    useFocusEffect(
        useCallback(() => {
            setSearch('');
            setViewMode('friends');
        }, [])
    );

    const { data: friends, isLoading: isLoadingFriends, refetch: refetchFriends } = useFriends();
    const { data: received, isLoading: isLoadingReceived, refetch: refetchReceived } = usePendingReceived();
    const { data: sent, isLoading: isLoadingSent, refetch: refetchSent } = usePendingSent();
    const { data: searchResults, isLoading: isLoadingSearch } = useUserSearch(debouncedSearch);

    useEffect(() => {
        if(viewMode === 'friends') {
            refetchFriends();
        } else {
            refetchReceived();
            refetchSent();
        }
    }, [viewMode]);

    useRefetchOnFocus(['friends']);

    const sendRequest = useSendFriendRequest();
    const acceptRequest = useAcceptFriendRequest();
    const rejectRequest = useRejectFriendRequest();
    const cancelRequest = useCancelFriendRequest();
    const removeFriend = useRemoveFriend();

    const friendIds = new Set((friends ?? []).map(f => f.id));
    const sentIds = new Set((sent ?? []).map(r => r.receiver.id));
    const receivedIds = new Set((received ?? []).map(r => r.sender.id));

    const visibleSearchResults = (searchResults ?? []).filter(u => !friendIds.has(u.id));

    const renderUserCard = (user: UserItem, action: ReactNode, subtitle?: string) => (
        <View key = { user.id } className = 'mb-2.5 flex-row items-center justify-between rounded-2xl bg-card p-3 mx-4 border border-border/50 shadow-sm'>
            <View className = 'flex-row items-center gap-3 flex-1 mr-2'>
                <View className = 'h-12 w-12 overflow-hidden rounded-full bg-secondary items-center justify-center border border-border/30'>
                    <Image source = {{ uri: getAvatarUrl(user?.avatarId ?? null, user?.username ?? '') }} style = {{ width: 48, height: 48 }} contentFit = 'cover' transition = { 200 }/>
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
        </View>
    )

    return (
        <View className = 'flex-1 bg-background'>
            <ScreenHeader title = 'Amici'/>
			<View className = 'gap-3 px-4 pt-3 pb-2'>
                <View className = 'relative'>
                    <Input  placeholder = 'Cerca per nome utente...' value = { search } onChangeText = { setSearch } className = 'rounded-2xl bg-secondary/80 pl-11 pr-10 border-0 text-sm h-11'/>
                    <View className = 'pointer-events-none absolute left-3.5 top-0 bottom-0 justify-center'>
                        <Search size = { 18 } className = 'text-muted-foreground' color = '#8E8E93'/>
                    </View>
                    {search.length > 0 && (
                        <Pressable onPress = { () => setSearch('') } hitSlop = { 10 } className = 'absolute right-3.5 top-0 bottom-0 justify-center'>
                            <X size = { 18 } color = '#8E8E93'/>
                        </Pressable>
                    )}
                </View>
                {!isSearching && (
                    <SegmentedControl options = { getViewOptions(received?.length ?? 0) } selected = { viewMode } onSelect = { value => setViewMode(value as 'friends' | 'requests') }/>
                )}
            </View>
			{isSearching ? (
                isLoadingSearch ? (
                    <View className = 'flex-1 items-center justify-center'>
                        <ActivityIndicator color = '#C45135' size = 'large'/>
                    </View>
                ) : (
                    <FlatList className = 'pt-2' data = { visibleSearchResults } keyExtractor = { item => item.id } showsVerticalScrollIndicator = { false }
                        renderItem = { ({ item }) => {
                            const alreadySent = sentIds.has(item.id);
                            const alreadyReceived = receivedIds.has(item.id);

                            let actionComponent = (
                                <Pressable onPress = { () => sendRequest.mutate(item.id) } className = 'flex-row items-center gap-1.5 rounded-full bg-[#C45135] px-3.5 py-2 active:bg-primary/90 active:scale-[0.98]'>
									<UserPlus size = { 15 } color = '#FFFFFF'/>
									<Text className = 'text-xs font-semibold text-white'>Aggiungi</Text>
                                </Pressable>
                            )

                            if(alreadySent) {
                                actionComponent = (
                                    <View className = 'flex-row items-center gap-1 rounded-full bg-secondary px-3 py-1.5'>
                                        <Clock size = { 13 } color = '#8E8E93'/>
                                        <Text className = 'text-xs font-medium text-muted-foreground'>Inviata</Text>
                                    </View>
                                )
                            } else if(alreadyReceived) {
                                actionComponent = (
                                    <View className = 'rounded-full bg-amber-500/10 px-3 py-1.5 border border-amber-500/20'>
                                        <Text className = 'text-xs font-semibold text-amber-600'>Da accettare</Text>
                                    </View>
                                )
                            }

                            return renderUserCard(item, actionComponent);
                        }}
                        ListEmptyComponent = {
                            <View className = 'py-12 items-center justify-center'>
                                <Text className = 'text-sm font-medium text-muted-foreground'>Nessun utente trovato</Text>
                            </View>
                        }
                    />
                )
            ) : viewMode === 'friends' ? (
                isLoadingFriends ? (
                    <View className = 'flex-1 items-center justify-center'>
                        <ActivityIndicator color = '#C45135' size = 'large'/>
                    </View>
                ) : friends && friends.length > 0 ? (
                    <FlatList className = 'pt-2' data = { friends } keyExtractor = { item => item.id } showsVerticalScrollIndicator = { false }
                        renderItem = { ({ item }) =>
                            renderUserCard( item,
                                <Pressable  onPress = { () => removeFriend.mutate(item.id) } className = 'h-9 w-9 items-center justify-center rounded-full bg-secondary/80 active:bg-primary/90 active:scale-[0.98]' hitSlop = { 6 }>
                                    {({ pressed }) => (
                                        <UserX size = { 17 } color = { pressed ? '#FFFFFF' : '#736E65' }/>
                                    )}
                                </Pressable>
                            )
                        }
                    />
                ) : (
                    <ComingSoon icon = { <Users size = { 40 } color = '#C45135'/> } title = 'Nessun amico ancora' subtitle = 'Usa la barra in alto per cercare e aggiungere nuovi amici.'/>
                )
            ) : (
                <View className = 'flex-1'>
                    {(isLoadingReceived || isLoadingSent) ? (
                        <View className = 'flex-1 items-center justify-center'>
                            <ActivityIndicator color = '#C45135' size = 'large'/>
                        </View>
                    ) : (received?.length ?? 0) === 0 && (sent?.length ?? 0) === 0 ? (
                        <ComingSoon icon = { <Users size = { 40 } color = '#C45135'/> } title = 'Nessuna richiesta' subtitle = 'Le richieste di amicizia inviate e ricevute compariranno qui.'/>
                    ) : (
                        <SectionList className = 'pt-2' showsVerticalScrollIndicator = { false }
                            sections = { [
                                {
                                    title: 'Richieste Ricevute',
                                    data: (received ?? []).map(r => ({ ...r.sender, requestId: r.id })),
                                    type: 'received',
                                },
                                {
                                    title: 'Richieste Inviate',
                                    data: (sent ?? []).map(r => ({ ...r.receiver, requestId: r.id })),
                                    type: 'sent',
                                },
                            ].filter(section => section.data.length > 0) }
                            keyExtractor = { item => item.requestId }
                            renderSectionHeader = { ({ section: { title } }) => (
                                <Text className = 'px-5 pb-2 pt-3 text-xs font-bold uppercase tracking-wider text-muted-foreground'>
                                    {title}
                                </Text>
                            ) }
                            renderItem = { ({ item, section }) => {
                                if(section.type === 'received') {
                                    return renderUserCard(item,
                                        <View className = 'flex-row items-center gap-2'>
                                            <Pressable onPress = { () => acceptRequest.mutate(item.requestId)} className = 'flex-row items-center gap-1 rounded-full bg-[#C45135] px-3 py-1.5 active:bg-primary/90 active:scale-[0.98]'>
                                                <Check size = { 14 } color = '#FFFFFF'/>
                                                <Text className = 'text-xs font-semibold text-white'>Accetta</Text>
                                            </Pressable>
                                            <Pressable onPress = { () => rejectRequest.mutate(item.requestId) } className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary active:bg-primary/90 active:scale-[0.98]'>
                                                {({ pressed }) => (
                                                     <X size = { 15 } color = { pressed ? '#FFFFFF' : '#736E65'}/>
                                                )}
                                            </Pressable>
                                        </View>,
                                        'Ti ha inviato una richiesta'
                                    )
                                }

                                return renderUserCard(item,
                                    <Pressable onPress = { () => cancelRequest.mutate(item.requestId) } className = 'rounded-full bg-secondary px-3 py-1.5 active:bg-primary/90 active:scale-[0.98]'>
										{({ pressed }) => (
											<Text className = { `text-xs font-semibold text-muted-foreground ${pressed && 'text-white'}` }>Annulla</Text>
										)}
                                    </Pressable>,
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