import { View, ActivityIndicator } from 'react-native';
import { router, useLocalSearchParams } from 'expo-router';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import { TeamFormValue } from '@/components/team-editor';
import { IndividualPlayerFormValue } from '@/components/individual-player-editor';
import MatchForm from '@/components/match-form';

import { getMatchById, updateMatch } from '@/api/match';

import { CreateMatchPayload } from '@/types/match';

const EditMatchScreen = () => {
	const { id } = useLocalSearchParams<{ id: string }>();
	const queryClient = useQueryClient();

	const { data: match, isLoading } = useQuery({
		queryKey: ['matches', 'detail', id],
		queryFn: () => getMatchById(id),
	});

	const handleSubmit = async (payload: CreateMatchPayload) => {
		await updateMatch(id, payload);
		
        queryClient.invalidateQueries({ queryKey: ['matches'] });
		queryClient.invalidateQueries({ queryKey: ['matches', 'detail', id] });
		
        router.back();
	}

	if(isLoading || !match) {
		return (
			<View className = 'flex-1 items-center justify-center bg-background'>
				<ActivityIndicator/>
			</View>
		)
	}

	const initialTeams: TeamFormValue[] | undefined = match.teams?.map(team => ({
		name: team.name ?? '',
		color: team.color,
		score: String(team.score),
		isWinner: team.isWinner,
		players: team.players.map((p) => ({
			userId: p.user?.id ?? null,
			guestName: p.guestName,
			displayName: p.user?.username ?? p.guestName ?? '',
		})),
	}));

	const initialPlayers: IndividualPlayerFormValue[] | undefined = match.players?.map(player => ({
		identity: {
			userId: player.user?.id ?? null,
			guestName: player.guestName,
			displayName: player.user?.username ?? player.guestName ?? '',
		},
		color: player.color ?? '#B23B3B',
		score: String(player.score ?? 0),
		isWinner: player.isWinner ?? false,
	}));

	return (
		<View className = 'flex-1 bg-background pt-14'>
			<MatchForm mode = 'full'
				initialGame = {{
					id: match.game.id,
					bggId: match.game.bggId,
					name: match.game.name,
					thumbnailUrl: match.game.thumbnailUrl,
					yearPublished: null,
					imageUrl: null,
					minPlayers: null,
					maxPlayers: null,
					playingTimeMinutes: null,
					description: null,
				}}
				initialPlayedAt = { match.playedAt } initialPlace = { match.place ?? undefined } initialNotes = { match.notes ?? undefined } initialDuration = { match.durationMinutes != null ? String(match.durationMinutes) : undefined } initialIsTeamBased = { match.isTeamBased } initialTeams = { initialTeams } initialPlayers = { initialPlayers } onSubmit = { handleSubmit } submitLabel = 'Salva modifiche'
			/>
		</View>
	)
}

export default EditMatchScreen;