import { useMemo, useRef, useState } from 'react';
import { View, Pressable, ActivityIndicator, ScrollView } from 'react-native';
import { useLocalSearchParams } from 'expo-router';
import { RefreshControl } from 'react-native-gesture-handler';
import { BottomSheetModal } from '@gorhom/bottom-sheet';
import { Image } from 'expo-image';
import { Dices, Pencil, Trash2, MapPin, FileText, Trophy, Users, ChevronRight, Repeat, Clock, Calendar, Puzzle } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import FabMenu from '@/components/common/fab-menu';
import RegisterMatchSheet, { RegisterMatchSheetRef } from '@/components/matches/register-match-sheet';
import AppBottomSheet from '@/components/common/app-bottom-sheet';
import MeepleIllustration from '@/components/common/meeple-illustration';

import { useAuth } from '@/contexts/auth-context';
import { useToast } from '@/contexts/toast-context';
import { useConfirmDialog } from '@/contexts/confirm-dialog-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { formatFullDate } from '@/lib/date';
import { formatDuration } from '@/lib/time';
import { getAvatarUrl } from '@/lib/file';

import { useDeleteMatch, useMatchDetail } from '@/hooks/use-match';
import { useRefetchOnFocus } from '@/hooks/use-refetch-on-focus';
import { usePullToRefresh } from '@/hooks/use-pull-to-refresh';
import { useThemeColors } from '@/hooks/use-theme-colors';

interface TeamMember {
	id: string;
	name: string;
	avatarId: string | null | undefined;
	userId: string | null;
}

interface TeamEntry {
	id: string;
	name: string;
	color: string;
	score: number | null;
	isWinner: boolean;
	members: TeamMember[];
}

interface PlayerEntry {
	id: string;
	name: string;
	avatarId: string | null | undefined;
	color: string | null;
	score: number | null;
	isWinner: boolean | null;
	userId: string | null;
}

type MatchEntry = TeamEntry | PlayerEntry;

const isTeamEntry = (entry: MatchEntry): entry is TeamEntry => 'members' in entry;

const PODIUM = {
	first: {
		avatarSize: 64,
		meepleSize: 16,
		nameClassName: 'text-center text-base font-bold text-foreground',
		scoreClassName: 'text-xl font-black text-amber-600',
		barHeight: 64,
		barClassName: 'bg-amber-500',
		labelClassName: 'text-xl font-black text-white',
		label: '1°',
	},
	second: {
		avatarSize: 52,
		meepleSize: 14,
		nameClassName: 'text-center text-sm font-semibold text-foreground',
		scoreClassName: 'text-lg font-bold text-muted-foreground',
		barHeight: 44,
		barClassName: 'bg-slate-200',
		labelClassName: 'text-lg font-black text-slate-500',
		label: '2°',
	},
	third: {
		avatarSize: 52,
		meepleSize: 14,
		nameClassName: 'text-center text-sm font-semibold text-foreground',
		scoreClassName: 'text-lg font-bold text-muted-foreground',
		barHeight: 32,
		barClassName: 'bg-amber-800/20 dark:bg-amber-600/30',
		labelClassName: 'text-lg font-black text-amber-800 dark:text-amber-400',
		label: '3°',
	},
}

const renderPodiumAvatar = (entry: MatchEntry, size: number) => {
	if(isTeamEntry(entry)) {
		return (
			<Image source = {{ uri: getAvatarUrl(null, entry.name ?? '') }} style = {{ width: size, height: size, borderRadius: size / 2, backgroundColor: entry.color }} contentFit = 'cover'/>
		)
	}

	return (
		<Image source = {{ uri: getAvatarUrl(entry.avatarId ?? null, entry.name ?? '') }} style = {{ width: size, height: size, borderRadius: size / 2 }} contentFit = 'cover' transition = { 200 }/>
	)
}

const renderPodiumMeeple = (entry: MatchEntry, size: number) => (
	<MeepleIllustration size = { size } color = { entry.color ?? '#FFFFFF' } outlined/>
)

