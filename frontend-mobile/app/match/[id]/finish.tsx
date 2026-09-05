import { useEffect, useState } from 'react';
import { View, Pressable, TextInput } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import { Trophy, Star } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import ScreenHeader from '@/components/common/screen-header';
import BackButton from '@/components/common/back-button';

import { getMatchById, updateMatch } from '@/api/match';
import { useToast } from '@/contexts/toast-context';

interface ScoreEntry {
	id: string;
	displayName: string;
	color: string;
	score: string;
	isWinner: boolean;
	userId: string | null;
	guestName: string | null;
}

const FinishMatchScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const { showToast } = useToast();
	const queryClient = useQueryClient();

	const { data: match, isLoading } = useQuery({
		queryKey: ['matches', 'detail', id],
		queryFn: () => getMatchById(id),
	});

	const [entries, setEntries] = useState<ScoreEntry[]>([]);
	const [startingId, setStartingId] = useState<string | null>(null);
	const [isSubmitting, setIsSubmitting] = useState(false);
	const [notes, setNotes] = useState('');

	useEffect(() => {
		if (!match) return;

		if (match.isTeamBased) {
			setEntries(
				match.teams?.map(team => ({
					id: team.id,
					displayName: team.name ?? 'Squadra senza nome',
					color: team.color,
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
	};

	const handleToggleWinner = (id: string) => {
		setEntries(prev => prev.map(e => (e.id === id ? { ...e, isWinner: !e.isWinner } : e)));
	};

	const handleSetStarting = (id: string) => {
		setStartingId(prev => (prev === id ? null : id));
	};

	const handleSubmit = async () => {
		if (!match) return;

		setIsSubmitting(true);
		try {
			if (match.isTeamBased) {
				await updateMatch(id, {
					gameId: match.game.id!,
					playedAt: match.playedAt,
					place: match.place,
					notes: match.notes,
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
					notes: match.notes,
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
		} catch (error: any) {
			showToast(error?.response?.data?.message ?? 'Errore durante il salvataggio', 'error');
		} finally {
			setIsSubmitting(false);
		}
	};

	if (isLoading || !match) {
		return <View className='flex-1 bg-background' />;
	}

	return (
		<View className='flex-1 bg-background'>
			<ScreenHeader title='Termina Partita' leftElement={<BackButton />} />

			<View className='p-4'>
				<Text className='mb-1 font-display text-lg text-foreground'>{match.game.name}</Text>
				<Text className='mb-4 text-sm text-muted-foreground'>
					Inserisci i punteggi, indica il vincitore (o i vincitori in caso di pareggio) e chi ha iniziato.
				</Text>

				<View className='gap-2'>
					{entries.map(entry => (
						<View
							key={entry.id}
							className='flex-row items-center gap-2.5 rounded-xl border border-border bg-card px-3 py-2.5'
						>
							<Pressable onPress={() => handleSetStarting(entry.id)} hitSlop={8}>
								<Star
									size={18}
									color={startingId === entry.id ? '#C45135' : '#DDD8CE'}
									fill={startingId === entry.id ? '#C45135' : 'transparent'}
								/>
							</Pressable>

							<View style={{ backgroundColor: entry.color }} className='h-4 w-4 rounded-full' />

							<Text className='flex-1 text-sm font-medium text-foreground' numberOfLines={1}>
								{entry.displayName}
							</Text>

							<Pressable onPress={() => handleToggleWinner(entry.id)} hitSlop={8}>
								<Trophy size={20} color={entry.isWinner ? '#C45135' : '#DDD8CE'} />
							</Pressable>

							<Input
								value={entry.score}
								onChangeText={text => handleScoreChange(entry.id, text)}
								keyboardType='number-pad'
								placeholder='0'
								className='h-10 w-16 text-center'
							/>
						</View>
					))}
				</View>
				<View className='mt-4 flex-row items-center gap-2'>
					<Star size={12} color='#736E65' fill='#736E65' />
					<Text className='text-xs text-muted-foreground'>Indica chi ha iniziato la partita</Text>
				</View>
				<Text className = 'mb-1.5 mt-4 text-xs uppercase tracking-wide text-muted-foreground font-semibold'>Note (opzionale)</Text>
				<TextInput
					value = { notes }
					onChangeText = { setNotes }
					placeholder = "Com'è andata? Dettagli sulla partita..."
					multiline
					numberOfLines = { 4 }
					textAlignVertical = 'top'
					className = 'min-h-[100px] rounded-xl border border-border bg-secondary px-3 py-2.5 text-sm text-foreground'
				/>
				<Button className='mt-6 h-12 rounded-full' onPress={handleSubmit} disabled={isSubmitting}>
					<Text className='text-base font-semibold text-primary-foreground'>
						{isSubmitting ? 'Salvataggio...' : 'Salva risultato'}
					</Text>
				</Button>
			</View>
		</View>
	);
};

export default FinishMatchScreen;