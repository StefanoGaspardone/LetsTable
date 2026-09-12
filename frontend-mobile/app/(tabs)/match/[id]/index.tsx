import { useMemo, useRef, useState } from 'react';
import { View, Pressable, ActivityIndicator, ScrollView } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Image } from 'expo-image';
import { Dices, Pencil, Trash2, MapPin, FileText, Trophy, Users, ChevronRight, Repeat, Clock, Calendar } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import FabMenu from '@/components/common/fab-menu';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/common/register-match-sheet';
import AppBottomSheet from '@/components/common/app-bottom-sheet';
import MeepleIllustration from '@/components/common/meeple-illustration';

import { getMatchById, deleteMatch } from '@/api/match';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';

import { formatDuration } from '@/lib/time';

import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { getAvatarUrl } from '@/lib/file';

interface TeamEntry {
	id: string;
	name: string;
	color: string;
	score: number;
	isWinner: boolean;
	members: {
		id: string;
		name: string;
		avatarId: string | null | undefined;
		userId: string | null;
	}[];
}

const renderPodiumAvatar = (entry: any, size: number) => {
	const isTeam = 'members' in entry;

	if(isTeam) {
		return (
			<Image source = {{ uri: getAvatarUrl(null, entry.name ?? '') }} style = {{ width: size, height: size, borderRadius: size / 2, backgroundColor: entry.color }} contentFit = 'cover'/>
		)
	}

	return (
		<Image source = {{ uri: getAvatarUrl(entry.avatarId ?? null, entry.name ?? '') }} style = {{ width: size, height: size, borderRadius: size / 2 }} contentFit = 'cover' transition = { 200 }/>
	)
}

const renderPodiumMeeple = (entry: any, size: number) => (
	<MeepleIllustration size = { size } color = { entry.color } outlined/>
)