interface PodiumSlotProps {
	entry: MatchEntry;
	avatarSize: number;
	meepleSize: number;
	nameClassName: string;
	scoreClassName: string;
	barHeight: number;
	barClassName: string;
	labelClassName: string;
	label: string;
	onPress: (entry: MatchEntry) => void;
}

const PodiumSlot = ({ entry, avatarSize, meepleSize, nameClassName, scoreClassName, barHeight, barClassName, labelClassName, label, onPress }: PodiumSlotProps) => (
	<View className = 'flex-1 items-center'>
		<Pressable onPress = { () => onPress(entry) } className = 'w-full items-center active:scale-[0.98] active:opacity-75'>
			{renderPodiumAvatar(entry, avatarSize)}
			<View className = 'mt-1 flex-row items-center gap-1'>
				{renderPodiumMeeple(entry, meepleSize)}
				<Text className = { nameClassName } numberOfLines = { 1 }>
					{entry.name}
				</Text>
			</View>
			{entry.score != null && (
				<Text className = { scoreClassName }>{entry.score}</Text>
			)}
			<View style = {{ height: barHeight }} className = { `mt-2 w-full items-center justify-center rounded-t-lg ${barClassName}` }>
				<Text className = { labelClassName }>{label}</Text>
			</View>
		</Pressable>
	</View>
)

const sortEntries = <T extends MatchEntry>(entries: T[]): T[] => {
	return [...entries].sort((a, b) => {
		if(a.isWinner) return -1;
		if(b.isWinner) return 1;

		return (b.score ?? 0) - (a.score ?? 0);
	});
}

const MatchDetailScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();

	const { user } = useAuth();
	const { showToast } = useToast();
	const { confirm } = useConfirmDialog();
	const router = useNavigationStack();

	const { colors } = useThemeColors();

	const registerMatchSheetRef = useRef<RegisterMatchSheetRef>(null);
	const teamMembersSheetRef = useRef<BottomSheetModal>(null);

	const [selectedTeam, setSelectedTeam] = useState<TeamEntry | null>(null);

	const { data: match, isLoading } = useMatchDetail(id);
	const deleteMatchMutation = useDeleteMatch();

	useRefetchOnFocus(['matches', 'detail', id]);

	const { refreshing, onRefresh } = usePullToRefresh([['matches', 'detail', id]]);

	const isCreator = match?.createdBy?.id === user?.id;
	const isInProgress = match?.durationMinutes == null;
	const isDeleting = deleteMatchMutation.isPending;

	const handleDelete = async () => {
		const ok = await confirm({
			title: 'Elimina partita',
			message: 'Vuoi eliminare questa partita? L\'azione è irreversibile.',
			confirmLabel: 'Elimina',
			destructive: true,
		});

		if(!ok) return;

		deleteMatchMutation.mutate(id, {
			onSuccess: () => router.replace('/(tabs)/home'),
			onError: (error: any) => showToast(error?.response?.data?.message ?? 'Errore durante l\'eliminazione', 'error'),
		});
	}

	const openTeam = (team: TeamEntry) => {
		setSelectedTeam(team);
		teamMembersSheetRef.current?.present();
	}

	const handlePlayerPress = async (playerUserId: string | null, playerName: string) => {
		if(playerUserId) {
			teamMembersSheetRef.current?.dismiss();

			if(playerUserId === user?.id) router.push('/(tabs)/profile');
			else router.push(`/user/${playerUserId}`);

			return;
		}

		await confirm({
			title: 'Giocatore ospite',
			message: `${playerName} ha giocato come ospite. Se vuole tracciare le sue partite e la sua collezione, può creare un account gratuito su Let's Table!`,
			confirmLabel: 'Ho capito',
			infoOnly: true,
		});
	}

	const handleEntryPress = (entry: MatchEntry) => {
		if(isTeamEntry(entry)) openTeam(entry);
		else handlePlayerPress(entry.userId, entry.name);
	}

	const handleReplay = () => {
		if(!match) return;

		registerMatchSheetRef.current?.present(
			{
				id: match.game.id,
				name: match.game.name,
				thumbnailUrl: match.game.thumbnailUrl,
				bggId: match.game.bggId,
			},
			match.expansionsUsed.map(expansion => ({
				id: expansion.id!,
				bggId: expansion.bggId,
				name: expansion.name,
				thumbnailUrl: expansion.thumbnailUrl,
			}))
		);
	}

	const sortedEntries = useMemo((): MatchEntry[] => {
		if(!match) return [];

		if(match.isTeamBased) {
			return sortEntries((match.teams ?? []).map(team => ({
				id: team.id,
				name: team.name ?? 'Squadra senza nome',
				color: team.color,
				score: team.score,
				isWinner: team.isWinner,
				members: team.players.map(player => ({
					id: player.id,
					name: player.user?.username ?? player.guestName ?? 'Sconosciuto',
					avatarId: player.user?.avatarId,
					userId: player.user?.id ?? null,
				})),
			})));
		}

		return sortEntries((match.players ?? []).map(player => ({
			id: player.id,
			name: player.user?.username ?? player.guestName ?? 'Sconosciuto',
			avatarId: player.user?.avatarId,
			color: player.color,
			score: player.score,
			isWinner: player.isWinner,
			userId: player.user?.id ?? null,
		})));
	}, [match]);

	if(isLoading || !match) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = { colors.primary }/>
			</View>
		)
	}

	const sectionTitle = !isInProgress ? 'Classifica' : match.isTeamBased ? 'Squadre' : 'Giocatori';

	const hasScores = sortedEntries.some(entry => entry.score != null);
	const isWinnerOnlyLayout = match.isTeamBased && !hasScores;

	const winnerEntries = isWinnerOnlyLayout ? sortedEntries.filter(entry => entry.isWinner) : [];
	const singleWinner = winnerEntries.length === 1 ? winnerEntries[0] : null;
	const nonWinnerEntries = isWinnerOnlyLayout ? sortedEntries.filter(entry => !entry.isWinner) : [];

	const ranks: number[] = [];
	sortedEntries.forEach((entry, index) => {
		if(index === 0) {
			ranks.push(1);
			return;
		}

		const prevScore = sortedEntries[index - 1].score ?? 0;
		const curScore = entry.score ?? 0;

		ranks.push(curScore === prevScore ? ranks[index - 1] : index + 1);
	});

	const topSliceSize = Math.min(3, sortedEntries.length);
	const hasTopTie = topSliceSize > 0 && new Set(ranks.slice(0, topSliceSize)).size < topSliceSize;

	const showPhysicalPodium = !isWinnerOnlyLayout && hasScores && sortedEntries.length > 0 && !hasTopTie;
	const showRankedList = !isWinnerOnlyLayout && hasScores && hasTopTie;

	const [firstPlace, secondPlace, thirdPlace] = sortedEntries;
	const remainingEntries = sortedEntries.slice(3);

	let mainListEntries: MatchEntry[];
	let getRank: (index: number) => number | null;

	if(isInProgress) {
		mainListEntries = sortedEntries;
		getRank = () => null;
	} else if(isWinnerOnlyLayout) {
		mainListEntries = nonWinnerEntries;
		getRank = () => null;
	} else if(showPhysicalPodium) {
		mainListEntries = remainingEntries;
		getRank = index => ranks[index + 3];
	} else if(showRankedList) {
		mainListEntries = sortedEntries;
		getRank = index => ranks[index];
	} else {
		mainListEntries = remainingEntries;
		getRank = index => index + 4;
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Dettaglio Partita' leftElement = { <BackButton/> }/>
			<ScrollView className = 'flex-1' contentContainerStyle = {{ padding: 16, paddingBottom: 24 }} refreshControl = { <RefreshControl refreshing = { refreshing } onRefresh = { onRefresh } tintColor = { colors.primary } colors = { [colors.primary] } progressBackgroundColor = { colors.card }/> }>
				<Pressable className = 'flex-row items-center gap-3 rounded-2xl border border-border bg-card p-2 active:scale-[0.98] active:opacity-75' onPress = { () => router.push(`/game/${match.game.bggId}`) }>
					<View style = {{ width: 56, height: 56 }} className = 'overflow-hidden rounded-xl bg-secondary'>
						{match.game.thumbnailUrl ? (
							<Image source = {{ uri: match.game.thumbnailUrl }} style = {{ width: 56, height: 56 }} contentFit = 'cover'/>
						) : (
							<View className = 'h-full w-full items-center justify-center'>
								<Dices size = { 22 } color = { colors.mutedForeground }/>
							</View>
						)}
					</View>
					<Text className = 'flex-1 font-display text-lg text-foreground' numberOfLines = { 1 }>
						{match.game.name}
					</Text>
					<View className = 'justify-center'>
						<ChevronRight size = { 18 } color = { colors.mutedForeground }/>
					</View>
				</Pressable>
				{isInProgress && (
					<View className = 'mt-3 flex-row items-center gap-2 self-start rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5'>
						<View className = 'h-2 w-2 rounded-full bg-primary'/>
						<Text className = 'text-sm font-medium text-primary'>Partita in corso</Text>
					</View>
				)}
				{isInProgress && !isCreator && (
					<View className = 'mt-3 flex-row items-start gap-2.5 rounded-2xl border border-border bg-secondary p-3'>
						<Clock size = { 16 } color = { colors.mutedForeground } style = {{ marginTop: 1 }}/>
						<Text className = 'flex-1 text-sm text-muted-foreground'>
							Questa partita è ancora in corso. Chiedi a <Text className = 'font-semibold text-sm text-foreground'>{match.createdBy.username}</Text> di terminarla e segnare i risultati.
						</Text>
					</View>
				)}
				<View className = 'mt-4 flex-row items-center rounded-2xl border border-border bg-card p-3 shadow-sm'>
					<View className = 'flex-1 items-center px-2'>
						<View className = 'mb-1 flex-row items-center gap-1.5'>
							<Calendar size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
							<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
								Data
							</Text>
						</View>
						<Text className = 'font-display text-base text-foreground'>{formatFullDate(match.playedAt)}</Text>
					</View>
					{!isInProgress && match.durationMinutes != null && (
						<>
							<View className = 'h-8 w-[1px] bg-border'/>
							<View className = 'flex-1 items-center px-2'>
								<View className = 'mb-1 flex-row items-center gap-1.5'>
									<Clock size = { 14 } color = { colors.primary } strokeWidth = { 2.5 }/>
									<Text className = 'font-sans-bold text-xs uppercase tracking-wider text-muted-foreground'>
										Durata
									</Text>
								</View>
								<Text className = 'font-display text-base text-foreground'>{formatDuration(match.durationMinutes)}</Text>
							</View>
						</>
					)}
				</View>
				{(!!match.place || !!match.notes) && (
					<View className = 'mt-4 gap-3 rounded-2xl border border-border bg-card p-3'>
						{!!match.place && (
							<View className = 'flex-row items-start gap-2'>
								<MapPin size = { 16 } color = { colors.mutedForeground } style = {{ marginTop: 1 }}/>
								<View className = 'flex-1'>
									<Text className = 'text-xs text-muted-foreground'>Luogo</Text>
									<Text className = 'text-sm text-foreground'>{match.place}</Text>
								</View>
							</View>
						)}
						{!!match.place && !!match.notes && <View className = 'h-[0.75px] bg-border/60'/>}
						{!!match.notes && (
							<View className = 'flex-row items-start gap-2'>
								<FileText size = { 16 } color = { colors.mutedForeground } style = {{ marginTop: 1 }}/>
								<View className = 'flex-1'>
									<Text className = 'text-xs text-muted-foreground'>Note</Text>
									<Text className = 'text-sm text-foreground'>{match.notes}</Text>
								</View>
							</View>
						)}
					</View>
				)}
				{match.expansionsUsed.length > 0 && (
					<View className = 'mt-4 rounded-2xl border border-border bg-card p-3'>
						<View className = 'mb-2 flex-row items-center gap-2'>
							<Puzzle size = { 16 } color = { colors.mutedForeground }/>
							<Text className = 'text-xs text-muted-foreground'>Espansioni usate</Text>
						</View>
						<View className = 'gap-2'>
							{match.expansionsUsed.map(expansion => (
								<Pressable key = { expansion.id } onPress = { () => router.push(`/game/${expansion.bggId}`) } className = 'flex-row items-center gap-2.5 rounded-xl bg-secondary p-2 active:opacity-75 active:scale-[0.98]'>
									<View style = {{ width: 40, height: 40 }} className = 'overflow-hidden rounded-xl bg-card'>
										{expansion.thumbnailUrl ? (
											<Image source = {{ uri: expansion.thumbnailUrl }} style = {{ width: 40, height: 40 }} contentFit = 'cover'/>
										) : (
											<View className = 'h-full w-full items-center justify-center'>
												<Dices size = { 14 } color = { colors.mutedForeground }/>
											</View>
										)}
									</View>
									<Text className = 'flex-1 text-sm text-foreground font-medium' numberOfLines = { 1 }>
										{expansion.name}
									</Text>
									<ChevronRight size = { 14 } color = { colors.mutedForeground }/>
								</Pressable>
							))}
						</View>
					</View>
				)}
				<View className = 'mb-3 mt-6 flex-row items-center gap-2'>
					{match.isTeamBased ? <Users size = { 18 } color = { colors.mutedForeground }/> : <Trophy size = { 18 } color = { colors.mutedForeground }/>}
					<Text className = 'text-sm font-semibold uppercase tracking-wide text-muted-foreground'>
						{sectionTitle}
					</Text>
				</View>
				{!isInProgress && isWinnerOnlyLayout && singleWinner && (
					<View className = 'mb-2 items-center rounded-2xl border border-border bg-card px-4 py-6 shadow-sm'>
						<Pressable onPress = { () => handleEntryPress(singleWinner) } className = 'items-center active:scale-[0.98] active:opacity-75'>
							<Trophy size = { 28 } color = '#F59E0B'/>
							<Text className = 'mb-3 mt-1 text-xs font-bold uppercase tracking-widest text-amber-600'>Vincitore</Text>
							{renderPodiumAvatar(singleWinner, 88)}
							<View className = 'mt-3 flex-row items-center gap-1.5'>
								{renderPodiumMeeple(singleWinner, 20)}
								<Text className = 'text-center font-display text-xl text-foreground' numberOfLines = { 1 }>
									{singleWinner.name}
								</Text>
							</View>
							{isTeamEntry(singleWinner) && (
								<Text className = 'mt-1 text-xs text-muted-foreground'>{singleWinner.members.length} membri</Text>
							)}
						</Pressable>
					</View>
				)}
				{!isInProgress && isWinnerOnlyLayout && winnerEntries.length > 1 && (
					<View className = 'mb-2 rounded-2xl border border-border bg-card p-4 shadow-sm'>
						<View className = 'flex-row flex-wrap items-center justify-center gap-4'>
							{winnerEntries.map(winner => (
								<Pressable key = { winner.id } onPress = { () => handleEntryPress(winner) } className = 'items-center active:scale-[0.98] active:opacity-75'>
									{renderPodiumAvatar(winner, 64)}
									<View className = 'mt-1 flex-row items-center gap-1'>
										{renderPodiumMeeple(winner, 16)}
										<Text className = 'text-center text-base font-bold text-foreground' numberOfLines = { 1 }>
											{winner.name}
										</Text>
									</View>
									<View style = {{ height: 44 }} className = 'mt-2 w-32 items-center justify-center rounded-t-lg bg-amber-500'>
										<Trophy size = { 28 } color = '#FFFFFF'/>
									</View>
								</Pressable>
							))}
						</View>
					</View>
				)}
				{!isInProgress && showPhysicalPodium && (
					<View className = 'mb-2 rounded-2xl border border-border bg-card p-4 shadow-sm'>
						<View className = 'flex-row items-end justify-center gap-2'>
							{thirdPlace && secondPlace && <PodiumSlot entry = { secondPlace } { ...PODIUM.second } onPress = { handleEntryPress }/>}
							{firstPlace && <PodiumSlot entry = { firstPlace } { ...PODIUM.first } onPress = { handleEntryPress }/>}
							{!thirdPlace && secondPlace && <PodiumSlot entry = { secondPlace } { ...PODIUM.second } onPress = { handleEntryPress }/>}
							{thirdPlace && <PodiumSlot entry = { thirdPlace } { ...PODIUM.third } onPress = { handleEntryPress }/>}
						</View>
					</View>
				)}
				<View className = 'gap-2'>
					{mainListEntries.map((entry, index) => {
						const rank = getRank(index);

						if(isTeamEntry(entry)) {
							return (
								<Pressable key = { entry.id } onPress = { () => openTeam(entry) } className = { `flex-row items-center gap-3 rounded-xl border p-2.5 active:scale-[0.98] active:opacity-75 ${!isInProgress && entry.isWinner ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
									{!isInProgress && rank != null && (
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
									{!isInProgress && entry.score != null && (
										<View className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary'>
											<Text className = 'text-sm font-bold text-muted-foreground'>{entry.score}</Text>
										</View>
									)}
									<ChevronRight size = { 16 } color = { colors.mutedForeground }/>
								</Pressable>
							)
						}

						return (
							<Pressable key = { entry.id } onPress = { () => handlePlayerPress(entry.userId, entry.name) } className = { `flex-row items-center gap-2 rounded-xl border p-2.5 active:scale-[0.98] active:opacity-75 ${!isInProgress && entry.isWinner ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
								{!isInProgress && rank != null && (
									<View className = 'h-7 w-7 items-center justify-center rounded-full bg-secondary'>
										<Text className = 'text-sm font-bold text-muted-foreground'>{rank}°</Text>
									</View>
								)}
								<MeepleIllustration size = { 16 } color = { entry.color ?? '#FFFFFF' } outlined/>
								<Image source = {{ uri: getAvatarUrl(entry.avatarId ?? null, entry.name ?? '') }} style = {{ width: 36, height: 36, borderRadius: 100 }} contentFit = 'cover'/>
								<Text className = 'flex-1 text-sm font-medium text-foreground' numberOfLines = { 1 }>
									{entry.name}
									{entry.userId === user?.id && (
										<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
									)}
								</Text>
								{!isInProgress && (
									<View className = 'h-8 w-8 items-center justify-center rounded-full bg-secondary'>
										<Text className = 'text-sm font-bold text-muted-foreground'>{entry.score}</Text>
									</View>
								)}
							</Pressable>
						)
					})}
				</View>
				{isCreator && isInProgress && (
					<View className = 'mt-6 flex-row gap-3'>
						<Button className = 'h-12 flex-1 rounded-full active:scale-[0.98]' onPress = { () => router.push(`/match/${match.id}/finish`) }>
							<Text className = 'text-sm font-semibold text-primary-foreground'>Termina partita</Text>
						</Button>
						<Pressable onPress = { handleDelete } disabled = { isDeleting } className = 'h-12 w-12 items-center justify-center rounded-full border border-primary/40 active:border-primary/90 active:bg-primary/90 active:scale-[0.98]'>
							{({ pressed }) =>
								isDeleting ? (
									<ActivityIndicator size = 'small' color = { colors.primary }/>
								) : (
									<Trash2 size = { 18 } color = { pressed ? '#FFFFFF' : colors.primary }/>
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
							icon: <Repeat size = { 18 }/>,
							onPress: handleReplay,
						},
						...(isCreator
							? [
									{
										label: 'Modifica',
										icon: <Pencil size = { 18 }/>,
										onPress: () => router.push(`/match/${match.id}/edit`),
									},
									{
										label: 'Elimina',
										icon: <Trash2 size = { 18 }/>,
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
								{selectedTeam.members.map(member => (
									<View key = { member.id } style = {{ width: '25%', padding: 4 }}>
										<Pressable onPress = { () => handlePlayerPress(member.userId, member.name) } className = 'items-center gap-1.5 py-3 rounded-2xl border border-border bg-card active:scale-[0.98] active:opacity-75'>
											<Image source = {{ uri: getAvatarUrl(member.avatarId ?? null, member.name ?? '') }} style = {{ width: 56, height: 56, borderRadius: 28 }} contentFit = 'cover'/>
											<Text className = 'text-center text-sm font-medium text-foreground' numberOfLines = { 1 }>
												{member.name}
												{member.userId === user?.id && (
													<Text className = 'text-xs font-semibold text-primary'> (io)</Text>
												)}
											</Text>
										</Pressable>
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