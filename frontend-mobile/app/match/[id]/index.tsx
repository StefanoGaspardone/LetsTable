import { useMemo, useRef, useState } from 'react';
import { View, Pressable, ActivityIndicator, LayoutAnimation, Platform, UIManager, ScrollView } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Crown, Dices, Pencil, Trash2, MapPin, FileText, Trophy, Users, ChevronDown, ChevronUp, User, Repeat, Clock } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import FabMenu from '@/components/common/fab-menu';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';

import { getMatchById, deleteMatch } from '@/api/match';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';

import { formatDuration } from '@/lib/time';

if(Platform.OS === 'android' && UIManager.setLayoutAnimationEnabledExperimental) {
    UIManager.setLayoutAnimationEnabledExperimental(true);
}

const MatchDetailScreen = () => {
    const { id } = useLocalSearchParams<{ id: string }>();
    const { user } = useAuth();
    const { showToast } = useToast();
    const { confirm } = useConfirmDialog();
    const queryClient = useQueryClient();

    const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);

    const [isDeleting, setIsDeleting] = useState(false);
    const [expandedTeamId, setExpandedTeamId] = useState<string | null>(null);

    const { data: match, isLoading } = useQuery({
        queryKey: ['matches', 'detail', id],
        queryFn: () => getMatchById(id),
    });

    const isCreator = match?.createdBy?.id === user?.id;
    const isInProgress = match?.durationMinutes == null;

    const toggleTeamExpand = (teamId: string) => {
        LayoutAnimation.configureNext(LayoutAnimation.Presets.easeInEaseOut);
        setExpandedTeamId(prev => (prev === teamId ? null : teamId));
    }

    const handleDelete = async () => {
        const ok = await confirm({
            title: 'Elimina partita',
            message: 'Vuoi eliminare questa partita? L\'azione è irreversibile.',
            confirmLabel: 'Elimina',
            destructive: true,
        });
        
		if(!ok) return;

        setIsDeleting(true);
        
		try {
            await deleteMatch(id);
            
			queryClient.invalidateQueries({ queryKey: ['matches'] });
            router.push('/(tabs)/home');
        } catch(error: any) {
            showToast(error?.response?.data?.message ?? 'Errore durante l\'eliminazione', 'error');
            setIsDeleting(false);
        }
    }

    const handleReplay = () => {
        if(!match) return;
        
		registerMatchSheetRef.current?.present({
            id: match.game.id,
            name: match.game.name,
            thumbnailUrl: match.game.thumbnailUrl,
        });
    }

    const sortedEntries = useMemo(() => {
        if(!match) return [];
        
		if(match.isTeamBased) {
            const teams = match.teams?.map(team => ({
                id: team.id,
                name: team.name ?? 'Squadra senza nome',
                color: team.color,
                score: team.score,
                isWinner: team.isWinner,
                members: team.players.map(p => ({
                    id: p.id,
                    name: p.user?.username ?? p.guestName ?? 'Sconosciuto',
                    avatarUrl: p.user?.avatarUrl,
                })),
            })) ?? [];
            
			return [...teams].sort((a, b) => {
                if(a.isWinner) return -1;
                if(b.isWinner) return 1;
                return (b.score ?? 0) - (a.score ?? 0);
            });
        } else {
            const players = match.players?.map(p => ({
                id: p.id,
                name: p.user?.username ?? p.guestName ?? 'Sconosciuto',
                avatarUrl: p.user?.avatarUrl,
                color: p.color,
                score: p.score,
                isWinner: p.isWinner,
            })) ?? [];
            
			return [...players].sort((a, b) => {
                if(a.isWinner) return -1;
                if(b.isWinner) return 1;
                return (b.score ?? 0) - (a.score ?? 0);
            });
        }
    }, [match]);

    if(isLoading || !match) {
        return <View className = 'flex-1 bg-background'/>;
    }

    const formattedDate = new Date(match.playedAt).toLocaleDateString('it-IT', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
    });

    const firstPlace = sortedEntries[0];
    const secondPlace = sortedEntries[1];
    const thirdPlace = sortedEntries[2];
    const remainingEntries = sortedEntries.slice(3);

    return (
        <View className = 'flex-1 bg-background'>
            <ScreenHeader title = 'Dettaglio Partita' leftElement = { <BackButton/> }/>
            <ScrollView className = 'flex-1' contentContainerClassName = 'p-4 pb-24'>
                <Pressable className = 'flex-row items-center gap-3 rounded-2xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75' onPress = { () => router.push(`/game/${match.game.bggId}`) }>
                    <View style = {{ width: 56, height: 56 }} className = 'overflow-hidden rounded-xl bg-secondary'>
                        {match.game.thumbnailUrl ? (
                            <Image source = {{ uri: match.game.thumbnailUrl }} style = {{ width: 56, height: 56 }} contentFit = 'cover'/>
                        ) : (
                            <View className = 'h-full w-full items-center justify-center'>
                                <Dices size = { 22 } color = '#736E65'/>
                            </View>
                        )}
                    </View>
                    <View className = 'flex-1'>
                        <Text className = 'font-display text-lg text-foreground' numberOfLines = { 1 }>
                            {match.game.name}
                        </Text>
                        <Text className ='text-sm text-muted-foreground'>{formattedDate}</Text>
                    </View>
                </Pressable>
				{isInProgress ? (
                    <View className = 'mt-3 flex-row items-center gap-2 self-start rounded-full border border-[#C45135]/30 bg-[#C45135]/10 px-3 py-1.5'>
                        <View className = 'h-2 w-2 rounded-full bg-[#C45135]'/>
                        <Text className = 'text-sm font-medium text-[#C45135]'>Partita in corso</Text>
                    </View>
                ) : match.durationMinutes != null ? (
                    <View className = 'mt-4 flex-row items-center gap-2'>
                        <Clock size = { 18 } color = '#736E65'/>
                        <Text className = 'text-sm text-muted-foreground font-semibold'>{formatDuration(match.durationMinutes)}</Text>
                    </View>
                ) : null}
				{match.place && (
                    <View className = 'mt-4 flex-row items-center gap-2'>
                        <MapPin size = { 18 } color = '#736E65'strokeWidth = { 2 }/>
                        <Text className = 'text-sm text-muted-foreground font-semibold'>{match.place}</Text>
                    </View>
                )}
                {match.notes && (
                    <View className = 'mt-4 flex-row items-start gap-2'>
                        <FileText size = { 18 } color = '#736E65' style = {{ marginTop: 2 }}/>
                        <Text className = 'flex-1 text-sm text-muted-foreground font-semibold'>{match.notes}</Text>
                    </View>
                )}
				<View className = 'mb-3 mt-6 flex-row items-center gap-2'>
                    {match.isTeamBased ? <Users size = { 18 } color = '#736E65'/> : <Trophy size = { 18 } color = '#736E65'/>}
                    <Text className = 'text-sm font-semibold uppercase tracking-wide text-muted-foreground'>
                        {isInProgress ? (match.isTeamBased ? 'Squadre' : 'Giocatori') : 'Classifica'}
                    </Text>
                </View>
				{!isInProgress && sortedEntries.length > 0 && (
                    <View className = 'mb-6 mt-2 flex-row items-end justify-center gap-2 px-1'>
                        <View className='flex-1 items-center'>
                            {secondPlace ? (
                                <View className = 'w-full items-center py-2'>
                                    <View className = 'mb-2 rounded-full bg-slate-200 px-3 py-0.5 dark:bg-slate-800'>
                                        <Text className = 'text-sm font-black text-slate-700 dark:text-slate-300'>2°</Text>
                                    </View>
                                    {'avatarUrl' in secondPlace && secondPlace.avatarUrl ? (
                                        <Image source = {{ uri: secondPlace.avatarUrl }} style={{ width: 48, height: 48, borderRadius: 24 }}/>
                                    ) : (
                                        <View className = 'h-12 w-12 items-center justify-center rounded-full bg-secondary' style = { secondPlace.color ? { backgroundColor: `${secondPlace.color}20` } : undefined }>
                                            <User size = { 22 } color = { secondPlace.color ?? '#736E65' }/>
                                        </View>
                                    )}
                                    <Text className = 'mt-2 text-center text-sm font-semibold text-foreground' numberOfLines = { 1 }>
                                        {secondPlace.name}
                                    </Text>
                                    {secondPlace.score != null && (
                                        <Text className = 'mt-0.5 text-sm font-bold text-muted-foreground'>{secondPlace.score}</Text>
                                    )}
                                </View>
                            ) : (
                                <View className = 'h-24 w-full'/>
                            )}
                        </View>
						<View className = 'flex-1 items-center'>
                            {firstPlace && (
                                <View className = 'w-full items-center py-2'>
                                    <View className = 'mb-2 flex-row items-center gap-1 rounded-full bg-amber-500 px-3.5 py-1 shadow-sm'>
                                        <Crown size = { 14 } color = '#FFFFFF' strokeWidth = { 2.5 }/>
                                        <Text className = 'text-sm font-black text-white'>1°</Text>
                                    </View>
                                    {'avatarUrl' in firstPlace && firstPlace.avatarUrl ? (
                                        <Image source = {{ uri: firstPlace.avatarUrl }} style = {{ width: 68, height: 68, borderRadius: 34 }}/>
                                    ) : (
                                        <View className = 'h-17 w-17 items-center justify-center rounded-full bg-amber-500/20'>
                                            <User size = { 30 } color = '#D97706'/>
                                        </View>
                                    )}
                                    <Text className = 'mt-2 text-center text-base font-bold text-foreground' numberOfLines = { 1 }>
                                        {firstPlace.name}
                                    </Text>
                                    {firstPlace.score != null && (
                                        <Text className = 'mt-0.5 text-base font-black text-amber-600 dark:text-amber-400'>{firstPlace.score}</Text>
                                    )}
                                </View>
                            )}
                        </View>
						<View className = 'flex-1 items-center'>
                            {thirdPlace ? (
                                <View className = 'w-full items-center py-2'>
                                    <View className = 'mb-2 rounded-full bg-amber-900/10 px-3 py-0.5 dark:bg-amber-900/30'>
                                        <Text className = 'text-sm font-black text-amber-800 dark:text-amber-300'>3°</Text>
                                    </View>
                                    {'avatarUrl' in thirdPlace && thirdPlace.avatarUrl ? (
                                        <Image source = {{ uri: thirdPlace.avatarUrl }} style = {{ width: 48, height: 48, borderRadius: 24 }}/>
                                    ) : (
                                        <View className = 'h-12 w-12 items-center justify-center rounded-full bg-secondary' style = { thirdPlace.color ? { backgroundColor: `${thirdPlace.color}20` } : undefined }>
                                            <User size = { 22 } color = { thirdPlace.color ?? '#736E65' }/>
                                        </View>
                                    )}
                                    <Text className = 'mt-2 text-center text-sm font-semibold text-foreground' numberOfLines = { 1 }>
                                        {thirdPlace.name}
                                    </Text>
                                    {thirdPlace.score != null && (
                                        <Text className = 'mt-0.5 text-sm font-bold text-muted-foreground'>{thirdPlace.score}</Text>
                                    )}
                                </View>
                            ) : (
                                <View className = 'h-24 w-full'/>
                            )}
                        </View>
                    </View>
                )}
				<View className = 'gap-2'>
                    {(isInProgress ? sortedEntries : remainingEntries).map((entry, index) => {
                        const rank = isInProgress ? index + 1 : index + 4;
                        const isTeam = 'members' in entry;

                        if(isTeam) {
                            const isExpanded = expandedTeamId === entry.id;
                            
							return (
                                <View key = { entry.id } className = 'overflow-hidden rounded-xl border border-border bg-card'>
                                    <Pressable onPress = { () => toggleTeamExpand(entry.id) } className = 'flex-row items-center gap-3 p-2.5 active:opacity-80'>
                                        {!isInProgress && <Text className = 'w-5 text-center text-xs font-bold text-muted-foreground'>{rank}°</Text>}
                                        <View style = {{ backgroundColor: entry.color }} className = 'h-4 w-4 rounded-full'/>
                                        <View className = 'flex-1'>
                                            <Text className = 'text-sm font-semibold text-foreground' numberOfLines = { 1 }>{entry.name}</Text>
                                            <Text className = 'text-xs text-muted-foreground'>{entry.members.length} membri</Text>
                                        </View>
                                        {!isInProgress && entry.score != null && <Text className = 'text-sm font-bold text-foreground'>{entry.score}</Text>}
                                        {isExpanded ? <ChevronUp size = { 16 } color = '#736E65'/> : <ChevronDown size = { 16 } color = '#736E65'/>}
                                    </Pressable>
                                    {isExpanded && (
                                        <View className = 'gap-2 border-t border-border/50 bg-secondary/30 p-2.5'>
                                            {entry.members.map(member => (
                                                <View key = { member.id } className = 'flex-row items-center gap-2'>
                                                    {member.avatarUrl ? (
                                                        <Image source = {{ uri: member.avatarUrl }} style = {{ width: 24, height: 24, borderRadius: 12 }}/>
                                                    ) : (
                                                        <View className = 'h-6 w-6 items-center justify-center rounded-full bg-secondary'>
                                                            <User size = { 12 } color = '#736E65'/>
                                                        </View>
                                                    )}
                                                    <Text className = 'text-xs text-foreground'>{member.name}</Text>
                                                </View>
                                            ))}
                                        </View>
                                    )}
                                </View>
                            )
                        }

                        return (
                            <View key = { entry.id } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2.5'>
                                {!isInProgress && <Text className = 'w-5 text-center text-xs font-bold text-muted-foreground'>{rank}°</Text>}
                                <View style = {{ backgroundColor: entry.color ?? '#DDD8CE' }} className = 'h-4 w-4 rounded-full'/>
                                {'avatarUrl' in entry && entry.avatarUrl ? (
                                    <Image source = {{ uri: entry.avatarUrl }} style = {{ width: 32, height: 32, borderRadius: 16 }}/>
                                ) : (
                                    <View className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary'>
                                        <User size = { 16 } color = '#736E65'/>
                                    </View>
                                )}
                                <Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>{entry.name}</Text>
                                {!isInProgress && entry.score != null && <Text className = 'text-sm font-bold text-foreground'>{entry.score}</Text>}
                            </View>
                        )
                    })}
                </View>
				{isCreator && isInProgress && (
                    <View className = 'mt-6 flex-row gap-3'>
                        <Button className = 'h-12 flex-1 rounded-full' onPress = { () => router.push(`/match/${match.id}/finish`) }>
                            <Text className = 'text-sm font-semibold text-primary-foreground'>Termina partita</Text>
                        </Button>
                        <Pressable onPress = { handleDelete } disabled = { isDeleting } className = 'h-12 w-12 items-center justify-center rounded-full border border-[#C45135]/40 active:border-primary/90 active:bg-primary/90'>
                            {({ pressed }) =>
                                isDeleting ? (
                                    <ActivityIndicator size = 'small' color = '#C45135'/>
                                ) : (
                                    <Trash2 size = { 18 } color = { pressed ? '#FFFFFF' : '#C45135' }/>
                                )
                            }
                        </Pressable>
                    </View>
                )}
            </ScrollView>
            {isCreator && !isInProgress && (
                <FabMenu
                    actions = { [
                        {
                            label: 'Rigioca',
                            icon: <Repeat size = { 18 } className = 'text-foreground'/>,
                            onPress: handleReplay,
                        },
                        {
                            label: 'Modifica',
                            icon: <Pencil size = { 18 } className = 'text-foreground'/>,
                            onPress: () => router.push(`/match/${match.id}/edit`),
                        },
                        {
                            label: 'Elimina',
                            icon: <Trash2 size = { 18 } className = 'text-foreground'/>,
                            onPress: handleDelete,
                        },
                    ] }
                />
            )}

            <RegisterMatchSheet ref = { registerMatchSheetRef }/>
        </View>
    )
}

export default MatchDetailScreen;