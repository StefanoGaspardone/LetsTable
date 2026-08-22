import { useState } from 'react';
import { View, ScrollView, Alert } from 'react-native';
import { Plus } from 'lucide-react-native';

import { Text } from '@/components/ui/text';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import TeamEditor, { TeamFormValue } from '@/components/team-editor';
import IndividualPlayerEditor, { IndividualPlayerFormValue } from '@/components/individual-player-editor';
import GamePickerField from '@/components/game-picker-field';

import { CreateMatchPayload } from '@/types/match';
import { Game } from '@/types/game';

interface MatchFormProps {
	mode: 'start' | 'full';
	initialGame?: Game;
	initialPlayedAt?: string;
	initialPlace?: string;
	initialNotes?: string;
	initialDuration?: string;
	initialIsTeamBased?: boolean;
	initialTeams?: TeamFormValue[];
	initialPlayers?: IndividualPlayerFormValue[];
	onSubmit: (payload: CreateMatchPayload) => Promise<void>;
	submitLabel: string;
}

const DEFAULT_COLOR = '#B23B3B';

const emptyPlayer = (): IndividualPlayerFormValue => {
	return { identity: null, color: DEFAULT_COLOR, score: '0', isWinner: false };
}

const emptyTeam = (): TeamFormValue => {
	return { name: '', color: DEFAULT_COLOR, score: '0', isWinner: false, players: [] };
}

