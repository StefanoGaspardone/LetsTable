import { useCallback, useEffect, useRef, useState } from 'react';
import { View, ScrollView, TextInput, Pressable, ActivityIndicator } from 'react-native';
import { useFocusEffect, useLocalSearchParams } from 'expo-router';
import { Trophy, Users, Award } from 'lucide-react-native';
import { Image } from 'expo-image';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';
import SegmentedControl from '@/components/common/segmented-control';

import { useToast } from '@/contexts/toast-context';
import { useAuth } from '@/contexts/auth-context';
import { useNavigationStack } from '@/contexts/navigation-stack-context';

import { getAvatarUrl } from '@/lib/file';

import { useMatchDetail, useUpdateMatch } from '@/hooks/use-match';
import { useThemeColors } from '@/hooks/use-theme-colors';

import { Match, UpdateMatchPayload } from '@/types/match';

type ResultMode = 'score' | 'outcome';

interface ScoreEntry {
	id: string;
	displayName: string;
	color: string;
	avatarId: string | null | undefined;
	score: string;
	isWinner: boolean;
	userId: string | null;
	guestName: string | null;
	isStartingFirst: boolean;
}

const RESULT_MODE_OPTIONS = [
	{ value: 'score', label: 'Punteggio' },
	{ value: 'outcome', label: 'Vittoria/Sconfitta' },
]

const buildEntries = (match: Match, fallbackColor: string): ScoreEntry[] => {
	if(match.isTeamBased) {
		return (match.teams ?? []).map(team => ({
			id: team.id,
			displayName: team.name ?? 'Squadra senza nome',
			color: team.color,
			avatarId: null,
			score: '',
			isWinner: false,
			userId: null,
			guestName: null,
			isStartingFirst: team.isStartingFirst,
		}));
	}

	return (match.players ?? []).map(player => ({
		id: player.id,
		displayName: player.user?.username ?? player.guestName ?? 'Sconosciuto',
		color: player.color ?? fallbackColor,
		avatarId: player.user?.avatarId,
		score: '',
		isWinner: false,
		userId: player.user?.id ?? null,
		guestName: player.guestName,
		isStartingFirst: player.isStartingFirst,
	}));
}

const parseDuration = (value: string): number | null => {
	const parsed = Number.parseInt(value.trim(), 10);
	return Number.isFinite(parsed) && parsed > 0 ? parsed : null;
}

const FinishMatchScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();

	const { user } = useAuth();
	const { showToast } = useToast();
	const router = useNavigationStack();
	const { colors } = useThemeColors();

	const { data: match, isLoading } = useMatchDetail(id);
	const updateMatchMutation = useUpdateMatch();

	const scrollRef = useRef<ScrollView>(null);
	const initializedSessionRef = useRef<string | null>(null);

	const [focusSession, setFocusSession] = useState(0);
	const [entries, setEntries] = useState<ScoreEntry[]>([]);
	const [notes, setNotes] = useState('');
	const [durationMinutes, setDurationMinutes] = useState('');
	const [resultMode, setResultMode] = useState<ResultMode>('score');

	const isSubmitting = updateMatchMutation.isPending;

	useFocusEffect(
		useCallback(() => {
			setNotes('');
			setDurationMinutes('');
			setResultMode('score');
			setFocusSession(prev => prev + 1);

			scrollRef.current?.scrollTo({ y: 0, animated: false });
		}, [id])
	);

	useEffect(() => {
		if(!match) return;

		const sessionKey = `${match.id}:${focusSession}`;
		if(initializedSessionRef.current === sessionKey) return;

		initializedSessionRef.current = sessionKey;
		setEntries(buildEntries(match, colors.primary));
	}, [match, focusSession]);

	const handleScoreChange = (entryId: string, score: string) => {
		setEntries(prev => prev.map(entry => (entry.id === entryId ? { ...entry, score } : entry)));
	}

	const handleToggleWinner = (entryId: string) => {
		setEntries(prev => prev.map(entry => (entry.id === entryId ? { ...entry, isWinner: !entry.isWinner } : entry)));
	}

	const buildPayload = (currentMatch: Match): UpdateMatchPayload => {
		const base = {
			gameId: currentMatch.game.id,
			playedAt: currentMatch.playedAt,
			place: currentMatch.place,
			notes: notes.trim() || null,
			durationMinutes: parseDuration(durationMinutes),
			expansionIds: currentMatch.expansionsUsed.map(expansion => expansion.id),
		}

		if(currentMatch.isTeamBased) {
			return {
				...base,
				isTeamBased: true,
				players: null,
				teams: (currentMatch.teams ?? []).map(team => {
					const entry = entries.find(candidate => candidate.id === team.id)!;

					return {
						name: team.name,
						color: team.color,
						score: resultMode === 'score' ? (Number(entry.score) || 0) : null,
						isWinner: entry.isWinner,
						isStartingFirst: entry.isStartingFirst,
						players: team.players.map(player => ({
							userId: player.user?.id ?? null,
							guestName: player.guestName,
						})),
					};
				}),
			}
		}

		return {
			...base,
			isTeamBased: false,
			teams: null,
			players: entries.map(entry => ({
				userId: entry.userId,
				guestName: entry.guestName,
				color: entry.color,
				score: Number(entry.score) || 0,
				isWinner: entry.isWinner,
				isStartingFirst: entry.isStartingFirst,
			})),
		}
	}

	const handleSubmit = () => {
		if(!match) return;

		updateMatchMutation.mutate({ matchId: id, payload: buildPayload(match) }, {
			onSuccess: () => router.replace(`/match/${id}`),
			onError: (error: any) => showToast(error?.response?.data?.message ?? 'Errore durante il salvataggio', 'error'),
		});
	}

	const requiresScore = !match?.isTeamBased || resultMode === 'score';

	const isValid = entries.length > 0
		&& (requiresScore
			? entries.every(entry => entry.score.trim() !== '')
			: entries.some(entry => entry.isWinner));

	if(isLoading || !match) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = { colors.primary }/>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Termina Partita' leftElement = { <BackButton/> }/>
			<ScrollView ref = { scrollRef } className = 'flex-1' contentContainerStyle = {{ padding: 16, paddingBottom: 40 }} keyboardShouldPersistTaps = 'handled'>
				<View className = 'mb-3 flex-row items-center gap-2'>
					{match.isTeamBased ? <Users size = { 18 } color = { colors.mutedForeground }/> : <Trophy size = { 18 } color = { colors.mutedForeground }/>}
					<Text className = 'text-sm font-semibold uppercase tracking-wide text-muted-foreground'>
						{match.isTeamBased ? 'Risultati Squadre' : 'Risultati Giocatori'}
					</Text>
				</View>
				{match.isTeamBased && (
					<View className = 'mb-4'>
						<SegmentedControl options = { RESULT_MODE_OPTIONS } selected = { resultMode } onSelect = { value => setResultMode(value as ResultMode) }/>
					</View>
				)}
				<View className = 'gap-3'>
					{entries.map(entry => (
						<View key = { entry.id } className = { `rounded-xl border p-4 ${entry.isWinner ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
							<View className = 'mb-3 flex-row items-center justify-between'>
								<View className = 'flex-1 flex-row items-center gap-1.5'>
									<Image source = {{ uri: getAvatarUrl(entry.avatarId ?? null, entry.displayName ?? '') }} style = {{ width: 32, height: 32, borderRadius: 100 }} contentFit = 'cover'/>
									<Text className = 'flex-1 font-medium text-base text-foreground' numberOfLines = { 1 }>
										{entry.displayName}
										{!match.isTeamBased && entry.userId === user?.id && (
											<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
										)}
									</Text>
								</View>
								<Pressable onPress = { () => handleToggleWinner(entry.id) } className = { `flex-row items-center gap-1.5 rounded-full border px-3 py-1.5 ${entry.isWinner ? 'border-primary bg-primary' : 'border-border bg-background'}` }>
									<Award size = { 14 } color = { entry.isWinner ? '#FFFFFF' : colors.mutedForeground }/>
									<Text className = { `text-xs font-semibold ${entry.isWinner ? 'text-primary-foreground' : 'text-muted-foreground'}` }>
										{entry.isWinner ? 'Vincitore' : 'Segna vincitore'}
									</Text>
								</Pressable>
							</View>
							{requiresScore && (
								<View className = 'flex-row items-center gap-3'>
									<Text className = 'text-sm font-medium text-muted-foreground'>Punteggio:</Text>
									<TextInput className = 'h-10 flex-1 rounded-lg border border-border bg-background px-3 text-sm font-medium text-foreground' keyboardType = 'numeric' placeholder = '0' placeholderTextColor = { colors.mutedForeground } value = { entry.score } onChangeText = { value => handleScoreChange(entry.id, value) }/>
								</View>
							)}
						</View>
					))}
				</View>
				<Text className = 'mb-1.5 mt-6 text-xs font-semibold uppercase tracking-wide text-muted-foreground'>
					Durata (opzionale)
				</Text>
				<TextInput value = { durationMinutes } onChangeText = { setDurationMinutes } placeholderTextColor = { colors.mutedForeground } placeholder = 'Lascia vuoto per calcolarla automaticamente' keyboardType = 'numeric' className = 'h-11 rounded-xl border border-border bg-secondary px-3 text-sm text-foreground'/>
				<Text className = 'mb-1.5 mt-6 text-xs font-semibold uppercase tracking-wide text-muted-foreground'>
					Note (opzionale)
				</Text>
				<TextInput value = { notes } onChangeText = { setNotes } placeholderTextColor = { colors.mutedForeground } placeholder = "Com'è andata? Dettagli sulla partita..." multiline numberOfLines = { 4 } textAlignVertical = 'top' className = 'min-h-[100px] rounded-xl border border-border bg-secondary px-3 py-2.5 text-sm text-foreground'/>
				<Button className = 'mt-6 h-12 rounded-full active:scale-[0.98]' disabled = { isSubmitting || !isValid } onPress = { handleSubmit }>
					{isSubmitting ? (
						<ActivityIndicator color = '#FFFFFF'/>
					) : (
						<Text className = 'text-sm font-semibold text-primary-foreground'>Salva e concludi</Text>
					)}
				</Button>
			</ScrollView>
		</View>
	)
}

export default FinishMatchScreen;