const MatchDetailScreen = () => {
    const { id } = useLocalSearchParams<{ id: string }>();
    const { user } = useAuth();
    const { showToast } = useToast();
    const { confirm } = useConfirmDialog();
    const queryClient = useQueryClient();

    const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);
    const teamMembersSheetRef = useRef<any>(null);

    const [isDeleting, setIsDeleting] = useState(false);
	const [selectedTeam, setSelectedTeam] = useState<TeamEntry | null>(null);

    const { data: match, isLoading } = useQuery({
        queryKey: ['matches', 'detail', id],
        queryFn: () => getMatchById(id),
    });

	useRefetchOnFocus(['matches', 'detail', id]);

    const isCreator = match?.createdBy?.id === user?.id;
    const isInProgress = match?.durationMinutes == null;

    const handleDelete = async () => {
        const ok = await confirm({
            title: 'Elimina partita',
            message: 'Vuoi eliminare questa partita? L\'azione è irreversibile.',
            confirmLabel: 'Elimina',
            destructive: true,
        });
        
		if(!ok) {
			return;
		}

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
					avatarId: p.user?.avatarId,
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
				avatarId: p.user?.avatarId,
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
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = '#C45135'/>
			</View>
		)
	}

    const formattedDate = new Date(match.playedAt).toLocaleDateString('it-IT', {
        day: 'numeric',
        month: 'long',
        year: 'numeric',
    });

	const getSectionTitle = () => {
		if(!isInProgress) return 'Classifica';

		return match.isTeamBased ? 'Squadre' : 'Giocatori';
	}

    const firstPlace = sortedEntries[0];
    const secondPlace = sortedEntries[1];
    const thirdPlace = sortedEntries[2];
    const remainingEntries = sortedEntries.slice(3);

    return (
        <View className = 'flex-1 bg-background'>
            <ScreenHeader title = 'Dettaglio Partita' leftElement = { <BackButton/> }/>
            <ScrollView className = 'flex-1' contentContainerStyle = {{ padding: 16, paddingBottom: 24 }}>
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
                        {getSectionTitle()}
                    </Text>
                </View>
				{!isInProgress && sortedEntries.length > 0 && (
					<View className = 'mb-2 rounded-2xl border border-border bg-white p-4 shadow-sm'>
						<View className = 'flex-row items-end justify-center gap-2'>
							{sortedEntries.length >= 3 && (
								<View className = 'flex-1 items-center'>
									{secondPlace ? (
										<Pressable onPress = { () => { if('members' in secondPlace) { setSelectedTeam(secondPlace); teamMembersSheetRef.current?.present(); } } } className = 'w-full items-center active:scale-[0.98] active:opacity-75'>
											{renderPodiumAvatar(secondPlace, 52)}
											<View className = 'mt-1 flex-row items-center gap-1'>
												{renderPodiumMeeple(secondPlace, 14)}
												<Text className = 'text-center text-sm font-semibold text-foreground' numberOfLines = { 1 }>
													{secondPlace.name}
												</Text>
											</View>
											{'score' in secondPlace && secondPlace.score != null && (
												<Text className = 'text-lg font-bold text-muted-foreground'>{secondPlace.score}</Text>
											)}
											<View style = {{ height: 44 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-slate-200'>
												<Text className = 'text-lg font-black text-slate-500'>2°</Text>
											</View>
										</Pressable>
									) : (
										<View style = {{ height: 44 }} className = 'mt-[92px] w-full rounded-t-lg bg-slate-100'/>
									)}
								</View>
							)}
							<View className = 'flex-1 items-center'>
								{firstPlace && (
									<Pressable onPress = { () => { if('members' in firstPlace) { setSelectedTeam(firstPlace); teamMembersSheetRef.current?.present(); } } } className = 'w-full items-center active:scale-[0.98] active:opacity-75'>
										{renderPodiumAvatar(firstPlace, 64)}
										<View className = 'mt-1 flex-row items-center gap-1'>
											{renderPodiumMeeple(firstPlace, 16)}
											<Text className = 'text-center text-base font-bold text-foreground' numberOfLines = { 1 }>
												{firstPlace.name}
											</Text>
										</View>
										{'score' in firstPlace && firstPlace.score != null && (
											<Text className = 'text-xl font-black text-amber-600'>{firstPlace.score}</Text>
										)}
										<View style = {{ height: 64 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-amber-500'>
											<Text className = 'text-xl font-black text-white'>1°</Text>
										</View>
									</Pressable>
								)}
							</View>
							{sortedEntries.length === 2 && (
								<View className = 'flex-1 items-center'>
									<Pressable onPress = { () => { if('members' in secondPlace!) { setSelectedTeam(secondPlace); teamMembersSheetRef.current?.present(); } } } className = 'w-full items-center active:scale-[0.98] active:opacity-75'>
										{renderPodiumAvatar(secondPlace!, 52)}
										<View className = 'mt-1 flex-row items-center gap-1'>
											{renderPodiumMeeple(secondPlace!, 14)}
											<Text className = 'text-center text-sm font-semibold text-foreground' numberOfLines = { 1 }>
												{secondPlace!.name}
											</Text>
										</View>
										{'score' in secondPlace! && secondPlace!.score != null && (
											<Text className = 'text-lg font-bold text-muted-foreground'>{secondPlace!.score}</Text>
										)}
										<View style = {{ height: 44 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-slate-200'>
											<Text className = 'text-lg font-black text-slate-500'>2°</Text>
										</View>
									</Pressable>
								</View>
							)}
							{sortedEntries.length >= 3 && (
								<View className = 'flex-1 items-center'>
									{thirdPlace ? (
										<Pressable onPress = { () => { if('members' in thirdPlace) { setSelectedTeam(thirdPlace); teamMembersSheetRef.current?.present(); } } } className = 'w-full items-center active:scale-[0.98] active:opacity-75'>
											{renderPodiumAvatar(thirdPlace, 52)}
											<View className = 'mt-1 flex-row items-center gap-1'>
												{renderPodiumMeeple(thirdPlace, 14)}
												<Text className = 'text-center text-sm font-semibold text-foreground' numberOfLines = { 1 }>
													{thirdPlace.name}
												</Text>
											</View>
											{'score' in thirdPlace && thirdPlace.score != null && (
												<Text className = 'text-lg font-bold text-muted-foreground'>{thirdPlace.score}</Text>
											)}
											<View style = {{ height: 32 }} className = 'mt-2 w-full items-center justify-center rounded-t-lg bg-amber-800/20'>
												<Text className = 'text-lg font-black text-amber-800'>3°</Text>
											</View>
										</Pressable>
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
							return (
                                <Pressable key = { entry.id } onPress = { () => { setSelectedTeam(entry); teamMembersSheetRef.current?.present(); } } className = 'flex-row items-center gap-3 rounded-xl border border-border bg-card p-2.5 active:scale-[0.98] active:opacity-75'>
                                    {!isInProgress && (
										<View className = 'h-7 w-7 items-center justify-center rounded-full bg-secondary'>
											<Text className = 'text-sm font-bold text-muted-foreground'>{rank}°</Text>
										</View>
									)}
                                   	
                                <Image source = {{ uri: getAvatarUrl(null, entry.name ?? '') }} style = {{ width: 36, height: 36, borderRadius: 100 }} contentFit = 'cover'/>
									<View className = 'flex-1'>
										<View className = 'flex-row items-center gap-1.5'>
											<MeepleIllustration size = { 16 } color = { entry.color } outlined/>
											<Text className = 'text-sm font-semibold text-foreground' numberOfLines = { 1 }>{entry.name}</Text>
										</View>
										<Text className = 'text-xs text-muted-foreground'>{entry.members.length} membri</Text>
									</View>
                                    {!isInProgress && entry.score && (
									<View className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary'>
										<Text className = 'text-sm font-bold text-muted-foreground'>{entry.score}</Text>
									</View>
								)}
                                    <ChevronRight size = { 16 } color = '#736E65'/>
                                </Pressable>
                            )
                        }

                        return (
                            <View key = { entry.id } className = 'flex-row items-center gap-2 rounded-xl border border-border bg-card p-2.5'>
                                {!isInProgress && (
									<View className = 'h-7 w-7 items-center justify-center rounded-full bg-secondary'>
										<Text className = 'text-sm font-bold text-muted-foreground'>{rank}°</Text>
									</View>
								)}
                                <View style = {{ backgroundColor: entry.color ?? '#DDD8CE' }} className = 'h-5 w-5 rounded-full border border-black'/>
                                <Image source = {{ uri: getAvatarUrl(entry.avatarId ?? null, entry.name ?? '') }} style = {{ width: 36, height: 36, borderRadius: 100 }} contentFit = 'cover'/>
                                <Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>
									{entry.name}
									{'userId' in entry && entry.userId === user?.id && (
										<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
									)}
								</Text>
								{!isInProgress && (
									<View className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary'>
										<Text className = 'text-sm font-bold text-muted-foreground'>{entry.score}</Text>
									</View>
								)}
                            </View>
                        )
                    })}
                </View>
				{isCreator && isInProgress && (
                    <View className = 'mt-6 flex-row gap-3'>
                        <Button className = 'h-12 flex-1 rounded-full active:scale-[0.98]' onPress = { () => router.push(`/match/${match.id}/finish`) }>
                            <Text className = 'text-sm font-semibold text-primary-foreground'>Termina partita</Text>
                        </Button>
                        <Pressable onPress = { handleDelete } disabled = { isDeleting } className = 'h-12 w-12 items-center justify-center rounded-full border border-[#C45135]/40 active:border-primary/90 active:bg-primary/90 active:scale-[0.98]'>
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
            {!isInProgress && (
				<FabMenu
					actions = { [
						{
							label: 'Rigioca',
							icon: <Repeat size = { 18 } className = 'text-foreground'/>,
							onPress: handleReplay,
						},
						...(isCreator
							? [
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
								]
							: []),
					] }
				/>
			)}
            <RegisterMatchSheet ref = { registerMatchSheetRef }/>
            <AppBottomSheet ref = { teamMembersSheetRef }>
				<View className = 'px-4 pb-6 pt-2'>
					{selectedTeam && (
						<>
							<View className = 'mb-4 flex-row items-center gap-3'>
								<View style = {{ width: 40, height: 40, borderRadius: 20, backgroundColor: selectedTeam.color }} className = 'items-center justify-center'>
									<Text className = 'text-base font-bold text-white'>{selectedTeam.name.charAt(0).toUpperCase()}</Text>
								</View>
								<Text className = 'font-display text-lg text-foreground'>{selectedTeam.name}</Text>
							</View>
							<View className = 'flex-row flex-wrap'>
								{selectedTeam.members.map((member: any) => (
									<View key = { member.id } style = {{ width: '25%', padding: 4 }}>
										<View className = 'items-center gap-1.5 py-3 rounded-2xl border border-border bg-card active:scale-[0.98] active:opacity-75'>
											<Image source = {{ uri: getAvatarUrl(member.avatarId ?? null, member.name ?? '') }} style = {{ width: 56, height: 56, borderRadius: 28 }} contentFit = 'cover'/>
											<Text className = 'text-center text-sm font-medium text-foreground' numberOfLines = { 1 }>
												{member.name}
												{member.userId === user?.id && (
													<Text className = 'text-xs font-semibold text-primary'> (io)</Text>
												)}
											</Text>
										</View>
									</View>
								))}
							</View>
						</>
					)}
				</View>
			</AppBottomSheet>
        </View>
    )
}

export default MatchDetailScreen;