const MatchForm = ({ mode, initialGame, initialPlayedAt, initialPlace, initialNotes, initialDuration, initialIsTeamBased = false, initialTeams, initialPlayers, onSubmit, submitLabel }: MatchFormProps) => {
	const [game, setGame] = useState<Game | null>(initialGame ?? null);
	const [playedAt, setPlayedAt] = useState(initialPlayedAt ?? new Date().toISOString().slice(0, 10));
	const [place, setPlace] = useState(initialPlace ?? '');
	const [notes, setNotes] = useState(initialNotes ?? '');
	const [duration, setDuration] = useState(initialDuration ?? '');
	const [isTeamBased, setIsTeamBased] = useState(initialIsTeamBased);
	const [teams, setTeams] = useState<TeamFormValue[]>(initialTeams ?? [emptyTeam(), emptyTeam()]);
	const [players, setPlayers] = useState<IndividualPlayerFormValue[]>(initialPlayers ?? [emptyPlayer()]);
	const [isSubmitting, setIsSubmitting] = useState(false);

	const showScoring = mode === 'full';

	function validate(): string | null {
		if(!game) return 'Seleziona un gioco';
		if(!playedAt) return 'Inserisci la data della partita';

		if(isTeamBased) {
			if(teams.length < 1) return 'Aggiungi almeno una squadra';
			
            for (const team of teams) {
				if(!team.color) return 'Ogni squadra deve avere un colore';
				if(team.players.length === 0) return `La squadra '${team.name || 'senza nome'}' non ha giocatori`;
			}
		} else {
			if(players.length < 1) return 'Aggiungi almeno un giocatore';
			
            for(const player of players) if(!player.identity) return 'Ogni giocatore deve essere selezionato';
		}

		return null;
	}

	const handleSubmit = async () => {
		const error = validate();
		
        if(error) {
			Alert.alert('Dati mancanti', error);
			return;
		}

		setIsSubmitting(true);
		
        try {
			const payload: CreateMatchPayload = {
				gameId: game!.id,
				playedAt,
				place: place.trim() || null,
				notes: notes.trim() || null,
				durationMinutes: mode === 'full' && duration ? Number(duration) : null,
				isTeamBased,
				teams: isTeamBased
					? teams.map((team) => ({
							name: team.name.trim() || null,
							color: team.color,
							score: mode === 'full' ? Number(team.score) || 0 : 0,
							isWinner: mode === 'full' ? team.isWinner : false,
							players: team.players.map((p) => ({ userId: p.userId, guestName: p.guestName })),
						}))
					: null,
				players: !isTeamBased
					? players.map((player) => ({
							userId: player.identity!.userId,
							guestName: player.identity!.guestName,
							color: player.color,
							score: mode === 'full' ? Number(player.score) || 0 : 0,
							isWinner: mode === 'full' ? player.isWinner : false,
						}))
					: null,
			}

			await onSubmit(payload);
		} catch(err: any) {
			const message = err?.response?.data?.message ?? 'Errore durante il salvataggio';
			Alert.alert('Errore', message);
		} finally {
			setIsSubmitting(false);
		}
	}

	return (
		<ScrollView className = 'flex-1 bg-background px-4' contentContainerStyle = {{ paddingBottom: 40 }}>
			<Text className = 'mb-1 mt-4 text-sm text-muted-foreground'>Gioco</Text>
			<GamePickerField value = { game } onChange = { setGame }/>
			<Text className = 'mb-1 mt-4 text-sm text-muted-foreground'>Data</Text>
			<Input placeholder = 'AAAA-MM-GG' value = { playedAt } onChangeText = { setPlayedAt }/>
			<Text className = 'mb-1 mt-4 text-sm text-muted-foreground'>Luogo (opzionale)</Text>
			<Input placeholder = 'Es. Casa di Marco' value = { place } onChangeText = { setPlace }/>
			{mode === 'full' && (
				<>
					<Text className = 'mb-1 mt-4 text-sm text-muted-foreground'>Durata in minuti</Text>
					<Input placeholder = 'Es. 90' keyboardType = 'number-pad' value = { duration } onChangeText = { setDuration }/>
				</>
			)}
			<Text className = 'mb-1 mt-4 text-sm text-muted-foreground'>Note (opzionale)</Text>
			<Input placeholder = 'Note libere' value = { notes } onChangeText = { setNotes } multiline/>
			<View className = 'mt-6 flex-row gap-2'>
				<Button variant = { !isTeamBased ? 'default' : 'outline' } className = 'flex-1' onPress = { () => setIsTeamBased(false) }>
					<Text>Giocatori singoli</Text>
				</Button>
				<Button variant = { isTeamBased ? 'default' : 'outline' } className = 'flex-1' onPress = { () => setIsTeamBased(true) }>
					<Text>Squadre</Text>
				</Button>
			</View>
			<View className = 'mt-4'>
				{isTeamBased ? (
					<>
						{teams.map((team, index) => (
							<TeamEditor key = { index } value = { team } showScoring = { showScoring } onChange = { updated => setTeams(teams.map((t, i) => (i === index ? updated : t))) } onRemove = { () => setTeams(teams.filter((_, i) => i !== index)) }/>
						))}
						<Button variant = 'outline' className = 'flex-row items-center gap-2' onPress = { () => setTeams([...teams, emptyTeam()]) }>
							<Plus size = { 16 } className = 'text-foreground'/>
							<Text>Aggiungi squadra</Text>
						</Button>
					</>
				) : (
					<>
						{players.map((player, index) => (
							<IndividualPlayerEditor key = { index } value = { player } showScoring = { showScoring } onChange = { updated => setPlayers(players.map((p, i) => (i === index ? updated : p))) } onRemove = { () => setPlayers(players.filter((_, i) => i !== index)) }/>
						))}
						<Button variant = 'outline' className = 'flex-row items-center gap-2' onPress = { () => setPlayers([...players, emptyPlayer()]) }>
							<Plus size = { 16 } className = 'text-foreground'/>
							<Text>Aggiungi giocatore</Text>
						</Button>
					</>
				)}
			</View>
			<Button className = 'mt-6' onPress = { handleSubmit } disabled = { isSubmitting }>
				<Text>{isSubmitting ? 'Salvataggio...' : submitLabel}</Text>
			</Button>
		</ScrollView>
	)
}

export default MatchForm;