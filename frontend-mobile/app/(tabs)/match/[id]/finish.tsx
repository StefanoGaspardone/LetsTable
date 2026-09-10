import { useCallback, useEffect, useRef, useState } from 'react';
import { View, ScrollView, TextInput, Pressable, ActivityIndicator } from 'react-native';
import { router, useFocusEffect, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Trophy, Users, Award, Star, User } from 'lucide-react-native';
import { Image } from 'expo-image';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';

import { getMatchById, updateMatch } from '@/api/match';

import { useToast } from '@/contexts/toast-context';
import { useAuth } from '@/contexts/auth-context';

interface ScoreEntry {
	id: string;
	displayName: string;
	color: string;
	avatarUrl: string | null;
	score: string;
	isWinner: boolean;
	userId: string | null;
	guestName: string | null;
}

const FinishMatchScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const { showToast } = useToast();
	const queryClient = useQueryClient();

	const { user } = useAuth();

	const { data: match, isLoading } = useQuery({
		queryKey: ['matches', 'detail', id],
		queryFn: () => getMatchById(id),
	});

	const scrollRef = useRef<ScrollView>(null);

	const [entries, setEntries] = useState<ScoreEntry[]>([]);
	const [startingId, setStartingId] = useState<string | null>(null);
	const [notes, setNotes] = useState('');
	const [isSubmitting, setIsSubmitting] = useState(false);

	useFocusEffect(
		useCallback(() => {
			setStartingId(null);
			setNotes('');
			scrollRef.current?.scrollTo({ y: 0, animated: false });
		}, [id])
	);

	useEffect(() => {
		if(!match) return;

		if(match.isTeamBased) {
			setEntries(
				match.teams?.map(team => ({
					id: team.id,
					displayName: team.name ?? 'Squadra senza nome',
					color: team.color,
					avatarUrl: null,
					score: '',
					isWinner: false,
					userId: null,
					guestName: null,
				})) ?? []
			);
		} else {
			setEntries(
				match.players?.map(p => ({
					id: p.id,
					displayName: p.user?.username ?? p.guestName ?? 'Sconosciuto',
					color: p.color ?? '#C45135',
					avatarUrl: p.user?.avatarUrl ?? null,
					score: '',
					isWinner: false,
					userId: p.user?.id ?? null,
					guestName: p.guestName,
				})) ?? []
			);
		}
	}, [match]);

	const handleScoreChange = (id: string, score: string) => {
		setEntries(prev => prev.map(e => (e.id === id ? { ...e, score } : e)));
	}

	const handleToggleWinner = (id: string) => {
		setEntries(prev => prev.map(e => (e.id === id ? { ...e, isWinner: !e.isWinner } : e)));
	}

	const handleSetStarting = (id: string) => {
		setStartingId(prev => (prev === id ? null : id));
	}

	const handleSubmit = async () => {
		if(!match) return;

		setIsSubmitting(true);

		try {
			if(match.isTeamBased) {
				await updateMatch(id, {
					gameId: match.game.id!,
					playedAt: match.playedAt,
					place: match.place,
					notes: notes.trim() || null,
					isTeamBased: true,
					players: null,
					teams: match.teams!.map(team => {
						const entry = entries.find(e => e.id === team.id)!;
						return {
							name: team.name,
							color: team.color,
							score: Number(entry.score) || 0,
							isWinner: entry.isWinner,
							startingPosition: startingId === team.id ? 1 : null,
							players: team.players.map(p => ({
								userId: p.user?.id ?? null,
								guestName: p.guestName,
							})),
						};
					}),
				});
			} else {
				await updateMatch(id, {
					gameId: match.game.id!,
					playedAt: match.playedAt,
					place: match.place,
					notes: notes.trim() || null,
					isTeamBased: false,
					teams: null,
					players: entries.map(entry => ({
						userId: entry.userId,
						guestName: entry.guestName,
						color: entry.color,
						score: Number(entry.score) || 0,
						isWinner: entry.isWinner,
						startingPosition: startingId === entry.id ? 1 : null,
					})),
				});
			}

			queryClient.invalidateQueries({ queryKey: ['matches'] });
			queryClient.invalidateQueries({ queryKey: ['matches', 'detail', id] });
			
			router.replace(`/match/${id}`);
		} catch(error: any) {
			showToast(error?.response?.data?.message ?? 'Errore durante il salvataggio', 'error');
		} finally {
			setIsSubmitting(false);
		}
	}

	const isValid = entries.length > 0
		&& entries.every(entry => entry.score.trim() !== '')
		&& startingId !== null
		&& entries.some(entry => entry.isWinner);

	if(isLoading || !match) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator color = '#C45135'/>
			</View>
		)
	}

	return (
		<View className = 'flex-1 bg-background'>
			<ScreenHeader title = 'Termina Partita' leftElement = { <BackButton/> }/>

			<ScrollView ref = { scrollRef } className = 'flex-1' contentContainerStyle = {{ padding: 16, paddingBottom: 40 }}>
				<View className = 'mb-3 flex-row items-center gap-2'>
					{match.isTeamBased ? <Users size = { 18 } color = '#736E65'/> : <Trophy size = { 18 } color = '#736E65'/>}
					<Text className = 'text-sm font-semibold uppercase tracking-wide text-muted-foreground'>
						{match.isTeamBased ? 'Risultati Squadre' : 'Risultati Giocatori'}
					</Text>
				</View>
				<View className = 'gap-3'>
	{entries.map(entry => (
		<View key = { entry.id } className = { `rounded-xl border p-4 ${entry.isWinner ? 'border-primary bg-primary/5' : 'border-border bg-card'}` }>
			<View className = 'mb-3 flex-row items-center justify-between'>
				<View className = 'flex-1 flex-row items-center gap-1.5'>
					<Pressable onPress = { () => handleSetStarting(entry.id) } hitSlop = { 8 }>
						<Star size = { 18 } color = { startingId === entry.id ? '#C45135' : '#DDD8CE' } fill = { startingId === entry.id ? '#C45135' : 'transparent' }/>
					</Pressable>
					{!match.isTeamBased && (
						entry.avatarUrl ? (
							<Image source = {{ uri: entry.avatarUrl }} style = {{ width: 32, height: 32, borderRadius: 100 }}/>
						) : (
							<View className = 'h-7 w-7 items-center justify-center rounded-full bg-secondary'>
								<User size = { 14 } color = '#736E65'/>
							</View>
						)
					)}
					<Text className = 'flex-1 font-medium text-base text-foreground' numberOfLines = { 1 }>
						{entry.displayName}
						{!match.isTeamBased && entry.userId === user?.id && (
							<Text className = 'text-xs font-semibold text-muted-foreground'> (io)</Text>
						)}
					</Text>
				</View>
				<Pressable onPress = { () => handleToggleWinner(entry.id) } className = { `flex-row items-center gap-1.5 rounded-full border px-3 py-1.5 ${entry.isWinner ? 'border-primary bg-primary' : 'border-border bg-background'}` }>
					<Award size = { 14 } color = { entry.isWinner ? '#FFFFFF' : '#736E65' }/>
					<Text className = { `text-xs font-semibold ${entry.isWinner ? 'text-primary-foreground' : 'text-muted-foreground'}` }>
						{entry.isWinner ? 'Vincitore' : 'Segna vincitore'}
					</Text>
				</Pressable>
			</View>

			<View className = 'flex-row items-center gap-3'>
				<Text className = 'text-sm font-medium text-muted-foreground'>Punteggio:</Text>
				<TextInput
					className = 'h-10 flex-1 rounded-lg border border-border bg-background px-3 text-sm font-medium text-foreground'
					keyboardType = 'numeric'
					placeholder = '0'
					placeholderTextColor = '#A0A0A0'
					value = { entry.score }
					onChangeText = { val => handleScoreChange(entry.id, val) }
				/>
			</View>
		</View>
	))}
</View>

				<View className = 'mt-3 flex-row items-center gap-2'>
					<Star size = { 12 } color = '#736E65' fill = '#736E65'/>
					<Text className = 'text-xs text-muted-foreground'>Indica chi ha iniziato la partita</Text>
				</View>

				<Text className = 'mb-1.5 mt-6 text-xs font-semibold uppercase tracking-wide text-muted-foreground'>
					Note (opzionale)
				</Text>
				<TextInput
					value = { notes }
					onChangeText = { setNotes }
					placeholder = "Com'è andata? Dettagli sulla partita..."
					multiline
					numberOfLines = { 4 }
					textAlignVertical = 'top'
					className = 'min-h-[100px] rounded-xl border border-border bg-secondary px-3 py-2.5 text-sm text-foreground'
				/>
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