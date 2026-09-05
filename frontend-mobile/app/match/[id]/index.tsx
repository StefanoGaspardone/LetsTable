import { useMemo, useRef, useState } from 'react';
import { View, Pressable, ActivityIndicator, LayoutAnimation, Platform, UIManager, ScrollView } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Dices, Pencil, Trash2, MapPin, FileText, Trophy, Users, ChevronDown, ChevronUp, User, Repeat, Clock, Calendar } from 'lucide-react-native';

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
					userId: p.user?.id ?? null,
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
				userId: p.user?.id ?? null,
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
					<Text className = 'flex-1 font-display text-lg text-foreground' numberOfLines = { 1 }>
						{match.game.name}
					</Text>
				</Pressable>
				{isInProgress && (
					<View className = 'mt-3 flex-row items-center gap-2 self-start rounded-full border border-[#C45135]/30 bg-[#C45135]/10 px-3 py-1.5'>
						<View className = 'h-2 w-2 rounded-full bg-[#C45135]'/>
						<Text className = 'text-sm font-medium text-[#C45135]'>Partita in corso</Text>
					</View>
				)}
				<View className = 'mt-4 flex-row items-center rounded-2xl border border-border bg-white p-3 shadow-sm'>
					<View className = 'flex-1 items-center px-2'>
						<View className = 'mb-1 flex-row items-center gap-1.5'>
							<Calendar size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
							<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
								Data
							</Text>
						</View>
						<Text className = 'font-display text-base text-foreground'>{formattedDate}</Text>
					</View>
					{!isInProgress && match.durationMinutes != null && (
						<>
							<View className = 'h-8 w-[1px] bg-border'/>
							<View className = 'flex-1 items-center px-2'>
								<View className = 'mb-1 flex-row items-center gap-1.5'>
									<Clock size = { 14 } color = '#C45135' strokeWidth = { 2.5 }/>
									<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
										Durata
									</Text>
								</View>
								<Text className = 'font-display text-base text-foreground'>{formatDuration(match.durationMinutes)}</Text>
							</View>
						</>
					)}
				</View>
				{(match.place || match.notes) && (
					<View className = 'mt-4 gap-3 rounded-2xl border border-border bg-card p-3'>
						{match.place && (
							<View className = 'flex-row items-start gap-2'>
								<MapPin size = { 16 } color = '#736E65' style = {{ marginTop: 1 }}/>
								<View className = 'flex-1'>
									<Text className = 'text-xs text-muted-foreground'>Luogo</Text>
									<Text className = 'text-sm text-foreground'>{match.place}</Text>
								</View>
							</View>
						)}
						{match.place && match.notes && <View className = 'h-[0.75px] bg-border/60'/>}
						{match.notes && (
							<View className = 'flex-row items-start gap-2'>
								<FileText size = { 16 } color = '#736E65' style = {{ marginTop: 1 }}/>
								<View className = 'flex-1'>
									<Text className = 'text-xs text-muted-foreground'>Note</Text>
									<Text className = 'text-sm text-foreground'>{match.notes}</Text>
								</View>
							</View>
						)}
					</View>
				)}
				<View className = 'mb-3 mt-6 flex-row items-center gap-2'>
                    {match.isTeamBased ? <Users size = { 18 } color = '#736E65'/> : <Trophy size = { 18 } color = '#736E65'/>}
                    <Text className = 'text-sm font-semibold uppercase tracking-wide text-muted-foreground'>
                        {isInProgress ? (match.isTeamBased ? 'Squadre' : 'Giocatori') : 'Classifica'}
                    </Text>
                </View>
				{!isInProgress && sortedEntries.length > 0 && (
					<View className = 'mb-6 rounded-2xl border border-border bg-white p-4 shadow-sm'>
						<View className = 'flex-row items-end justify-center gap-2'>
							{sortedEntries.length >= 3 && (
								<View className = 'flex-1 items-center'>
									{secondPlace ? (
										<View className = 'w-full items-center'>
											{'avatarUrl' in secondPlace && secondPlace.avatarUrl ? (
												<Image source = {{ uri: secondPlace.avatarUrl }} style = {{ width: 52, height: 52, borderRadius: 26 }}/>
											) : (
												<View className = 'h-[52px] w-[52px] items-center justify-center rounded-full bg-secondary' style = {{ borderWidth: 1 }}>
													<User size = { 22 } color = { secondPlace.color ?? '#736E65' }/>
												</View>
											)}
											<Text className = 'mt-2 text-center text-xs font-semibold text-foreground' numberOfLines = { 1 }>
												{secondPlace.name}
												{'userId' in secondPlace! && secondPlace!.userId === user?.id && (
													<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
												)}
											</Text>
											{secondPlace.score != null && (
												<Text className = 'text-sm font-bold text-muted-foreground'>{secondPlace.score}</Text>
											)}
											<View style = {{ height: 44 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-slate-200'>
												<Text className = 'text-lg font-black text-slate-500'>2</Text>
											</View>
										</View>
									) : (
										<View style = {{ height: 44 }} className = 'mt-[92px] w-full rounded-t-lg bg-slate-100'/>
									)}
								</View>
							)}
							<View className = 'flex-1 items-center'>
								{firstPlace && (
									<View className = 'w-full items-center'>
										{'avatarUrl' in firstPlace && firstPlace.avatarUrl ? (
											<Image source = {{ uri: firstPlace.avatarUrl }} style = {{ width: 64, height: 64, borderRadius: 32 }}/>
										) : (
											<View className = 'h-16 w-16 items-center justify-center rounded-full bg-amber-500/15' style = {{ borderWidth: 1 }}>
												<User size = { 28 } color = '#D97706'/>
											</View>
										)}
										<Text className = 'mt-2 text-center text-sm font-bold text-foreground' numberOfLines = { 1 }>
											{firstPlace.name}
											{'userId' in firstPlace && firstPlace.userId === user?.id && (
												<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
											)}
										</Text>
										{firstPlace.score != null && (
											<Text className = 'text-base font-black text-amber-600'>{firstPlace.score}</Text>
										)}
										<View style = {{ height: 64 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-amber-500'>
											<Text className = 'text-xl font-black text-white'>1</Text>
										</View>
									</View>
								)}
							</View>
							{sortedEntries.length === 2 && (
								<View className = 'flex-1 items-center'>
									<View className = 'w-full items-center'>
										{'avatarUrl' in secondPlace! && secondPlace!.avatarUrl ? (
											<Image source = {{ uri: secondPlace!.avatarUrl }} style = {{ width: 52, height: 52, borderRadius: 26 }}/>
										) : (
											<View className = 'h-[52px] w-[52px] items-center justify-center rounded-full bg-secondary' style = {{ borderWidth: 1 }}>
												<User size = { 22 } color = { secondPlace!.color ?? '#736E65' }/>
											</View>
										)}
										<Text className = 'mt-2 text-center text-xs font-semibold text-foreground' numberOfLines = { 1 }>
											{secondPlace!.name}
											{'userId' in secondPlace! && secondPlace!.userId === user?.id && (
												<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
											)}
										</Text>
										{secondPlace!.score != null && (
											<Text className = 'text-sm font-bold text-muted-foreground'>{secondPlace!.score}</Text>
										)}
										<View style = {{ height: 44 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-slate-200'>
											<Text className = 'text-lg font-black text-slate-500'>2</Text>
										</View>
									</View>
								</View>
							)}
							{sortedEntries.length >= 3 && (
								<View className = 'flex-1 items-center'>
									{thirdPlace ? (
										<View className = 'w-full items-center'>
											{'avatarUrl' in thirdPlace && thirdPlace.avatarUrl ? (
												<Image source = {{ uri: thirdPlace.avatarUrl }} style = {{ width: 52, height: 52, borderRadius: 26 }}/>
											) : (
												<View className = 'h-[52px] w-[52px] items-center justify-center rounded-full bg-secondary' style = {{ borderWidth: 1 }}>
													<User size = { 22 } color = { thirdPlace.color ?? '#736E65' }/>
												</View>
											)}
											<Text className = 'mt-2 text-center text-xs font-semibold text-foreground' numberOfLines = { 1 }>
												{thirdPlace.name}
												{'userId' in thirdPlace && thirdPlace.userId === user?.id && (
													<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
												)}
											</Text>
											{thirdPlace.score != null && (
												<Text className = 'text-sm font-bold text-muted-foreground'>{thirdPlace.score}</Text>
											)}
											<View style = {{ height: 32 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-amber-800/20'>
												<Text className = 'text-lg font-black text-amber-800'>3</Text>
											</View>
										</View>
									) : (
										<View style = {{ height: 32 }} className = 'mt-[104px] w-full rounded-t-lg bg-slate-100'/>
									)}
								</View>
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
                                                    <Text className = 'text-xs text-foreground'>
														{member.name}
														{member.userId === user?.id && <Text className = 'text-muted-foreground font-semibold'> (io)</Text>}
													</Text>
                                                </View>
                                            ))}
                                        </View>
                                    )}
                                </View>
                            )
                        }

                        return (
                            <View key = { entry.id } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2.5'>
                                {!isInProgress && (
									<View className = 'h-6 w-6 items-center justify-center rounded-full bg-secondary'>
										<Text className = 'text-xs font-bold text-muted-foreground'>{rank}</Text>
									</View>
								)}
                                <View style = {{ backgroundColor: entry.color ?? '#DDD8CE' }} className = 'h-4 w-4 rounded-full'/>
                                {'avatarUrl' in entry && entry.avatarUrl ? (
                                    <Image source = {{ uri: entry.avatarUrl }} style = {{ width: 32, height: 32, borderRadius: 16 }}/>
                                ) : (
                                    <View className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary'>
                                        <User size = { 16 } color = '#736E65'/>
                                    </View>
                                )}
                                <Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>
									{entry.name}
									{'userId' in entry && entry.userId === user?.id && (
										<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
									)}
								</Text>
								{!isInProgress && (
									<View className = 'h-6 w-6 items-center justify-center rounded-full bg-secondary'>
										<Text className = 'text-xs font-bold text-muted-foreground'>{rank}</Text>
									</View>
								)}
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